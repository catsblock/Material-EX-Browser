package com.catsblock.materialex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catsblock.materialex.R
import com.catsblock.materialex.engine.ConsoleEntry
import com.catsblock.materialex.engine.DevToolsBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private enum class DevTab(val label: Int) {
    ELEMENTS(R.string.devtools_tab_elements),
    CONSOLE(R.string.devtools_tab_console),
    SOURCES(R.string.devtools_tab_sources),
    NETWORK(R.string.devtools_tab_network),
    STORAGE(R.string.devtools_tab_storage),
    COOKIES(R.string.devtools_tab_cookies),
    CSS(R.string.devtools_tab_css)
}

private class DevSession(val bridge: DevToolsBridge, val href: String) {
    var error by mutableStateOf<String?>(null)

    suspend fun call(op: String, args: JSONObject = JSONObject()): JSONObject? =
        bridge.request(href, op, args).fold(
            onSuccess = {
                error = null
                it
            },
            onFailure = {
                error = it.message ?: "error"
                null
            }
        )
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

private fun pathOf(array: JSONArray): List<Int> = (0 until array.length()).map { array.optInt(it) }

private fun pathJson(path: List<Int>) = JSONArray(path)

private val mono = FontFamily.Monospace

@Composable
fun DevToolsPanel(
    bridge: DevToolsBridge,
    href: String,
    isPrivate: Boolean,
    onOpenSiteCss: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val session = remember(bridge, href) { DevSession(bridge, href) }
    var tab by remember { mutableStateOf(DevTab.ELEMENTS) }
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 8.dp, modifier = Modifier.weight(1f)) {
                    DevTab.entries.forEach { entry ->
                        Tab(selected = tab == entry, onClick = { tab = entry }, text = { Text(stringResource(entry.label)) })
                    }
                }
                TextButton(onClick = onClose) { Text(stringResource(R.string.onboarding_close)) }
            }
            if (isPrivate) {
                Text(
                    stringResource(R.string.devtools_private),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
                return@Column
            }
            session.error?.let { code ->
                Text(
                    if (code == "page-not-ready") stringResource(R.string.devtools_not_ready) else stringResource(R.string.devtools_error, code),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                when (tab) {
                    DevTab.ELEMENTS -> ElementsTab(session)
                    DevTab.CONSOLE -> ConsoleTab(session)
                    DevTab.SOURCES -> SourcesTab(session)
                    DevTab.NETWORK -> NetworkTab(session)
                    DevTab.STORAGE -> StorageTab(session)
                    DevTab.COOKIES -> CookiesTab(session)
                    DevTab.CSS -> {
                        Text(stringResource(R.string.devtools_css_hint), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                        Button(onClick = onOpenSiteCss) { Text(stringResource(R.string.action_site_editor)) }
                    }
                }
            }
        }
    }
}

private data class DomNode(val path: List<Int>, val depth: Int, val label: String, val children: Int, val text: String)

