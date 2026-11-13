package app.framy.ui

import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.framy.MailItem
import app.framy.MailState
import app.framy.R
import app.framy.Store
import app.framy.canvas.CanvasLogic
import app.framy.mail.MailLogic
import app.framy.mail.MailService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
private fun LivePill() {
    Row(
        Modifier.padding(end = 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(0xFF1DB954)))
        Text("Live", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailScreen(state: MailState, canvasUnread: Int, onOpen: (MailItem) -> Unit, onTab: (String) -> Unit, onChat: () -> Unit, onSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var sheetFor by remember { mutableStateOf<MailItem?>(null) }
    val configured = remember(state) { MailService.configured() }

    Scaffold(
        topBar = { TabTopBar("Mail", onChat, onSettings, extra = { if (state.live) LivePill() }) },
        bottomBar = { BottomTabs("tab:mail", canvasUnread, onTab) },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                !configured -> EmptyState("Add your Gmail address and app password to get email instantly.", "Open Settings", onSettings)
                state.authRejected -> EmptyState("Gmail rejected your app password. Create a new one.", "Open Settings", onSettings)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    val visible = MailLogic.visible(state.items, state.hidden)
                    if (visible.isEmpty()) item { EmptyState(if (state.live) "No email yet. New mail shows up here within seconds." else "Connecting to Gmail…") }
                    dayGroups(visible, { it.date }, { it.uid }) { m ->
                        TwoLineRow({ Avatar(m.fromName.take(1).uppercase(), m.fromAddr.hashCode().toLong()) }, m.fromName,
                            "${m.subject} · ${m.snippet}", shortTime(m.date), !m.read, { onOpen(m) }, onLongClick = { sheetFor = m })
                    }
                }
            }
        }
    }

    sheetFor?.let { m ->
        ModalBottomSheet(onDismissRequest = { sheetFor = null }) {
            TextButton(onClick = {
                sheetFor = null
                Store.updateMail { it.copy(hidden = it.hidden + m.fromAddr.lowercase()) }
                scope.launch {
                    val r = snack.showSnackbar("Hidden mail from ${m.fromAddr}", actionLabel = "Undo", duration = SnackbarDuration.Long)
                    if (r == SnackbarResult.ActionPerformed) Store.updateMail { it.copy(hidden = it.hidden - m.fromAddr.lowercase()) }
                }
            }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { Text("Hide mail from ${m.fromAddr}", fontSize = 16.sp) }
            Box(Modifier.size(24.dp))
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MailDetail(item: MailItem, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var body by remember(item.uid) { mutableStateOf<String?>(null) }
    var files by remember(item.uid) { mutableStateOf(emptyList<Pair<String, Long>>()) }
    var failed by remember(item.uid) { mutableStateOf(false) }

    LaunchedEffect(item.uid) {
        if (!item.read) Store.updateMail { s -> s.copy(items = s.items.map { if (it.uid == item.uid) it.copy(read = true) else it }) }
        withContext(Dispatchers.IO) {
            try { MailService.fetchBody(item.uid).let { body = it.first; files = it.second } } catch (e: Exception) { failed = true }
        }
    }

    DetailScaffold(item.fromName, onBack) {
        Text(item.subject, fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp)
        Text("${item.fromAddr} · ${CanvasLogic.formatDue(item.date)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        when {
            body != null -> RichText(body!!)
            failed -> { Text(item.snippet); Text("Couldn't load the full email. Check your connection.", color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }
            else -> LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (files.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            files.forEach { (name, size) ->
                AssistChip(onClick = {
                    scope.launch {
                        // shortcut: attachments live in cacheDir/att, which Android may clear under storage pressure; they re-download on tap.
                        val f = File(ctx.cacheDir, "att/${item.uid}/${name.replace('/', '_')}")
                        val ok = withContext(Dispatchers.IO) { runCatching { if (!f.exists()) MailService.saveAttachment(item.uid, name, f) }.isSuccess }
                        if (!ok) { Toast.makeText(ctx, "Couldn't download $name", Toast.LENGTH_SHORT).show(); return@launch }
                        val uri = FileProvider.getUriForFile(ctx, "app.framy.files", f)
                        val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                        catch (e: Exception) { Toast.makeText(ctx, "No app can open $name", Toast.LENGTH_SHORT).show() }
                    }
                }, label = { Text("$name (${size / 1024} KB)", maxLines = 1) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_file), null, Modifier.size(18.dp)) })
            }
        }
    }
}
