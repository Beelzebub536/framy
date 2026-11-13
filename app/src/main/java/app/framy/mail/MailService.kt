package app.framy.mail

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.util.Log
import app.framy.MailItem
import app.framy.Notify
import app.framy.Secrets
import app.framy.Store
import com.sun.mail.imap.IMAPFolder
import java.io.File
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.mail.AuthenticationFailedException
import javax.mail.FetchProfile
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Part
import javax.mail.Session
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeUtility

/** Holds ONE read-only IMAP IDLE connection to Gmail's INBOX so new mail notifies within seconds. */
class MailService : Service() {
    companion object {
        private const val HOST = "imap.gmail.com"
        private const val FIRST_RUN_LIST = 20

        @Volatile private var folder: IMAPFolder? = null
        @Volatile private var imap: javax.mail.Store? = null
        @Volatile private var running = false
        @Volatile private var kick = false
        private val lock = Object()

        fun configured() = Secrets.get("mailAddress") != null && Secrets.get("mailPassword") != null

        fun start(ctx: Context) {
            if (!configured() || Store.mail.value.authRejected) return
            try { ctx.startForegroundService(Intent(ctx, MailService::class.java)) }
            catch (e: Exception) { Log.w("Framy", "mail service start refused: ${e.message}") } // background-start limits; app open retries
        }

        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, MailService::class.java)) }

        /** Called every tick: break IDLE so the connection is proven alive (or reconnect if it is dead). */
        fun renew(ctx: Context) {
            if (!configured()) return
            if (!running) { start(ctx); return }
            val f = folder
            if (f != null && f.isOpen) {
                val ex = Executors.newSingleThreadExecutor()
                val probe = ex.submit { f.messageCount } // any command ends IDLE; the loop re-syncs and re-IDLEs
                try { probe.get(30, TimeUnit.SECONDS) } catch (e: Exception) {
                    Log.w("Framy", "mail renew failed, reconnecting: ${e.message}")
                    try { imap?.close() } catch (_: Exception) {}
                } finally { ex.shutdownNow() }
            }
            wake()
        }

        private fun wake() = synchronized(lock) { kick = true; lock.notifyAll() }

        private fun session(idle: Boolean) = Session.getInstance(Properties().apply {
            put("mail.imaps.connectiontimeout", "20000")
            if (!idle) put("mail.imaps.timeout", "30000") // IDLE needs an unbounded read; renew() guards it instead
            put("mail.imaps.ssl.checkserveridentity", "true")
        })

        private fun <T> withInbox(block: (IMAPFolder) -> T): T {
            val s = session(idle = false).getStore("imaps")
            s.connect(HOST, 993, Secrets.get("mailAddress"), Secrets.get("mailPassword"))
            try {
                val f = s.getFolder("INBOX") as IMAPFolder
                f.open(Folder.READ_ONLY) // EXAMINE: nothing is ever marked read, moved or deleted
                try { return block(f) } finally { f.close(false) }
            } finally { s.close() }
        }

        /** Body (plain text preferred, else HTML) + attachments (name, size) of one message. Own short connection. */
        fun fetchBody(uid: Long): Pair<String, List<Pair<String, Long>>> = withInbox { f ->
            val m = f.getMessageByUID(uid) ?: return@withInbox "" to emptyList()
            val (plain, html) = textParts(m)
            (plain ?: html ?: "") to attachments(m).map { (name, p) -> name to p.size.toLong() }
        }

        fun saveAttachment(uid: Long, name: String, dest: File) = withInbox { f ->
            val p = f.getMessageByUID(uid)?.let { attachments(it).firstOrNull { (n, _) -> n == name }?.second }
                ?: error("attachment not found")
            dest.parentFile?.mkdirs()
            p.inputStream.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
        }

        private fun textParts(p: Part): Pair<String?, String?> {
            if (Part.ATTACHMENT.equals(p.disposition, true)) return null to null
            return when {
                p.isMimeType("text/plain") -> (p.content as? String) to null
                p.isMimeType("text/html") -> null to (p.content as? String)
                p.isMimeType("multipart/*") -> {
                    val mp = p.content as javax.mail.Multipart
                    var plain: String? = null; var html: String? = null
                    for (i in 0 until mp.count) {
                        val (a, b) = textParts(mp.getBodyPart(i))
                        plain = plain ?: a; html = html ?: b
                    }
                    plain to html
                }
                else -> null to null
            }
        }

        private fun attachments(p: Part): List<Pair<String, Part>> = when {
            p.isMimeType("multipart/*") -> (p.content as javax.mail.Multipart).let { mp -> (0 until mp.count).flatMap { attachments(mp.getBodyPart(it)) } }
            Part.ATTACHMENT.equals(p.disposition, true) && p.fileName != null -> listOf(MimeUtility.decodeText(p.fileName) to p)
            else -> emptyList()
        }

        private fun toItem(f: IMAPFolder, m: Message): MailItem {
            val from = m.from?.firstOrNull() as? InternetAddress
            val addr = from?.address ?: ""
            val (plain, html) = try { textParts(m) } catch (e: Exception) { null to null }
            return MailItem(
                uid = f.getUID(m), fromName = MailLogic.sender(from?.personal, addr), fromAddr = addr,
                subject = m.subject?.takeIf { it.isNotBlank() } ?: "(no subject)",
                date = (m.receivedDate ?: m.sentDate)?.time ?: System.currentTimeMillis(),
                snippet = MailLogic.snippet(plain, html),
            )
        }

        private fun fetch(f: IMAPFolder, msgs: Array<Message>): List<MailItem> {
            f.fetch(msgs, FetchProfile().apply { add(FetchProfile.Item.ENVELOPE); add(UIDFolder.FetchProfileItem.UID) })
            return msgs.map { toItem(f, it) }
        }
    }

    private var thread: Thread? = null
    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = wake()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notify.runningNotification(this)
        if (Build.VERSION.SDK_INT >= 34) startForeground(Notify.RUNNING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(Notify.RUNNING, n)
        if (thread?.isAlive != true) {
            running = true
            getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(netCallback)
            thread = Thread(::loop, "framy-imap").apply { start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        try { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(netCallback) } catch (_: Exception) {}
        Thread { try { imap?.close() } catch (_: Exception) {} }.start()
        wake()
        Store.updateMail { it.copy(live = false) }
        super.onDestroy()
    }

    private fun loop() {
        while (running) {
            try {
                val s = session(idle = true).getStore("imaps")
                imap = s
                s.connect(HOST, 993, Secrets.get("mailAddress"), Secrets.get("mailPassword"))
                val f = s.getFolder("INBOX") as IMAPFolder
                f.open(Folder.READ_ONLY)
                folder = f
                Store.updateMail { it.copy(live = true, authRejected = false) }
                while (running) {
                    sync(f)
                    f.idle(true) // returns on new mail / any server event, or when renew() sends a command
                }
            } catch (e: AuthenticationFailedException) {
                Store.updateMail { it.copy(live = false, authRejected = true) }
                Notify.problem(this, Notify.PROBLEM_MAIL, "Gmail rejected the app password. Update it in Settings.")
                running = false
                stopSelf()
                return
            } catch (e: Exception) {
                Log.w("Framy", "imap: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                folder = null
                try { imap?.close() } catch (_: Exception) {}
                if (Store.mail.value.live) Store.updateMail { it.copy(live = false) }
            }
            // Offline or dropped: wait for the next tick or the network to come back. No retry spin.
            synchronized(lock) { while (running && !kick) lock.wait(); kick = false }
        }
    }

    private fun sync(f: IMAPFolder) {
        val count = f.messageCount
        val maxUid = if (count > 0) f.getUID(f.getMessage(count)) else 0L
        val st = Store.mail.value
        val step = MailLogic.cursor(st.uidValidity, st.lastUid, f.uidValidity, maxUid)
        step.resetTo?.let { to ->
            // First run (or mailbox reset): show the latest few in the list, notify nothing.
            val recent = if (count > 0) fetch(f, f.getMessages(maxOf(1, count - FIRST_RUN_LIST + 1), count)) else emptyList()
            Store.updateMail { it.copy(uidValidity = f.uidValidity, lastUid = to, items = recent.map { m -> m.copy(read = true) }) }
        }
        step.fetchFromUid?.let { from ->
            val fresh = fetch(f, f.getMessagesByUID(from, UIDFolder.LASTUID)).filter { it.uid >= from }
            if (fresh.isEmpty()) return
            Store.updateMail { s -> s.copy(lastUid = maxOf(s.lastUid, fresh.maxOf { it.uid }), items = fresh + s.items.filter { o -> fresh.none { it.uid == o.uid } }) }
            Notify.mail(this, MailLogic.visible(fresh, Store.mail.value.hidden))
        }
    }
}