@Composable
private fun ElementsTab(session: DevSession) {
    val scope = rememberCoroutineScope()
    var nodes by remember { mutableStateOf(emptyList<DomNode>()) }
    var selected by remember { mutableStateOf<List<Int>?>(null) }
    var wholePage by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            session.call("dom.tree")?.optJSONArray("nodes")?.objects()?.let { list ->
                nodes = list.map { DomNode(pathOf(it.optJSONArray("path") ?: JSONArray()), it.optInt("depth"), it.optString("label"), it.optInt("children"), it.optString("text")) }
            }
        }
    }

    LaunchedEffect(session.href) { refresh() }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        OutlinedButton(onClick = { refresh() }) { Text(stringResource(R.string.devtools_refresh)) }
        OutlinedButton(onClick = {
            wholePage = true
            selected = null
            scope.launch { session.call("html.get")?.let { editor = it.optString("html") } }
        }) { Text(stringResource(R.string.devtools_whole_page)) }
    }
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
        itemsIndexed(nodes) { _, node ->
            Text(
                node.label + if (node.text.isNotEmpty()) "  ${node.text}" else "",
                fontFamily = mono,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                color = if (selected == node.path) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        wholePage = false
                        selected = node.path
                        scope.launch { session.call("dom.getOuter", JSONObject().put("path", pathJson(node.path)))?.let { editor = it.optString("html") } }
                    }
                    .padding(start = (node.depth * 10).coerceAtMost(200).dp, top = 4.dp, bottom = 4.dp)
            )
        }
    }
    if (wholePage || selected != null) {
        OutlinedTextField(
            value = editor,
            onValueChange = { editor = it },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = mono),
            modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            Button(onClick = {
                scope.launch {
                    val path = selected
                    if (wholePage) session.call("html.set", JSONObject().put("html", editor))
                    else if (path != null) session.call("dom.setOuter", JSONObject().put("path", pathJson(path)).put("html", editor))
                    refresh()
                }
            }) { Text(stringResource(R.string.devtools_apply)) }
            selected?.let { path ->
                OutlinedButton(onClick = { scope.launch { session.call("dom.highlight", JSONObject().put("path", pathJson(path))) } }) {
                    Text(stringResource(R.string.devtools_highlight))
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        session.call("dom.remove", JSONObject().put("path", pathJson(path)))
                        selected = null
                        editor = ""
                        refresh()
                    }
                }) { Text(stringResource(R.string.devtools_remove)) }
            }
        }
    }
}

@Composable
private fun ConsoleTab(session: DevSession) {
    val scope = rememberCoroutineScope()
    val allLogs by session.bridge.logs.collectAsStateWithLifecycle()
    var local by remember(session.href) { mutableStateOf(emptyList<ConsoleEntry>()) }
    var code by remember { mutableStateOf("") }
    val entries = (allLogs[session.href].orEmpty() + local).sortedBy { it.time }
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) { if (entries.isNotEmpty()) listState.scrollToItem(entries.size - 1) }

    LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
        itemsIndexed(entries) { _, entry ->
            val color = when (entry.level) {
                "error" -> MaterialTheme.colorScheme.error
                "warn" -> MaterialTheme.colorScheme.tertiary
                "input" -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
            Text(
                (if (entry.level == "input") "> " else if (entry.level == "result") "< " else "") + entry.text,
                color = color,
                fontFamily = mono,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            )
        }
    }
    OutlinedTextField(
        value = code,
        onValueChange = { code = it },
        placeholder = { Text(stringResource(R.string.devtools_console_hint)) },
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = mono),
        modifier = Modifier.fillMaxWidth().heightIn(max = 110.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Button(enabled = code.isNotBlank(), onClick = {
            val source = code
            code = ""
            scope.launch {
                local = local + ConsoleEntry("input", source, System.currentTimeMillis())
                val reply = session.call("js.eval", JSONObject().put("code", source))
                if (reply != null) {
                    val failed = reply.has("error")
                    local = local + ConsoleEntry(if (failed) "error" else "result", reply.optString(if (failed) "error" else "result"), System.currentTimeMillis())
                }
            }
        }) { Text(stringResource(R.string.devtools_run)) }
        OutlinedButton(onClick = {
            session.bridge.clearLogs(session.href)
            local = emptyList()
        }) { Text(stringResource(R.string.devtools_clear)) }
    }
}

private data class SourceItem(val kind: String, val url: String, val index: Int, val size: Int)

