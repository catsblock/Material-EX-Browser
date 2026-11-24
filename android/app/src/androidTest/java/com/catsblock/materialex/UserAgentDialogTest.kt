package com.catsblock.materialex

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.catsblock.materialex.core.siteedit.UaChoice
import com.catsblock.materialex.core.siteedit.UaMode
import com.catsblock.materialex.ui.UserAgentDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserAgentDialogTest {
    @get:Rule
    val rule = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun string(id: Int, vararg args: Any) =
        rule.activity.getString(id, *args)

    @Test
    fun choosingDesktopAppliesTheDesktopMode() {
        var applied: UaChoice? = null
        rule.setContent {
            MaterialTheme {
                UserAgentDialog(host = "example.com", current = UaChoice(UaMode.DEFAULT), onApply = { applied = it }, onDismiss = {})
            }
        }
        rule.onNodeWithText(string(R.string.user_agent_desktop)).performClick()
        rule.onNodeWithText(string(R.string.action_save)).performClick()
        assertEquals(UaChoice(UaMode.DESKTOP), applied)
    }

    @Test
    fun anInvalidCustomValueShowsAnErrorAndIsNotApplied() {
        var applied: UaChoice? = null
        rule.setContent {
            MaterialTheme {
                UserAgentDialog(host = "example.com", current = UaChoice(UaMode.DEFAULT), onApply = { applied = it }, onDismiss = {})
            }
        }
        rule.onNodeWithText(string(R.string.user_agent_custom)).performClick()
        rule.onNodeWithText(string(R.string.action_save)).performClick()
        rule.onNodeWithText(string(R.string.user_agent_error)).assertIsDisplayed()
        assertNull(applied)
    }

    @Test
    fun aValidCustomValueIsApplied() {
        var applied: UaChoice? = null
        rule.setContent {
            MaterialTheme {
                UserAgentDialog(host = "example.com", current = UaChoice(UaMode.DEFAULT), onApply = { applied = it }, onDismiss = {})
            }
        }
        rule.onNodeWithText(string(R.string.user_agent_custom)).performClick()
        rule.onNodeWithText(string(R.string.user_agent_custom_label)).performTextInput("TestAgent/1.0")
        rule.onNodeWithText(string(R.string.action_save)).performClick()
        assertEquals(UaChoice(UaMode.CUSTOM, "TestAgent/1.0"), applied)
    }
}
