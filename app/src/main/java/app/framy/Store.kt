package app.framy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Pure JSON (de)serialisation of the two state blobs. Corrupt input yields empty state, never a crash. */
object StoreJson {
    private fun strings(s: Collection<String>) = JSONArray(s.toList())
    private fun JSONArray?.strs(): Set<String> = this?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
    private fun <T> JSONArray?.objs(f: (JSONObject) -> T): List<T> = this?.let { a -> (0 until a.length()).map { f(a.getJSONObject(it)) } } ?: emptyList()
    private fun JSONObject.longOrNull(k: String): Long? = if (isNull(k) || !has(k)) null else getLong(k)

    fun canvasToJson(s: CanvasState): String = JSONObject()
        .put("courses", JSONArray(s.courses.map { JSONObject().put("id", it.id).put("code", it.code).put("name", it.name) }))
        .put("items", JSONArray(s.items.map {
            JSONObject().put("id", it.id).put("type", it.type).put("title", it.title).put("courseId", it.courseId ?: JSONObject.NULL)
                .put("createdAt", it.createdAt).put("htmlUrl", it.htmlUrl).put("preview", it.preview).put("read", it.read)
        }))
        .put("planner", JSONArray(s.planner.map {
            JSONObject().put("key", it.key).put("title", it.title).put("courseId", it.courseId ?: JSONObject.NULL)
                .put("dueAt", it.dueAt).put("submitted", it.submitted).put("htmlUrl", it.htmlUrl)
        }))
        .put("seen", strings(s.seen)).put("remindersSent", strings(s.remindersSent)).put("done", strings(s.done))
        .put("downloaded", JSONArray(s.downloaded.toList())).put("firstRunDone", s.firstRunDone).put("tokenRejected", s.tokenRejected)
        .toString()

    fun canvasFromJson(j: String): CanvasState = try {
        val o = JSONObject(j)
        CanvasState(
            courses = o.optJSONArray("courses").objs { Course(it.getLong("id"), it.getString("code"), it.getString("name")) },
            items = o.optJSONArray("items").objs {
                CanvasItem(it.getString("id"), it.getString("type"), it.getString("title"), it.longOrNull("courseId"),
                    it.getLong("createdAt"), it.getString("htmlUrl"), it.getString("preview"), it.optBoolean("read"))
            },
            planner = o.optJSONArray("planner").objs {
                PlannerItem(it.getString("key"), it.getString("title"), it.longOrNull("courseId"), it.getLong("dueAt"),
                    it.getBoolean("submitted"), it.getString("htmlUrl"))
            },
            seen = o.optJSONArray("seen").strs(), remindersSent = o.optJSONArray("remindersSent").strs(),
            done = o.optJSONArray("done").strs(),
            downloaded = o.optJSONArray("downloaded")?.let { a -> (0 until a.length()).map { a.getLong(it) }.toSet() } ?: emptySet(),
            firstRunDone = o.optBoolean("firstRunDone"), tokenRejected = o.optBoolean("tokenRejected"),
        )
    } catch (e: Exception) { CanvasState() }

    fun mailToJson(s: MailState): String = JSONObject()
        .put("uidValidity", s.uidValidity).put("lastUid", s.lastUid)
        .put("items", JSONArray(s.items.map {
            JSONObject().put("uid", it.uid).put("fromName", it.fromName).put("fromAddr", it.fromAddr).put("subject", it.subject)
                .put("date", it.date).put("snippet", it.snippet).put("read", it.read)
        }))
        .put("hidden", strings(s.hidden)).put("live", s.live).put("authRejected", s.authRejected)
        .toString()

    fun mailFromJson(j: String): MailState = try {
        val o = JSONObject(j)
        MailState(
            o.optLong("uidValidity", -1), o.optLong("lastUid", -1),
            o.optJSONArray("items").objs {
                MailItem(it.getLong("uid"), it.getString("fromName"), it.getString("fromAddr"), it.getString("subject"),
                    it.getLong("date"), it.getString("snippet"), it.optBoolean("read"))
            },
            o.optJSONArray("hidden").strs(), o.optBoolean("live"), o.optBoolean("authRejected"),
        )
    } catch (e: Exception) { MailState() }
}

/** App state in two small JSON files, exposed as StateFlows. Writes are atomic (temp file + rename). */
object Store {
    private const val MAX_ITEMS = 200
    private lateinit var dir: File
    private val canvasFlow = MutableStateFlow(CanvasState())
    private val mailFlow = MutableStateFlow(MailState())
    val canvas: StateFlow<CanvasState> = canvasFlow
    val mail: StateFlow<MailState> = mailFlow

    fun init(dir: File) {
        this.dir = dir
        canvasFlow.value = File(dir, "canvas.json").takeIf { it.exists() }?.readText()?.let(StoreJson::canvasFromJson) ?: CanvasState()
        // "live" is runtime-only: nothing is connected right after a process start.
        mailFlow.value = (File(dir, "mail.json").takeIf { it.exists() }?.readText()?.let(StoreJson::mailFromJson) ?: MailState()).copy(live = false)
    }

    @Synchronized fun updateCanvas(f: (CanvasState) -> CanvasState) {
        val s = f(canvasFlow.value).let { it.copy(items = it.items.sortedByDescending { i -> i.createdAt }.take(MAX_ITEMS)) }
        write("canvas.json", StoreJson.canvasToJson(s)); canvasFlow.value = s
    }

    @Synchronized fun updateMail(f: (MailState) -> MailState) {
        val s = f(mailFlow.value).let { it.copy(items = it.items.sortedByDescending { i -> i.uid }.take(MAX_ITEMS)) }
        write("mail.json", StoreJson.mailToJson(s)); mailFlow.value = s
    }

    private fun write(name: String, text: String) {
        val tmp = File(dir, "$name.tmp"); tmp.writeText(text); tmp.renameTo(File(dir, name))
    }
}
