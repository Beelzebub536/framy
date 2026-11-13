package app.framy

data class Course(val id: Long, val code: String, val name: String)

data class CanvasItem(
    val id: String, val type: String, val title: String, val courseId: Long?,
    val createdAt: Long /* epoch ms */, val htmlUrl: String, val preview: String, val read: Boolean = false,
)

data class PlannerItem(
    val key: String /* "<plannable_type>:<plannable_id>" */, val title: String, val courseId: Long?,
    val dueAt: Long, val submitted: Boolean, val htmlUrl: String, /* absolute */
)

data class MailItem(
    val uid: Long, val fromName: String, val fromAddr: String, val subject: String,
    val date: Long, val snippet: String, val read: Boolean = false,
)

data class CanvasState(
    val courses: List<Course> = emptyList(), val items: List<CanvasItem> = emptyList(),
    val planner: List<PlannerItem> = emptyList(), val seen: Set<String> = emptySet(),
    val remindersSent: Set<String> = emptySet(), val done: Set<String> = emptySet(),
    val downloaded: Set<Long> = emptySet(), val firstRunDone: Boolean = false, val tokenRejected: Boolean = false,
)

data class MailState(
    val uidValidity: Long = -1, val lastUid: Long = -1, val items: List<MailItem> = emptyList(),
    val hidden: Set<String> = emptySet(), val live: Boolean = false, val authRejected: Boolean = false,
)
