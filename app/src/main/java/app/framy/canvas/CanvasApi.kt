package app.framy.canvas

import app.framy.CanvasItem
import app.framy.Course
import app.framy.PlannerItem
import app.framy.Prefs
import app.framy.Secrets
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

sealed interface Fail {
    data object TokenRejected : Fail
    /** Faculty hid this part of the course (401/403 outside /users/self). */
    data object Hidden : Fail
    data class Other(val code: Int) : Fail
}

class CanvasException(val fail: Fail) : Exception(fail.toString())

/** Only 401/403 on the user's own endpoints mean the token is dead; elsewhere it means "hidden by faculty". */
fun classify(code: Int, path: String): Fail? = when {
    code in 200..299 -> null
    (code == 401 || code == 403) && path.startsWith("/api/v1/users/self") -> Fail.TokenRejected
    code == 401 || code == 403 -> Fail.Hidden
    else -> Fail.Other(code)
}

/** URL of rel="next" in a Canvas Link header, if any. */
fun nextLink(header: String?): String? = header?.split(',')
    ?.firstOrNull { it.contains("rel=\"next\"") }
    ?.substringAfter('<')?.substringBefore('>')

/** Read-only Canvas client: every call is a GET to the user's own Canvas host. */
object CanvasApi {
    private const val MAX_PAGES = 5

    private fun base() = Prefs.canvasBaseUrl
    private fun token() = Secrets.get("canvasToken") ?: throw CanvasException(Fail.TokenRejected)

    private class Resp(val body: String, val next: String?)

    private fun get(url: String, token: String = token()): Resp {
        val u = URL(url)
        val c = (u.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 20_000; readTimeout = 20_000
            instanceFollowRedirects = false // never carry the token to another host
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        try {
            classify(c.responseCode, u.path)?.let { throw CanvasException(it) }
            return Resp(c.inputStream.bufferedReader().use { it.readText() }, nextLink(c.getHeaderField("Link")))
        } finally { c.disconnect() }
    }

    private fun getAllPages(url: String): JSONArray {
        val out = JSONArray()
        var next: String? = url
        // shortcut: stops after 5 pages (500 modules/files); raise MAX_PAGES if a course ever has more.
        repeat(MAX_PAGES) {
            val r = get(next ?: return out)
            JSONArray(r.body).let { a -> for (i in 0 until a.length()) out.put(a.get(i)) }
            next = r.next
        }
        return out
    }

    fun courses(): List<Course> = CanvasLogic.parseCourses(get("${base()}/api/v1/users/self/courses?enrollment_state=active&per_page=100").body)
    fun activity(): List<CanvasItem> = CanvasLogic.parseActivity(get("${base()}/api/v1/users/self/activity_stream?per_page=50").body)

    fun planner(now: Long): List<PlannerItem> {
        val from = URLEncoder.encode(Instant.ofEpochMilli(now).toString(), "UTF-8")
        val to = URLEncoder.encode(Instant.ofEpochMilli(now + 14L * 24 * 3_600_000).toString(), "UTF-8")
        return CanvasLogic.parsePlanner(get("${base()}/api/v1/planner/items?start_date=$from&end_date=$to&per_page=100").body, base())
    }

    fun modules(courseId: Long): JSONArray = getAllPages("${base()}/api/v1/courses/$courseId/modules?include[]=items&per_page=100")
    fun folders(courseId: Long): JSONArray = getAllPages("${base()}/api/v1/courses/$courseId/folders?per_page=100")
    fun files(folderId: Long): JSONArray = getAllPages("${base()}/api/v1/folders/$folderId/files?per_page=100")

    /** Module File items carry an API url; resolve it to the (token-free, verifier-signed) download url. */
    fun fileDownloadUrl(fileApiUrl: String): Pair<String, String> {
        require(fileApiUrl.startsWith(base())) { "refusing to send the token off the Canvas host" }
        val o = JSONObject(get(fileApiUrl).body)
        return o.getString("url") to (o.optString("display_name").ifEmpty { o.optString("filename", "file") })
    }

    fun topic(courseId: Long, topicId: Long): JSONObject = JSONObject(get("${base()}/api/v1/courses/$courseId/discussion_topics/$topicId").body)
    fun conversation(id: Long): JSONObject = JSONObject(get("${base()}/api/v1/conversations/$id?auto_mark_as_read=false").body)

    /** True if this base URL + token can read /users/self. */
    fun validate(baseUrl: String, token: String): Boolean = try {
        get("${baseUrl.trim().trimEnd('/')}/api/v1/users/self", token); true
    } catch (e: Exception) { false }
}