@Composable
private fun SourcesTab(session: DevSession) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(emptyList<SourceItem>()) }
    var viewer by remember { mutableStateOf("") }
    var truncated by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            session.call("sources.list")?.optJSONArray("items")?.objects()?.let { list ->
                items = list.map { SourceItem(it.optString("kind"), it.optString("url"), it.optInt("index"), it.optInt("size")) }
            }
        }
    }
    LaunchedEffect(session.href) { refresh() }

    OutlinedButton(onClick = { refresh() }, modifier = Modifier.padding(vertical = 4.dp)) { Text(stringResource(R.string.devtools_refresh)) }
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
        itemsIndexed(items) { _, item ->
            val kind = stringResource(
                when (item.kind) {
                    "script" -> R.string.devtools_src_script
                    "style" -> R.string.devtools_src_style
                    else -> R.string.devtools_src_document
                }
            )
            Text(
                kind + " " + item.url.ifEmpty { stringResource(R.string.devtools_src_inline) },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = mono,
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            truncated = false
                            val reply = when {
                                item.kind == "document" -> session.call("html.get")
                                item.url.isNotEmpty() -> session.call("source.fetch", JSONObject().put("url", item.url))
                                else -> session.call("sources.inline", JSONObject().put("kind", item.kind).put("index", item.index))
                            }
                            reply?.let {
                                viewer = it.optString("text", it.optString("html")).take(200_000)
                                truncated = it.optBoolean("truncated")
                            }
                        }
                    }
                    .padding(vertical = 6.dp)
            )
        }
    }
    if (viewer.isNotEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            Text(viewer, fontFamily = mono, style = MaterialTheme.typography.bodySmall, softWrap = false)
            if (truncated) Text("…", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NetworkTab(session: DevSession) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf(emptyList<JSONObject>()) }
    fun refresh() {
        scope.launch { session.call("network.list")?.optJSONArray("requests")?.objects()?.let { rows = it.reversed() } }
    }
    LaunchedEffect(session.href) { refresh() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        OutlinedButton(onClick = { refresh() }) { Text(stringResource(R.string.devtools_refresh)) }
        OutlinedButton(onClick = {
            scope.launch {
                session.call("network.clear")
                rows = emptyList()
            }
        }) { Text(stringResource(R.string.devtools_clear)) }
    }
    if (rows.isEmpty()) Text(stringResource(R.string.devtools_empty), style = MaterialTheme.typography.bodyMedium)
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
        itemsIndexed(rows) { _, r ->
            val status = r.optInt("status")
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    "${r.optString("method")}  ${if (status > 0) status.toString() else "—"}  ${r.optString("type")}  ${r.optInt("ms")} ms",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (status < 0 || status >= 400) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(r.optString("url"), style = MaterialTheme.typography.bodySmall, fontFamily = mono, maxLines = 2)
            }
        }
    }
}

@Composable
private fun StorageTab(session: DevSession) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf("local") }
    var entries by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    fun args() = JSONObject().put("kind", kind)
    fun refresh() {
        scope.launch {
            session.call("storage.get", args())?.optJSONArray("entries")?.objects()?.let { list ->
                entries = list.map { it.optString("key") to it.optString("value") }
            }
        }
    }
    LaunchedEffect(session.href, kind) { refresh() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        (if (kind == "local") Button(onClick = {}) { Text(stringResource(R.string.devtools_local)) } else OutlinedButton(onClick = { kind = "local" }) { Text(stringResource(R.string.devtools_local)) })
        (if (kind == "session") Button(onClick = {}) { Text(stringResource(R.string.devtools_session)) } else OutlinedButton(onClick = { kind = "session" }) { Text(stringResource(R.string.devtools_session)) })
        OutlinedButton(onClick = { refresh() }) { Text(stringResource(R.string.devtools_refresh)) }
    }
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
        itemsIndexed(entries) { _, (k, v) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        key = k
                        value = v
                    }
                    .padding(vertical = 4.dp)
            ) {
                Text(k, style = MaterialTheme.typography.labelLarge, fontFamily = mono)
                Text(v, style = MaterialTheme.typography.bodySmall, fontFamily = mono, maxLines = 2)
            }
        }
    }
    OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text(stringResource(R.string.devtools_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(stringResource(R.string.devtools_value)) }, modifier = Modifier.fillMaxWidth().heightIn(max = 90.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Button(enabled = key.isNotEmpty(), onClick = {
            scope.launch {
                session.call("storage.set", args().put("key", key).put("value", value))
                refresh()
            }
        }) { Text(stringResource(R.string.action_save)) }
        OutlinedButton(enabled = key.isNotEmpty(), onClick = {
            scope.launch {
                session.call("storage.remove", args().put("key", key))
                key = ""
                value = ""
                refresh()
            }
        }) { Text(stringResource(R.string.devtools_remove)) }
        OutlinedButton(onClick = {
            scope.launch {
                session.call("storage.clear", args())
                refresh()
            }
        }) { Text(stringResource(R.string.devtools_clear)) }
    }
}

