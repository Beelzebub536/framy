package app.framy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.framy.CanvasItem
import app.framy.CanvasState
import app.framy.Course
import app.framy.PlannerItem
import app.framy.R
import app.framy.Secrets
import app.framy.Tick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: () -> Unit) =
    this.then(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick))

// ---- shared chrome (used by Canvas and Mail tabs) ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabTopBar(title: String, onChat: () -> Unit, onSettings: () -> Unit, extra: @Composable () -> Unit = {}, onRefresh: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(title, fontSize = 22.sp) },
        actions = {
            extra()
            IconButton(onClick = onChat) { Icon(painterResource(R.drawable.ic_chat), contentDescription = "Global chat") }
            if (onRefresh != null) IconButton(onClick = onRefresh) { Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh") }
            IconButton(onClick = onSettings) { Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings") }
        },
    )
}

@Composable
fun BottomTabs(current: String, canvasUnread: Int, onTab: (String) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = current == "tab:canvas", onClick = { onTab("tab:canvas") }, label = { Text("Canvas") },
            icon = {
                BadgedBox(badge = { if (canvasUnread > 0) Badge { Text(if (canvasUnread > 99) "99+" else "$canvasUnread") } }) {
                    Icon(painterResource(R.drawable.ic_canvas), contentDescription = null)
                }
            },
        )
        NavigationBarItem(
            selected = current == "tab:mail", onClick = { onTab("tab:mail") }, label = { Text("Mail") },
            icon = { Icon(painterResource(R.drawable.ic_mail), contentDescription = null) },
        )
    }
}

/** 40dp circle with a short label, coloured by course. */
@Composable
fun Avatar(label: String, courseId: Long?) {
    val (bg, fg) = courseId?.let { LocalCourseColor.current(it) }
        ?: (MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer)
    Box(Modifier.size(40.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** The 72dp two-line row used by every list. */
@Composable
fun TwoLineRow(leading: @Composable () -> Unit, headline: String, supporting: String, trailing: String, unread: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .then(if (onLongClick != null) Modifier.combinedClickableCompat(onClick, onLongClick) else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(headline, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(supporting, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.align(Alignment.Top).padding(top = 4.dp), horizontalAlignment = Alignment.End) {
            Text(trailing, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            if (unread) Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
fun SectionHeader(text: String) =
    Text(text, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)

@Composable
fun EmptyState(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        if (action != null) { Spacer(Modifier.size(16.dp)); Button(onClick = onAction) { Text(action) } }
    }
}

/** "2h", "9:32", "Fri", "12 Nov" — compact trailing time. */
fun shortTime(ms: Long, now: Long = System.currentTimeMillis()): String {
    val zone = ZoneId.systemDefault()
    val t = Instant.ofEpochMilli(ms).atZone(zone)
    val days = java.time.temporal.ChronoUnit.DAYS.between(t.toLocalDate(), LocalDate.now(zone))
    val mins = (now - ms) / 60_000
    return when {
        mins < 1 -> "now"
        mins < 60 -> "${mins}m"
        days == 0L -> "${mins / 60}h"
        days < 7 -> t.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))
        else -> t.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
    }
}

/** Groups by local day: Today / Yesterday / Earlier. */
fun <T> LazyListScope.dayGroups(items: List<T>, time: (T) -> Long, key: (T) -> Any, row: @Composable (T) -> Unit) {
    val today = LocalDate.now()
    val groups = items.groupBy {
        val d = Instant.ofEpochMilli(time(it)).atZone(ZoneId.systemDefault()).toLocalDate()
        when (d) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> "Earlier" }
    }
    for (g in listOf("Today", "Yesterday", "Earlier")) {
        val list = groups[g] ?: continue
        item(key = "h-$g") { SectionHeader(g) }
        items(list, key = key) { row(it) }
    }
}

// ---- Canvas tab ----

@Composable
fun CanvasScreen(
    state: CanvasState, onOpen: (CanvasItem) -> Unit, onOpenPlanner: (PlannerItem) -> Unit, onOpenCourse: (Course) -> Unit,
    onTab: (String) -> Unit, onChat: () -> Unit, onSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by rememberSaveable { mutableStateOf(false) }
    var segment by rememberSaveable { mutableStateOf(0) }
    val codes = state.courses.associate { it.id to it.code }

    Scaffold(
        topBar = {
            TabTopBar("Canvas", onChat, onSettings, onRefresh = {
                if (!refreshing) scope.launch { refreshing = true; withContext(Dispatchers.IO) { Tick.run(ctx.applicationContext) }; refreshing = false }
            })
        },
        bottomBar = { BottomTabs("tab:canvas", state.items.count { !it.read }, onTab) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                listOf("Updates", "Courses").forEachIndexed { i, label ->
                    SegmentedButton(selected = segment == i, onClick = { segment = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                }
            }
            when {
                Secrets.get("canvasToken") == null -> EmptyState("Add your Canvas access token to get updates.", "Open Settings", onSettings)
                state.tokenRejected -> EmptyState("Canvas rejected your token. Paste a new one.", "Open Settings", onSettings)
                segment == 1 -> LazyColumn {
                    if (state.courses.isEmpty()) item { EmptyState("No active courses yet. Pull refresh after your first sync.") }
                    items(state.courses, key = { it.id }) { c ->
                        TwoLineRow({ Avatar(c.code.take(3).uppercase(), c.id) }, c.code, c.name, "", false, { onOpenCourse(c) })
                    }
                }
                else -> LazyColumn {
                    val now = System.currentTimeMillis()
                    val due = state.planner.filter { !it.submitted && it.key !in state.done && it.dueAt > now }.sortedBy { it.dueAt }
                    if (due.isNotEmpty()) {
                        item(key = "h-due") { SectionHeader("Due soon") }
                        items(due.chunked(2), key = { it.first().key }) { pair ->
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { DueCard(it, codes[it.courseId], now, Modifier.weight(1f)) { onOpenPlanner(it) } }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    if (state.items.isEmpty() && due.isEmpty()) item { EmptyState("Nothing yet. New announcements and messages will show up here.") }
                    dayGroups(state.items, { it.createdAt }, { it.id }) { item ->
                        val code = codes[item.courseId]
                        TwoLineRow(
                            { Avatar(code?.take(3)?.uppercase() ?: "✉", item.courseId) },
                            item.title, listOfNotNull(code, item.preview.ifBlank { null }).joinToString(" · "),
                            shortTime(item.createdAt), !item.read, { onOpen(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DueCard(item: PlannerItem, code: String?, now: Long, modifier: Modifier, onClick: () -> Unit) {
    val hours = (item.dueAt - now) / 3_600_000
    val urgent = hours < 24
    val days = hours / 24
    val left = when { hours < 1 -> "Under 1h left"; hours < 24 -> "${hours}h left"; days == 1L -> "1 day"; else -> "$days days" }
    Surface(modifier = modifier.heightIn(min = 132.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, onClick = onClick) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (code != null) {
                val (bg, fg) = LocalCourseColor.current(item.courseId ?: 0)
                Text(code, Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp), color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Text(item.title, Modifier.weight(1f, fill = false), fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f, fill = false))
            Text(left, color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(app.framy.canvas.CanvasLogic.formatDue(item.dueAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
