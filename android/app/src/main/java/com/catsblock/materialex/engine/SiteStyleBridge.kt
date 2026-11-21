package com.catsblock.materialex.engine

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.catsblock.materialex.core.siteedit.CssIssue
import com.catsblock.materialex.core.siteedit.SiteStyleRules
import com.catsblock.materialex.core.siteedit.UaChoice
import com.catsblock.materialex.core.siteedit.UserAgentRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

private val Context.siteStore by preferencesDataStore(name = "site_rules")

class SiteStyleBridge(context: Context, scope: CoroutineScope) {
    private val store = context.applicationContext.siteStore
    private val stylesKey = stringPreferencesKey("styles")
    private val agentsKey = stringPreferencesKey("user_agents")
    private val stylesFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    private val agentsFlow = MutableStateFlow<Map<String, UaChoice>>(emptyMap())
    private var contentPort: WebExtension.Port? = null

    val styles: StateFlow<Map<String, String>> = stylesFlow.asStateFlow()
    val userAgents: StateFlow<Map<String, UaChoice>> = agentsFlow.asStateFlow()

    init {
        scope.launch {
            store.data.collect { prefs ->
                stylesFlow.value = SiteStyleRules.decode(prefs[stylesKey])
                agentsFlow.value = UserAgentRules.decode(prefs[agentsKey])
            }
        }
    }

    private val messages = object : WebExtension.MessageDelegate {
        override fun onMessage(
            nativeApp: String,
            message: Any,
            sender: WebExtension.MessageSender
        ): GeckoResult<Any>? {
            if (nativeApp != APP || sender.webExtension.id != EXTENSION_ID) return null
            val json = message as? JSONObject ?: return null
            if (json.optString("type") != "get") return null
            val host = json.optString("host").lowercase()
            val css = if (host.length in 1..253) stylesFlow.value[host].orEmpty() else ""
            return GeckoResult.fromValue(JSONObject().put("css", css))
        }

        override fun onConnect(port: WebExtension.Port) {
            contentPort = port
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onDisconnect(port: WebExtension.Port) {
                    if (contentPort === port) contentPort = null
                }
            })
        }
    }

    fun install(runtime: GeckoRuntime) {
        runtime.webExtensionController.ensureBuiltIn(ASSET_URI, EXTENSION_ID).accept(
            { extension -> extension?.setMessageDelegate(messages, APP) },
            { }
        )
    }

    fun preview(host: String, css: String): CssIssue? {
        val issue = SiteStyleRules.validate(css)
        if (issue == null) push(host, css)
        return issue
    }

    suspend fun save(host: String, css: String): CssIssue? {
        val issue = SiteStyleRules.validate(css)
        if (issue != null) return issue
        store.edit { it[stylesKey] = SiteStyleRules.encode(SiteStyleRules.withStyle(SiteStyleRules.decode(it[stylesKey]), host, css)) }
        push(host, css)
        return null
    }

    suspend fun reset(host: String) {
        store.edit { it[stylesKey] = SiteStyleRules.encode(SiteStyleRules.withStyle(SiteStyleRules.decode(it[stylesKey]), host, "")) }
        push(host, "")
    }

    suspend fun setUserAgent(host: String, choice: UaChoice?) {
        store.edit { it[agentsKey] = UserAgentRules.encode(UserAgentRules.with(UserAgentRules.decode(it[agentsKey]), host, choice)) }
    }

    private fun push(host: String, css: String) {
        contentPort?.postMessage(JSONObject().put("type", "apply").put("host", host).put("css", css))
    }

    companion object {
        const val EXTENSION_ID = "site-styles@materialex.catsblock.com"
        private const val APP = "materialexSiteStyle"
        private const val ASSET_URI = "resource://android/assets/sitestyle/"
    }
}
