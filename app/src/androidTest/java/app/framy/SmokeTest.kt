package app.framy

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import app.framy.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Fresh install, no credentials, no chat URL: every screen must open without crashing. */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    // A real user taps "Allow"; the system dialog would otherwise cover the app. order=0 runs before the activity launches.
    @get:Rule(order = 0) val notifications: GrantPermissionRule =
        if (android.os.Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS) else GrantPermissionRule.grant()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private fun pressBack() = rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

    @Test fun launchAndOpenEveryScreen() {
        rule.onNodeWithText("Settings").assertExists() // fresh install opens on Settings
        rule.onNodeWithText("Save token").assertExists()
        pressBack()
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
