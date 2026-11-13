package app.framy.canvas

import app.framy.CanvasItem
import app.framy.Course
import app.framy.PlannerItem
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Splits a list into what gets an individual notification and how many overflow into one summary. */
fun <T> capped(list: List<T>, max: Int = 5): Pair<List<T>, Int> = list.take(max) to (list.size - max).coerceAtLeast(0)

/** Pure Canvas parsing and decision rules. No Android, no network. */
object CanvasLogic {
    private const val H = 3_600_000L
    private val SUBMITTABLE = setOf("assignment", "quiz", "discussion_topic")
    private val DUE_FMT = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

    fun parseIso(ts: String): Long = Instant.parse(ts).toEpochMilli()

    fun formatDue(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DUE_FMT.format(Instant.ofEpochMilli(ms).atZone(zone)).replace("am", "AM").replace("pm", "PM")

    /** Minimal HTML -> plain text for previews and snippets. */
    fun stripHtml(html: String): String = html
        .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</li>"), " ")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&rsquo;", "'")
        .replace(Regex("\\s+"), " ").trim()

    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).takeIf { it.isNotEmpty() }
    private fun JSONObject.lng(k: String): Long? = if (isNull(k) || !has(k)) null else optLong(k)
    private fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }

    fun parseCourses(json: String): List<Course> = JSONArray(json).objects().map {
        val id = it.getLong("id")
        Course(id, it.str("course_code") ?: it.str("name") ?: "course $id", it.str("name") ?: it.str("course_code") ?: "course $id")
    }

    fun parseActivity(json: String): List<CanvasItem> = JSONArray(json).objects().mapNotNull {
        val created = it.str("created_at") ?: return@mapNotNull null
        val type = it.str("type") ?: "Update"
        CanvasItem(
            id = it.get("id").toString(), type = type, title = it.str("title") ?: type,
            courseId = it.lng("course_id"), createdAt = parseIso(created), htmlUrl = it.str("html_url") ?: "",
            preview = stripHtml(it.str("message") ?: "").take(140),
        )
    }

    fun parsePlanner(json: String, baseUrl: String): List<PlannerItem> = JSONArray(json).objects().mapNotNull {
        val type = it.str("plannable_type") ?: return@mapNotNull null
        if (type !in SUBMITTABLE) return@mapNotNull null
        val p = it.optJSONObject("plannable") ?: JSONObject()
        val due = p.str("due_at") ?: (if (type == "assignment") it.str("plannable_date") else null) ?: return@mapNotNull null
        val subs = it.optJSONObject("submissions") // `false` when there is nothing to submit yet
        val url = it.str("html_url") ?: ""
        PlannerItem(
            key = "$type:${it.get("plannable_id")}", title = p.str("title") ?: type, courseId = it.lng("course_id"),
            dueAt = parseIso(due), submitted = subs != null && (subs.optBoolean("submitted") || subs.optBoolean("graded")),
            htmlUrl = if (url.startsWith("/")) baseUrl.trimEnd('/') + url else url,
        )
    }

    /** Unseen items from active courses (or course-less, e.g. conversations). Nothing on the very first run. */
    fun toNotify(fetched: List<CanvasItem>, seen: Set<String>, activeCourseIds: Set<Long>, firstRun: Boolean): List<CanvasItem> =
        if (firstRun) emptyList()
        else fetched.filter { it.id !in seen && (it.courseId == null || it.courseId in activeCourseIds) }

    /** Nearest threshold only: within 3 h fires "3h" (and suppresses "24h"); within 24 h fires "24h". */
    fun dueReminders(items: List<PlannerItem>, sent: Set<String>, done: Set<String>, now: Long): List<Pair<PlannerItem, String>> =
        items.mapNotNull {
            val left = it.dueAt - now
            when {
                it.submitted || it.key in done || left <= 0 -> null
                left <= 3 * H && "${it.key}:3h" !in sent -> it to "3h"
                left in (3 * H + 1)..(24 * H) && "${it.key}:24h" !in sent && "${it.key}:3h" !in sent -> it to "24h"
                else -> null
            }
        }
}
