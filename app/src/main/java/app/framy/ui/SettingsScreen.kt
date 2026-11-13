package app.framy.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.framy.BuildConfig
import app.framy.Prefs
import app.framy.R
import app.framy.Secrets
import app.framy.Store
import app.framy.Tick
import app.framy.canvas.CanvasApi
import app.framy.mail.MailService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SWATCHES = listOf("slate" to 0xFF5E7FA8, "sage" to 0xFF6F8F7A, "teal" to 0xFF5B8E8E, "sand" to 0xFFA08F72, "rose" to 0xFFA87A80)

@Composable
private fun Section(title: String) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val mail by Store.mail.collectAsState()

    // Canvas
    var baseUrl by remember { mutableStateOf(Prefs.canvasBaseUrl) }
    var token by remember { mutableStateOf("") }
    var canvasMsg by remember { mutableStateOf<String?>(null) }
    var canvasBusy by remember { mutableStateOf(false) }
    val hasToken = remember(canvasMsg) { Secrets.get("canvasToken") != null }
    // Gmail
    var address by remember { mutableStateOf(Secrets.get("mailAddress") ?: "") }
    var password by remember { mutableStateOf("") }
    var mailMsg by remember { mutableStateOf<String?>(null) }
    var mailBusy by remember { mutableStateOf(false) }
    var hasMail by remember { mutableStateOf(MailService.configured()) }
    // Admin
    var taps by remember { mutableIntStateOf(0) }
    var adminKey by remember { mutableStateOf("") }

    DetailScaffold("Settings", onBack) {
        Text("Canvas", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text("Canvas address") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        OutlinedTextField(token, { token = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(),
            label = { Text(if (hasToken) "Access token (saved, paste to replace)" else "Access token") },
            supportingText = { Text(canvasMsg ?: "Canvas → Account → Settings → New access token", color = if (canvasMsg?.startsWith("Saved") == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) })
        Button(enabled = !canvasBusy && token.isNotBlank(), onClick = {
            scope.launch {
                canvasBusy = true
                val ok = withContext(Dispatchers.IO) { CanvasApi.validate(baseUrl, token) }
                if (ok) {
                    Prefs.canvasBaseUrl = baseUrl; Secrets.put("canvasToken", token); token = ""
                    Store.updateCanvas { it.copy(tokenRejected = false) }
                    canvasMsg = "Saved. Fetching your courses…"
                    withContext(Dispatchers.IO) { Tick.run(ctx) }
                    canvasMsg = "Saved."
                } else canvasMsg = "Canvas didn't accept that token or address."
                canvasBusy = false
            }
        }) { Text(if (canvasBusy) "Checking…" else "Save token") }

        Section("Gmail")
        OutlinedTextField(address, { address = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Gmail address") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(password, { password = it.replace(" ", "") }, Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(),
            label = { Text(if (hasMail) "App password (saved, type to replace)" else "App password") },
            supportingText = { Text(mailMsg ?: "Needs 2-Step Verification. Create one at myaccount.google.com/apppasswords", color = if (mailMsg?.startsWith("Connected") == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !mailBusy && address.isNotBlank() && password.isNotBlank(), onClick = {
                scope.launch {
                    mailBusy = true
                    val ok = withContext(Dispatchers.IO) { MailService.testLogin(address, password) }
                    if (ok) {
                        MailService.stop(ctx)
                        Secrets.put("mailAddress", address); Secrets.put("mailPassword", password); password = ""
                        Store.updateMail { it.copy(authRejected = false) }
                        MailService.start(ctx); hasMail = true
                        mailMsg = "Connected. New mail will arrive instantly."
                    } else mailMsg = "Gmail didn't accept that address or app password."
                    mailBusy = false
                }
            }) { Text(if (mailBusy) "Checking…" else "Save") }
            OutlinedButton(onClick = { openExternally(ctx, "https://myaccount.google.com/apppasswords") }) { Text("Get app password") }
        }
        if (hasMail) TextButton(onClick = {
            MailService.stop(ctx); Secrets.put("mailAddress", null); Secrets.put("mailPassword", null)
            Store.updateMail { app.framy.MailState() }; address = ""; hasMail = false; mailMsg = null
        }) { Text("Remove Gmail") }

        if (mail.hidden.isNotEmpty()) {
            Section("Hidden senders")
            Text("Tap one to show their mail again.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            mail.hidden.sorted().forEach { a ->
                Text(a, Modifier.fillMaxWidth().clickable { Store.updateMail { it.copy(hidden = it.hidden - a) } }.padding(vertical = 12.dp), fontSize = 16.sp)
            }
        }

        Section("Appearance")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (Build.VERSION.SDK_INT >= 31) FilterChip(Prefs.themeSeed == "wallpaper", { Prefs.themeSeed = "wallpaper" }, label = { Text("Wallpaper") })
            SWATCHES.forEach { (name, argb) ->
                val selected = Prefs.themeSeed == name
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(argb))
                    .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable { Prefs.themeSeed = name }, contentAlignment = Alignment.Center) {
                    if (selected) Icon(painterResource(R.drawable.ic_check), contentDescription = "$name selected", Modifier.size(18.dp), tint = Color.White)
                }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEachIndexed { i, (key, label) ->
                SegmentedButton(Prefs.mode == key, { Prefs.mode = key }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
            }
        }

        Section("Keep mail instant")
        val ignoring = remember(mail.live) { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }
        Text(if (ignoring) "Done. Android won't pause Framy's mail connection." else "Android may pause the mail connection while your phone sleeps. Allow Framy to run in the background so mail stays instant.",
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!ignoring) OutlinedButton(onClick = {
            try {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }) { Text("Allow background") }

        Section("Privacy")
        Text("Framy only reads. Your Canvas token and Gmail app password are encrypted on this phone and are sent only to Canvas and Gmail. The Global chat is the one place that sends anything, and only what you post.",
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Framy ${BuildConfig.VERSION_NAME} · GPL-3.0", Modifier.clickable { taps++ }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (taps >= 7 || Secrets.get("adminKey") != null) {
            OutlinedTextField(adminKey, { adminKey = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(),
                label = { Text(if (Secrets.get("adminKey") != null) "Developer key (saved)" else "Developer key") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = adminKey.isNotBlank(), onClick = { Secrets.put("adminKey", adminKey); adminKey = "" }) { Text("Save key") }
                TextButton(onClick = { Secrets.put("adminKey", null); taps = 0 }) { Text("Remove key") }
            }
        }
    }
}
