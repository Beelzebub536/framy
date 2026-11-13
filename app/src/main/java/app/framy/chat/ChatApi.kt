package app.framy.chat

import app.framy.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** The one part of Framy that sends: only from the Global chat screen, only when the user acts. */
object ChatApi {
    data class Comment(val id: Long, val nick: String, val tag: String, val body: String, val isDev: Boolean, val createdAt: Long)
    class RateLimited : IOException("rate limited")

    fun parse(json: String): List<Comment> = JSONArray(json).let { a ->
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            Comment(it.getLong("id"), it.optString("nick", "Anonymous"), it.optString("tag"), it.optString("body"),
                it.optBoolean("is_dev"), runCatching { Instant.parse(it.getString("created_at")).toEpochMilli() }.getOrDefault(0))
        }
    }

    val available get() = BuildConfig.CHAT_URL.isNotBlank()

    private fun call(method: String, path: String, body: String? = null, adminKey: String? = null): String {
        if (!available) throw IOException("no chat url")
        val c = (URL(BuildConfig.CHAT_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 10_000; readTimeout = 15_000 // Neon may be waking up
            adminKey?.let { setRequestProperty("X-Admin-Key", it) }
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json"); outputStream.use { it.write(body.toByteArray()) } }
        }
        try {
            if (c.responseCode == 429) throw RateLimited()
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    fun list(): List<Comment> = parse(call("GET", "/comments"))

    fun post(nick: String, tag: String, body: String, adminKey: String?): Long =
        JSONObject(call("POST", "/comments", JSONObject().put("nick", nick).put("tag", tag).put("body", body).toString(), adminKey)).getLong("id")

    fun delete(id: Long, adminKey: String) { call("DELETE", "/comments/$id", adminKey = adminKey) }
}
