package app.framy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.framy.Course
import app.framy.Downloads
import app.framy.R
import app.framy.Store
import app.framy.canvas.CanvasApi
import app.framy.canvas.CanvasException
import app.framy.canvas.Fail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data object Hidden : Load<Nothing>
    data object Failed : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
}

private suspend fun <T> load(block: () -> T): Load<T> = withContext(Dispatchers.IO) {
    try { Load.Ok(block()) } catch (e: CanvasException) { if (e.fail is Fail.Hidden) Load.Hidden else Load.Failed } catch (e: Exception) { Load.Failed }
}

private fun JSONArray.objs() = (0 until length()).map { getJSONObject(it) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseScreen(course: Course, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Column { Text(course.code, fontSize = 22.sp); Text(course.name, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Modules") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Files") })
            }
            if (tab == 0) Modules(course) else Files(course)
        }
    }
}

@Composable
private fun Modules(course: Course) {
    var state by remember(course.id) { mutableStateOf<Load<List<JSONObject>>>(Load.Loading) }
    var open by rememberSaveable(course.id) { mutableStateOf(setOf<Long>()) }
    LaunchedEffect(course.id) {
        state = load { CanvasApi.modules(course.id).objs() }
        (state as? Load.Ok)?.value?.firstOrNull()?.let { if (open.isEmpty()) open = setOf(it.getLong("id")) }
    }
    when (val s = state) {
        Load.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
        Load.Hidden -> EmptyState("Modules are hidden for this course.")
        Load.Failed -> EmptyState("Couldn't load modules. Check your connection.")
        is Load.Ok -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
            if (s.value.isEmpty()) item { EmptyState("No modules in this course yet.") }
            items(s.value, key = { it.getLong("id") }) { m ->
                val id = m.getLong("id")
                // shortcut: modules with too many items omit "items" (Canvas sends items_url); those show only their header.
                val items = m.optJSONArray("items")?.objs()?.filter { it.optString("type") != "SubHeader" } ?: emptyList()
                val expanded = id in open
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).clickable { open = if (expanded) open - id else open + id }.padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(m.optString("name"), Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${m.optInt("items_count", items.size)} items", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(painterResource(R.drawable.ic_chevron), null, Modifier.rotate(if (expanded) 180f else 0f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (expanded) items.forEach { ModuleItem(course.id, it) }
                }
            }
        }
    }
}

@Composable
private fun ModuleItem(courseId: Long, it: JSONObject) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloaded by Store.canvas.collectAsState()
    val type = it.optString("type")
    val isFile = type == "File"
    val fileId = it.optLong("content_id")
    val done = isFile && fileId in downloaded.downloaded
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val (bg, fg) = if (isFile) LocalCourseColor.current(courseId) else MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(bg), contentAlignment = Alignment.Center) {
            Icon(painterResource(if (isFile) R.drawable.ic_file else R.drawable.ic_link), null, Modifier.size(20.dp), tint = fg)
        }
        Column(Modifier.weight(1f)) {
            Text(it.optString("title"), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(typeLabel(type), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isFile) IconButton(onClick = {
            scope.launch {
                val r = load { CanvasApi.fileDownloadUrl(it.optString("url")) }
                if (r is Load.Ok) Downloads.enqueue(ctx, fileId, r.value.first, r.value.second)
                else android.widget.Toast.makeText(ctx, "Couldn't get that file", android.widget.Toast.LENGTH_SHORT).show()
            }
        }) {
            Icon(painterResource(if (done) R.drawable.ic_check else R.drawable.ic_download), contentDescription = if (done) "Downloaded" else "Download",
                tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        } else IconButton(onClick = { openExternally(ctx, it.optString("external_url").ifEmpty { it.optString("html_url") }) }) {
            Icon(painterResource(R.drawable.ic_open_external), contentDescription = "Open in browser")
        }
    }
}

private fun typeLabel(t: String) = when (t) {
    "File" -> "File"; "Page" -> "Page"; "Quiz" -> "Quiz"; "Assignment" -> "Assignment"; "Discussion" -> "Discussion"
    "ExternalUrl" -> "Link"; "ExternalTool" -> "External tool"; else -> t
}

@Composable
private fun Files(course: Course) {
    val ctx = LocalContext.current
    val downloaded by Store.canvas.collectAsState()
    var folders by remember(course.id) { mutableStateOf<Load<List<JSONObject>>>(Load.Loading) }
    var path by rememberSaveable(course.id) { mutableStateOf(listOf<Long>()) } // folder ids, root first
    var files by remember(course.id) { mutableStateOf<Load<List<JSONObject>>>(Load.Loading) }

    LaunchedEffect(course.id) {
        folders = load { CanvasApi.folders(course.id).objs() }
        (folders as? Load.Ok)?.value?.firstOrNull { it.isNull("parent_folder_id") }?.let { if (path.isEmpty()) path = listOf(it.getLong("id")) }
    }
    val current = path.lastOrNull()
    LaunchedEffect(current) { if (current != null) { files = Load.Loading; files = load { CanvasApi.files(current).objs() } } }
    androidx.activity.compose.BackHandler(enabled = path.size > 1) { path = path.dropLast(1) }

    when (val f = folders) {
        Load.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
        Load.Hidden -> EmptyState("Files are hidden for this course, check Modules")
        Load.Failed -> EmptyState("Couldn't load files. Check your connection.")
        is Load.Ok -> LazyColumn(Modifier.fillMaxSize()) {
            val subs = f.value.filter { !it.isNull("parent_folder_id") && it.getLong("parent_folder_id") == current }
            if (path.size > 1) item { SectionHeader(f.value.firstOrNull { it.getLong("id") == current }?.optString("name") ?: "") }
            items(subs, key = { "d" + it.getLong("id") }) { d ->
                FileRow(R.drawable.ic_folder, d.optString("name"), "${d.optInt("files_count")} files", null) { path = path + d.getLong("id") }
            }
            when (val fl = files) {
                Load.Loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                Load.Hidden -> item { EmptyState("Files are hidden for this course, check Modules") }
                Load.Failed -> item { EmptyState("Couldn't load files. Check your connection.") }
                is Load.Ok -> {
                    if (fl.value.isEmpty() && subs.isEmpty()) item { EmptyState("This folder is empty.") }
                    items(fl.value, key = { "f" + it.getLong("id") }) { file ->
                        val id = file.getLong("id")
                        FileRow(R.drawable.ic_file, file.optString("display_name"), sizeLabel(file.optLong("size")), id in downloaded.downloaded) {
                            val url = file.optString("url")
                            if (url.isBlank()) android.widget.Toast.makeText(ctx, "This file is locked", android.widget.Toast.LENGTH_SHORT).show()
                            else Downloads.enqueue(ctx, id, url, file.optString("display_name"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(icon: Int, name: String, sub: String, downloaded: Boolean?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(72.dp).clickable(onClick = onClick).padding(start = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (downloaded != null) Icon(painterResource(if (downloaded) R.drawable.ic_check else R.drawable.ic_download), contentDescription = if (downloaded) "Downloaded" else "Download",
            tint = if (downloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun sizeLabel(b: Long) = when { b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0); b >= 1024 -> "${b / 1024} KB"; else -> "$b B" }
