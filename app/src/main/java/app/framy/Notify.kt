package app.framy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.framy.canvas.CanvasLogic
import app.framy.canvas.capped
import app.framy.ui.MainActivity

object Notify {
    const val PROBLEM_CANVAS = 1
    const val PROBLEM_MAIL = 2
    const val RUNNING = 3
    private const val SUMMARY_CANVAS = 4
    private const val SUMMARY_MAIL = 5

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel("canvas", "Canvas updates", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel("mail", "New email", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel("reminders", "Due-date reminders", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel("problems", "Login problems", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel("running", "Keeping mail instant", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) },
        ).forEach(nm::createNotificationChannel)
    }

    private fun open(ctx: Context, target: String): PendingIntent = PendingIntent.getActivity(
        ctx, target.hashCode(),
        Intent(ctx, MainActivity::class.java).putExtra("open", target).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun post(ctx: Context, id: Int, channel: String, title: String, text: String, target: String) {
        val n = Notification.Builder(ctx, channel).setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open(ctx, target)).setAutoCancel(true).build()
        try { ctx.getSystemService(NotificationManager::class.java).notify(id, n) } catch (e: SecurityException) { /* notifications denied */ }
    }

    fun canvas(ctx: Context, items: List<CanvasItem>) {
        val courses = Store.canvas.value.courses.associateBy { it.id }
        val (shown, more) = capped(items)
        shown.forEach {
            val course = it.courseId?.let(courses::get)?.code
            post(ctx, "canvas:${it.id}".hashCode(), "canvas", it.title, listOfNotNull(course, it.preview.ifBlank { null }).joinToString(" · "), "canvas:${it.id}")
        }
        if (more > 0) post(ctx, SUMMARY_CANVAS, "canvas", "$more more Canvas updates", "Open Framy to see them all.", "tab:canvas")
    }

    fun mail(ctx: Context, items: List<MailItem>) {
        val (shown, more) = capped(items)
        shown.forEach { post(ctx, "mail:${it.uid}".hashCode(), "mail", it.fromName, "${it.subject}\n${it.snippet}", "mail:${it.uid}") }
        if (more > 0) post(ctx, SUMMARY_MAIL, "mail", "$more more emails", "Open Framy to see them all.", "tab:mail")
    }

    fun reminder(ctx: Context, item: PlannerItem, which: String) {
        val course = item.courseId?.let { id -> Store.canvas.value.courses.firstOrNull { it.id == id }?.code }
        val lead = if (which == "3h") "Due in 3 hours" else "Due in 24 hours"
        post(ctx, "planner:${item.key}:$which".hashCode(), "reminders", item.title,
            listOfNotNull(course, "$lead (${CanvasLogic.formatDue(item.dueAt)})").joinToString(" · "), "planner:${item.key}")
    }

    fun problem(ctx: Context, id: Int, text: String) = post(ctx, id, "problems", "Framy needs attention", text, "settings")

    fun runningNotification(ctx: Context): Notification = Notification.Builder(ctx, "running")
        .setSmallIcon(R.drawable.ic_notify).setContentTitle("Watching for new email")
        .setContentIntent(open(ctx, "tab:mail")).setOngoing(true).build()
}
