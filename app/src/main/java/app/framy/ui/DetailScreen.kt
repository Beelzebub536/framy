package app.framy.ui

import android.content.Intent
import android.net.Uri
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import app.framy.CanvasItem
import app.framy.PlannerItem
import app.framy.R
import app.framy.Store
import app.framy.canvas.CanvasApi
import app.framy.canvas.CanvasLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title, fontSize = 22.sp, maxLines = 1) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back") } })
    }) { pad -> Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() } }
}

private val TAG = Regex("<[a-zA-Z][^>]*>")

/** Renders HTML (or plain text) with tappable links. No ImageGetter: remote images are never loaded. */
@Composable
fun RichText(body: String) {
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val link = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        factory = { TextView(it).apply { movementMethod = LinkMovementMethod.getInstance(); textSize = 16f; setLineSpacing(0f, 1.25f) } },
        update = {
            it.setTextColor(color); it.setLinkTextColor(link)
            if (TAG.containsMatchIn(body)) it.text = HtmlCompat.fromHtml(body, HtmlCompat.FROM_HTML_MODE_COMPACT).trim()
            else { it.text = body; android.text.util.Linkify.addLinks(it, android.text.util.Linkify.WEB_URLS) }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

fun openExternally(ctx: android.content.Context, url: String) {
    if (url.isNotBlank()) ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private val TOPIC = Regex("/courses/(\\d+)/(?:discussion_topics|announcements)/(\\d+)")
private val CONVO = Regex("/conversations/(\\d+)")

/** Canvas file attachment: display name + verifier-signed download url (no token needed). */
data class Attachment(val id: Long, val name: String, val url: String)

@Composable
fun CanvasDetail(item: CanvasItem, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val code = Store.canvas.collectAsState().value.courses.firstOrNull { it.id == item.courseId }?.code
    var body by remember(item.id) { mutableStateOf(item.preview) }
    var sender by remember(item.id) { mutableStateOf<String?>(null) }
    var files by remember(item.id) { mutableStateOf(emptyList<Attachment>()) }

    LaunchedEffect(item.id) {
        if (!item.read) Store.updateCanvas { s -> s.copy(items = s.items.map { if (it.id == item.id) it.copy(read = true) else it }) }
        withContext(Dispatchers.IO) {
            try {
                fun attachments(a: org.json.JSONArray?) = a?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) }
                    .filter { it.optString("url").isNotEmpty() }.map { Attachment(it.optLong("id"), it.optString("display_name", "file"), it.getString("url")) } } ?: emptyList()
                TOPIC.find(item.htmlUrl)?.let { m ->
                    val t = CanvasApi.topic(m.groupValues[1].toLong(), m.groupValues[2].toLong())
                    body = t.optString("message").ifBlank { body }
                    sender = t.optJSONObject("author")?.optString("display_name")
                    files = attachments(t.optJSONArray("attachments"))
                } ?: CONVO.find(item.htmlUrl)?.let { m ->
                    val c = CanvasApi.conversation(m.groupValues[1].toLong())
                    val msgs = c.optJSONArray("messages") ?: return@let
                    val best = (0 until msgs.length()).map { msgs.getJSONObject(it) }
                        .minByOrNull { kotlin.math.abs(runCatching { CanvasLogic.parseIso(it.getString("created_at")) }.getOrDefault(0) - item.createdAt) } ?: return@let
                    body = best.optString("body").ifBlank { body }
                    val parts = c.optJSONArray("participants")
                    sender = parts?.let { p -> (0 until p.length()).map { p.getJSONObject(it) }.firstOrNull { it.optLong("id") == best.optLong("author_id") }?.optString("name") }
                    files = attachments(best.optJSONArray("attachments"))
                }
            } catch (e: Exception) { /* offline or hidden: keep the preview */ }
        }
    }

    DetailScaffold(code ?: "Canvas", onBack) {
        Text(item.title, fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp)
        Text(listOfNotNull(sender, CanvasLogic.formatDue(item.createdAt)).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        RichText(body)
        files.forEach { f -> FilledTonalButton(onClick = { app.framy.Downloads.enqueue(ctx, f.id, f.url, f.name) }) { Text("Download ${f.name}") } }
        if (item.htmlUrl.isNotBlank()) OutlinedButton(onClick = { openExternally(ctx, item.htmlUrl) }) { Text("Open in Canvas") }
    }
}

@Composable
fun PlannerDetail(item: PlannerItem, done: Boolean, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val code = Store.canvas.collectAsState().value.courses.firstOrNull { it.id == item.courseId }?.code
    DetailScaffold(code ?: "Assignment", onBack) {
        Text(item.title, fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp)
        Text("Due ${CanvasLogic.formatDue(item.dueAt)}", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            when { item.submitted -> "Canvas shows this as submitted."; done -> "Marked done here. No more reminders."; else -> "You'll get a reminder 24 hours and 3 hours before it's due." },
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!item.submitted) Button(onClick = {
                Store.updateCanvas { s -> s.copy(done = if (done) s.done - item.key else s.done + item.key) }
            }) { Text(if (done) "Undo done" else "Mark done") }
            if (item.htmlUrl.isNotBlank()) OutlinedButton(onClick = { openExternally(ctx, item.htmlUrl) }) { Text("Open in Canvas") }
        }
    }
}
