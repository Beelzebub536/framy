package app.framy.ui

import androidx.compose.runtime.Composable
import app.framy.Course

@Composable
fun CourseScreen(course: Course, onBack: () -> Unit) = DetailScaffold(course.code, onBack) { EmptyState("Modules and files arrive in the next step.") }