private data class CookieForm(
    val name: String = "",
    val value: String = "",
    val domain: String = "",
    val path: String = "/",
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val hostOnly: Boolean = true,
    val expiry: Double = 0.0
)

@Composable
private fun CookiesTab(session: DevSession) {
    val scope = rememberCoroutineScope()
    var cookies by remember { mutableStateOf(emptyList<CookieForm>()) }
    var form by remember { mutableStateOf(CookieForm()) }
    fun refresh() {
        scope.launch {
            session.call("cookies.list", JSONObject().put("url", session.href))?.optJSONArray("cookies")?.objects()?.let { list ->
                cookies = list.map {
                    CookieForm(
                        it.optString("name"), it.optString("value"), it.optString("domain"), it.optString("path", "/"),
                        it.optBoolean("secure"), it.optBoolean("httpOnly"), it.optBoolean("hostOnly"), it.optDouble("expirationDate", 0.0)
                    )
                }
            }
        }
    }
    LaunchedEffect(session.href) { refresh() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        OutlinedButton(onClick = { refresh() }) { Text(stringResource(R.string.devtools_refresh)) }
        OutlinedButton(onClick = { form = CookieForm() }) { Text(stringResource(R.string.devtools_new)) }
    }
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
        itemsIndexed(cookies) { _, c ->
            Column(Modifier.fillMaxWidth().clickable { form = c }.padding(vertical = 4.dp)) {
                Text(c.name, style = MaterialTheme.typography.labelLarge, fontFamily = mono)
                Text(c.value, style = MaterialTheme.typography.bodySmall, fontFamily = mono, maxLines = 1)
            }
        }
    }
    Column(Modifier.heightIn(max = 230.dp).verticalScroll(rememberScrollState())) {
        OutlinedTextField(value = form.name, onValueChange = { form = form.copy(name = it) }, label = { Text(stringResource(R.string.devtools_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = form.value, onValueChange = { form = form.copy(value = it) }, label = { Text(stringResource(R.string.devtools_value)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = form.domain, onValueChange = { form = form.copy(domain = it, hostOnly = false) }, label = { Text(stringResource(R.string.devtools_domain)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = form.path, onValueChange = { form = form.copy(path = it) }, label = { Text(stringResource(R.string.devtools_path)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = form.secure, onCheckedChange = { form = form.copy(secure = it) })
            Text(stringResource(R.string.devtools_secure))
            Checkbox(checked = form.httpOnly, onCheckedChange = { form = form.copy(httpOnly = it) })
            Text(stringResource(R.string.devtools_http_only))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Button(enabled = form.name.isNotEmpty(), onClick = {
            scope.launch {
                val args = JSONObject().put("url", session.href).put("name", form.name).put("value", form.value)
                    .put("path", form.path).put("secure", form.secure).put("httpOnly", form.httpOnly)
                if (!form.hostOnly && form.domain.isNotEmpty()) args.put("domain", form.domain)
                if (form.expiry > 0) args.put("expirationDate", form.expiry)
                session.call("cookies.set", args)
                refresh()
            }
        }) { Text(stringResource(R.string.action_save)) }
        OutlinedButton(enabled = form.name.isNotEmpty(), onClick = {
            scope.launch {
                session.call("cookies.remove", JSONObject().put("url", session.href).put("name", form.name))
                form = CookieForm()
                refresh()
            }
        }) { Text(stringResource(R.string.devtools_remove)) }
    }
}
