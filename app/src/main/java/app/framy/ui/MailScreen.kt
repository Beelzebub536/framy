package app.framy.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.framy.MailItem
import app.framy.MailState

@Composable
fun MailScreen(state: MailState, onOpen: (MailItem) -> Unit, onTab: (String) -> Unit, onChat: () -> Unit, onSettings: () -> Unit) {
    Scaffold(topBar = { TabTopBar("Mail", onChat, onSettings) }, bottomBar = { BottomTabs("tab:mail", 0, onTab) }) { pad ->
        androidx.compose.foundation.layout.Box(Modifier.padding(pad)) { EmptyState("Mail arrives in a later step.") }
    }
}

@Composable
fun MailDetail(item: MailItem, onBack: () -> Unit) = DetailScaffold(item.subject, onBack) { EmptyState(item.snippet) }
