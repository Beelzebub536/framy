package app.framy

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.framy.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fresh install, no credentials, no chat URL: every screen must open without crashing. */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun pressBack() = rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

    @Test fun launchAndOpenEveryScreen() {
        rule.onAllNodesWithText("Canvas")[0].assertExists()
        rule.onNodeWithText("Courses").performClick()
        rule.onNodeWithText("Updates").performClick()
        rule.onNodeWithText("Mail").performClick()
        rule.onNodeWithContentDescription("Global chat").performClick()
        rule.onNodeWithText("Global chat").assertExists()
        pressBack()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Settings").assertExists()
        pressBack()
        rule.onAllNodesWithText("Mail")[0].assertExists()
    }
}
