package app.framy.ui

import androidx.compose.runtime.Composable

@Composable
fun SettingsScreen(onBack: () -> Unit) = DetailScaffold("Settings", onBack) { EmptyState("Settings arrive in a later step.") }
