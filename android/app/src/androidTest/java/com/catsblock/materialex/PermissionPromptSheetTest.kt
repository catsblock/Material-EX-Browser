package com.catsblock.materialex

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.catsblock.materialex.core.permission.PermissionPrompt
import com.catsblock.materialex.core.permission.PermissionType
import com.catsblock.materialex.core.permission.PromptChoice
import com.catsblock.materialex.ui.PermissionPromptSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionPromptSheetTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int) = rule.activity.getString(id)

    private fun show(type: PermissionType, onChoice: (PromptChoice) -> Unit) {
        rule.setContent {
            MaterialTheme {
                PermissionPromptSheet(PermissionPrompt(1L, "https://example.com", type), onChoice)
            }
        }
    }

    @Test
    fun denyReportsADenial() {
        var choice: PromptChoice? = null
        show(PermissionType.CAMERA) { choice = it }
        rule.onNodeWithText(string(R.string.perm_deny)).performClick()
        assertEquals(PromptChoice.DENY, choice)
    }

    @Test
    fun allowOnceIsOfferedOnlyForTypesThatSupportIt() {
        var choice: PromptChoice? = null
        show(PermissionType.CAMERA) { choice = it }
        rule.onNodeWithText(string(R.string.perm_allow_once)).performClick()
        assertEquals(PromptChoice.ALLOW_ONCE, choice)
        assertTrue(PermissionType.CAMERA.supportsAllowOnce)
    }

    @Test
    fun alwaysAskIsADismissalNotADenial() {
        var choice: PromptChoice? = null
        show(PermissionType.MICROPHONE) { choice = it }
        rule.onNodeWithText(string(R.string.perm_always_ask)).performClick()
        assertEquals(PromptChoice.ALWAYS_ASK, choice)
    }
}
