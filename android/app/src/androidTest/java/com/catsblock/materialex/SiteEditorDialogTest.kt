package com.catsblock.materialex

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.catsblock.materialex.engine.SiteStyleBridge
import com.catsblock.materialex.ui.SiteEditorDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SiteEditorDialogTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun string(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    private fun show(host: String, onDismiss: () -> Unit): SiteStyleBridge {
        val bridge = SiteStyleBridge(rule.activity.applicationContext, scope)
        rule.setContent {
            MaterialTheme {
                SiteEditorDialog(host = host, bridge = bridge, isPrivate = false, onDismiss = onDismiss)
            }
        }
        return bridge
    }

    @Test
    fun unbalancedBracesAreReportedAndNothingIsSaved() {
        var dismissed = false
        show("unbalanced.test") { dismissed = true }
        rule.onNodeWithText(string(R.string.site_editor_hint, "unbalanced.test")).assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performTextInput("body { color: red;")
        rule.onNodeWithText(string(R.string.action_save)).performClick()
        rule.onNodeWithText(string(R.string.site_editor_error_braces)).assertIsDisplayed()
        assertFalse(dismissed)
    }

    @Test
    fun validCssSavesAndClosesTheEditor() {
        var dismissed = false
        val bridge = show("valid.test") { dismissed = true }
        rule.onNode(hasSetTextAction()).performTextInput("body { color: red; }")
        rule.onNodeWithText(string(R.string.action_save)).performClick()
        rule.waitUntil(5_000) { dismissed }
        assertTrue(bridge.styles.value["valid.test"] == "body { color: red; }")
    }

    @Test
    fun importRulesAreRejected() {
        show("import.test") {}
        rule.onNode(hasSetTextAction()).performTextInput("@import url(x.css);")
        rule.onNodeWithText(string(R.string.site_editor_preview)).performClick()
        rule.onNodeWithText(string(R.string.site_editor_error_import)).assertIsDisplayed()
    }
}
