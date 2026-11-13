package app.framy

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import app.framy.canvas.CanvasApi
import app.framy.canvas.CanvasException
import app.framy.canvas.CanvasLogic
import app.framy.canvas.Fail
import app.framy.mail.MailService

/** The ~10-minute heartbeat: renews mail IDLE, polls Canvas, fires reminders. */
object Tick {
    // Inexact alarms fire within [interval, 1.75 x interval]; 6 min lands each tick at ~6-10.5 min (spec: ~10).
    private const val INTERVAL = 6 * 60_000L

    fun arm(ctx: Context) {
        val pi = PendingIntent.getBroadcast(ctx, 0, Intent(ctx, TickReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        ctx.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + INTERVAL, pi)
    }

    /** Blocking; call off the main thread. Never throws. */
    @Synchronized fun run(ctx: Context) {
        try { MailService.renew(ctx) } catch (e: Exception) { Log.w("Framy", "mail renew: ${e.message}") }
        try { pollCanvas(ctx) } catch (e: Exception) { Log.w("Framy", "canvas tick: ${e.message}") }
    }

    private fun pollCanvas(ctx: Context) {
        if (Secrets.get("canvasToken") == null || Store.canvas.value.tokenRejected) return
        val now = System.currentTimeMillis()
        try {
            val courses = CanvasApi.courses()
            val activity = CanvasApi.activity()
            val planner = try { CanvasApi.planner(now) } catch (e: CanvasException) { if (e.fail is Fail.TokenRejected) throw e; Store.canvas.value.planner }
            val before = Store.canvas.value
            val active = courses.map { it.id }.toSet()
            val fresh = CanvasLogic.toNotify(activity, before.seen, active, firstRun = !before.firstRunDone)
            val reminders = CanvasLogic.dueReminders(planner, before.remindersSent, before.done, now)
            Store.updateCanvas { s ->
                val known = s.items.associateBy { it.id }
                s.copy(
                    courses = courses,
                    items = (activity.map { a -> known[a.id]?.let { a.copy(read = it.read) } ?: a } + s.items.filter { it.id !in activity.map { a -> a.id }.toSet() })
                        .filter { it.courseId == null || it.courseId in active },
                    planner = planner,
                    seen = (s.seen + activity.map { it.id }).toList().takeLast(1000).toSet(),
                    remindersSent = s.remindersSent + reminders.map { "${it.first.key}:${it.second}" },
                    firstRunDone = true,
                )
            }
            Notify.canvas(ctx, fresh)
            reminders.forEach { (item, which) -> Notify.reminder(ctx, item, which) }
        } catch (e: CanvasException) {
            if (e.fail is Fail.TokenRejected) {
                Store.updateCanvas { it.copy(tokenRejected = true) }
                Notify.problem(ctx, Notify.PROBLEM_CANVAS, "Canvas token rejected. Paste a new one in Settings.")
            } else Log.w("Framy", "canvas: ${e.fail}")
        }
    }
}

class TickReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Tick.arm(ctx.applicationContext) // re-arm first: a stuck tick must never stop the heartbeat
        val pending = goAsync()
        val wl = ctx.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "framy:tick").apply { acquire(60_000) }
        Thread {
            try { Tick.run(ctx.applicationContext) } finally {
                if (wl.isHeld) wl.release()
                pending.finish()
            }
        }.start()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Tick.arm(ctx.applicationContext)
        MailService.start(ctx.applicationContext)
    }
}
