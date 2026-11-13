package app.framy.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import app.framy.Secrets
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.framy.Store
import app.framy.mail.MailService

/** Tiny string-route stack: "tab:canvas" | "tab:mail" | "canvas:<id>" | "planner:<key>" | "course:<id>" | "mail:<uid>" | "chat" | "settings". */
class MainActivity : ComponentActivity() {
    private val pending = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pending.value = intent.getStringExtra("open")
        MailService.start(applicationContext) // retry here: background starts can be refused
        setContent { FramyTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) { App() } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pending.value = intent.getStringExtra("open")
    }

    @Composable
    private fun App() {
        // Nothing configured yet: open on Settings (back goes to the Canvas tab).
        var stack by rememberSaveable {
            mutableStateOf(if (Secrets.get("canvasToken") == null && !MailService.configured()) "tab:canvas\nsettings" else "tab:canvas")
        }
        val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        val routes = stack.split('\n')
        fun push(r: String) { stack = (routes + r).joinToString("\n") }
        fun pop() { stack = routes.dropLast(1).joinToString("\n") }
        fun tab(t: String) { stack = t }

        pending.value?.let { target ->
            pending.value = null
            stack = when {
                target.startsWith("tab:") -> target
                target.startsWith("mail:") -> "tab:mail\n$target"
                target == "settings" -> "${routes.first()}\nsettings"
                else -> "tab:canvas\n$target"
            }
        }
        BackHandler(enabled = routes.size > 1) { pop() }

        val canvas by Store.canvas.collectAsState()
        val mail by Store.mail.collectAsState()
        val top = routes.last()
        Box(Modifier.fillMaxSize()) {
            when {
                top == "tab:canvas" -> CanvasScreen(canvas, onOpen = { push("canvas:${it.id}") }, onOpenPlanner = { push("planner:${it.key}") },
                    onOpenCourse = { push("course:${it.id}") }, onTab = ::tab, onChat = { push("chat") }, onSettings = { push("settings") })
                top == "tab:mail" -> MailScreen(mail, canvas.items.count { !it.read }, onOpen = { push("mail:${it.uid}") }, onTab = ::tab, onChat = { push("chat") }, onSettings = { push("settings") })
                top.startsWith("canvas:") -> canvas.items.firstOrNull { it.id == top.removePrefix("canvas:") }
                    ?.let { CanvasDetail(it, onBack = ::pop) } ?: pop()
                top.startsWith("planner:") -> canvas.planner.firstOrNull { it.key == top.removePrefix("planner:") }
                    ?.let { PlannerDetail(it, canvas.done.contains(it.key), onBack = ::pop) } ?: pop()
                top.startsWith("course:") -> canvas.courses.firstOrNull { it.id.toString() == top.removePrefix("course:") }
                    ?.let { CourseScreen(it, onBack = ::pop) } ?: pop()
                top.startsWith("mail:") -> mail.items.firstOrNull { it.uid.toString() == top.removePrefix("mail:") }
                    ?.let { MailDetail(it, onBack = ::pop) } ?: pop()
                top == "chat" -> ChatScreen(onBack = ::pop)
                top == "settings" -> SettingsScreen(onBack = ::pop)
                else -> tab("tab:canvas")
            }
        }
    }
}
