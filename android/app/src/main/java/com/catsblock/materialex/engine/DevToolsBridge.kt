package com.catsblock.materialex.engine

import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

data class ConsoleEntry(val level: String, val text: String, val time: Long)

class DevToolsBridge {
    private var port: WebExtension.Port? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private val logFlow = MutableStateFlow<Map<String, List<ConsoleEntry>>>(emptyMap())
    private val mainHandler = Handler(Looper.getMainLooper())

    val logs: StateFlow<Map<String, List<ConsoleEntry>>> = logFlow.asStateFlow()

    private val portDelegate = object : WebExtension.PortDelegate {
        override fun onPortMessage(message: Any, port: WebExtension.Port) {
            val json = message as? JSONObject ?: return
            when (json.optString("type")) {
                "reply" -> pending.remove(json.optString("id"))?.complete(json)
                "log" -> {
                    val entry = json.optJSONObject("entry") ?: return
                    val href = json.optString("href")
                    val item = ConsoleEntry(entry.optString("level", "log"), entry.optString("text"), entry.optLong("time"))
                    logFlow.update { current ->
                        val list = (current[href].orEmpty() + item).takeLast(MAX_LOGS)
                        current + (href to list)
                    }
                }
            }
        }

        override fun onDisconnect(port: WebExtension.Port) {
            if (this@DevToolsBridge.port === port) this@DevToolsBridge.port = null
        }
    }

    private val messages = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            this@DevToolsBridge.port = port
            port.setDelegate(portDelegate)
        }
    }

    fun install(runtime: GeckoRuntime) {
        runtime.webExtensionController.ensureBuiltIn(ASSET_URI, EXTENSION_ID).accept(
            { extension -> mainHandler.post { extension?.setMessageDelegate(messages, APP) } },
            { }
        )
    }

    fun clearLogs(href: String) {
        logFlow.update { it - href }
    }

    suspend fun request(href: String, op: String, args: JSONObject = JSONObject()): Result<JSONObject> {
        val active = port ?: return Result.failure(IllegalStateException("page-not-ready"))
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred
        mainHandler.post {
            runCatching {
                active.postMessage(JSONObject().put("type", "cmd").put("id", id).put("href", href).put("op", op).put("args", args))
            }
        }
        val reply = withTimeoutOrNull(TIMEOUT_MS) { deferred.await() }
        pending.remove(id)
        if (reply == null) return Result.failure(IllegalStateException("timeout"))
        if (!reply.optBoolean("ok")) return Result.failure(IllegalStateException(reply.optString("error", "error")))
        return Result.success(reply.optJSONObject("result") ?: JSONObject())
    }

    companion object {
        const val EXTENSION_ID = "devtools@materialex.catsblock.com"
        private const val APP = "materialexDevtools"
        private const val ASSET_URI = "resource://android/assets/devtools/"
        private const val MAX_LOGS = 500
        private const val TIMEOUT_MS = 8_000L
    }
}
