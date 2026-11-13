package app.framy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.framy.Prefs
import app.framy.R
import app.framy.Secrets
import app.framy.chat.ChatApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val adminKey = remember { Secrets.get("adminKey") }
    var comments by remember { mutableStateOf<List<ChatApi.Comment>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var nick by remember { mutableStateOf(Prefs.nick) }
    var text by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    var rateLimited by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        error = null; comments = null
        comments = withContext(Dispatchers.IO) { runCatching { ChatApi.list() }.getOrNull() }
        if (comments == null) error = "Global chat is unavailable right now"
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Global chat", fontSize = 22.sp) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back") } })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            when {
                error != null -> Column(Modifier.weight(1f)) { EmptyState(error!!, "Retry") { reload++ } }
                comments == null -> Column(Modifier.weight(1f)) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                else -> LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                    if (comments!!.isEmpty()) item { EmptyState("No messages yet. Report a bug or say hi.") }
                    items(comments!!, key = { it.id }) { c ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(c.nick, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("#${c.tag}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (c.isDev) Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                    Text("Developer", Modifier.padding(horizontal = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                }
                                Text(shortTime(c.createdAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(c.body, fontSize = 16.sp, lineHeight = 22.sp)
                            if (adminKey != null) TextButton(onClick = {
                                scope.launch {
                                    if (withContext(Dispatchers.IO) { runCatching { ChatApi.delete(c.id, adminKey) }.isSuccess }) comments = comments?.filter { it.id != c.id }
                                }
                            }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            if (error == null) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nick, { nick = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Name (optional) · shown as #${Prefs.tag}") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(text, { text = it.take(1000) }, Modifier.weight(1f), label = { Text("Message") }, maxLines = 4,
                        supportingText = { Text("${text.length}/1000") })
                    Button(enabled = text.isNotBlank() && !posting, onClick = {
                        scope.launch {
                            posting = true
                            Prefs.nick = nick
                            val r = withContext(Dispatchers.IO) { runCatching { ChatApi.post(nick, Prefs.tag, text.trim(), adminKey) } }
                            posting = false
                            rateLimited = r.exceptionOrNull() is ChatApi.RateLimited
                            if (r.isSuccess) { text = ""; reload++ }
                            else if (!rateLimited) error = "Global chat is unavailable right now" // draft stays in `text`
                        }
                    }, modifier = Modifier.padding(top = 8.dp)) { Text("Post") }
                }
                if (rateLimited) Text("Slow down a little, try again in a minute", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }
        }
    }
}
