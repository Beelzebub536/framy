package app.framy.ui

import androidx.compose.runtime.Composable

@Composable
fun ChatScreen(onBack: () -> Unit) = DetailScaffold("Global chat", onBack) { EmptyState("Global chat is unavailable right now") }
