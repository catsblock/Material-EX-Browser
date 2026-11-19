package com.catsblock.materialex.engine

import android.content.Context
import com.catsblock.materialex.core.permission.PermissionBroker
import com.catsblock.materialex.core.permission.PermissionManager
import com.catsblock.materialex.core.permission.PermissionType
import com.catsblock.materialex.core.tabs.TabManager
import com.catsblock.materialex.core.tabs.TabsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

data class PageState(
    val url: String = "",
    val title: String = "",
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val isSecure: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
)

class BrowserEngine(context: Context, permissionManager: PermissionManager) {
    private val runtime: GeckoRuntime = GeckoRuntime.create(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager = TabManager()
    private val sessions = HashMap<String, GeckoSession>()
    private val pendingClose = ArrayList<GeckoSession>()
    private val pages = MutableStateFlow<Map<String, PageState>>(emptyMap())
    private val active = MutableStateFlow<GeckoSession?>(null)
    private val lastVisit = HashMap<String, String>()

    private val osBlockedFlow = MutableStateFlow(false)

    val permissions = PermissionBroker(permissionManager, DEFAULT_PROFILE)
    val osBlocked: StateFlow<Boolean> = osBlockedFlow.asStateFlow()
    var androidPermissionRequester: ((Array<String>, (Boolean) -> Unit) -> Unit)? = null
    var onPageVisited: ((url: String, title: String) -> Unit)? = null

    val tabs: StateFlow<TabsState> = manager.state
    val pageStates: StateFlow<Map<String, PageState>> = pages.asStateFlow()
    val activeSession: StateFlow<GeckoSession?> = active.asStateFlow()
    val state: StateFlow<PageState> = combine(manager.state, pages) { tabsState, map ->
        tabsState.activeId?.let { map[it] } ?: PageState()
    }.stateIn(scope, SharingStarted.Eagerly, PageState())

    fun openTab(url: String, select: Boolean = true): String {
        val tab = manager.open(url, DEFAULT_PROFILE, false, select)
        val session = GeckoSession()
        attach(tab.id, session)
        session.open(runtime)
        sessions[tab.id] = session
        pages.update { it + (tab.id to PageState(url = url)) }
        syncActive()
        if (url.isNotEmpty()) session.loadUri(url)
        return tab.id
    }

    fun selectTab(id: String) {
        manager.select(id)
        syncActive()
    }

    fun closeTab(id: String) {
        manager.close(id)
        sessions.remove(id)?.let(pendingClose::add)
        pages.update { it - id }
        lastVisit.remove(id)
        syncActive()
    }

    fun clearOsBlocked() {
        osBlockedFlow.value = false
    }

    fun flushClosed() {
        pendingClose.forEach { it.close() }
        pendingClose.clear()
    }

    fun load(url: String) {
        val session = active.value
        if (session == null) openTab(url) else session.loadUri(url)
    }

    fun goBack() = active.value?.goBack()

    fun goForward() = active.value?.goForward()

    fun reload() = active.value?.reload()

    fun stop() = active.value?.stop()

    private fun syncActive() {
        active.value = manager.state.value.activeId?.let { sessions[it] }
    }

    private fun change(id: String, block: (PageState) -> PageState) {
        pages.update { map -> map[id]?.let { map + (id to block(it)) } ?: map }
    }

    private fun attach(id: String, session: GeckoSession) {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                change(id) { it.copy(url = url.orEmpty()) }
                manager.update(id, url = url.orEmpty())
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                change(id) { it.copy(canGoBack = canGoBack) }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                change(id) { it.copy(canGoForward = canGoForward) }
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                change(id) { it.copy(isLoading = true, progress = 0) }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                change(id) { it.copy(isLoading = false, progress = 100) }
                val page = pages.value[id]
                if (success && page != null && page.url.isNotEmpty() && lastVisit[id] != page.url) {
                    lastVisit[id] = page.url
                    onPageVisited?.invoke(page.url, page.title)
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                change(id) { it.copy(progress = progress) }
            }

            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: GeckoSession.ProgressDelegate.SecurityInformation
            ) {
                change(id) { it.copy(isSecure = securityInfo.isSecure) }
            }
        }
        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<out String>?,
                callback: GeckoSession.PermissionDelegate.Callback
            ) {
                val needed = permissions?.toList().orEmpty()
                val requester = androidPermissionRequester
                if (needed.isEmpty() || requester == null) {
                    callback.reject()
                    return
                }
                requester(needed.toTypedArray()) { granted ->
                    if (granted) {
                        callback.grant()
                    } else {
                        osBlockedFlow.value = true
                        callback.reject()
                    }
                }
            }

            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission
            ): GeckoResult<Int>? {
                val type = when (perm.permission) {
                    GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> PermissionType.LOCATION
                    GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> PermissionType.NOTIFICATIONS
                    GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> PermissionType.PERSISTENT_STORAGE
                    else -> null
                } ?: return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                val result = GeckoResult<Int>()
                scope.launch {
                    val allowed = permissions.resolve(perm.uri, type)
                    result.complete(
                        if (allowed) {
                            GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                        } else {
                            GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                        }
                    )
                }
                return result
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback
            ) {
                scope.launch {
                    val videoSource = video?.firstOrNull()
                    val audioSource = audio?.firstOrNull()
                    val videoAllowed = videoSource != null && permissions.resolve(uri, PermissionType.CAMERA)
                    val audioAllowed = audioSource != null && permissions.resolve(uri, PermissionType.MICROPHONE)
                    if (videoAllowed || audioAllowed) {
                        callback.grant(
                            if (videoAllowed) videoSource else null,
                            if (audioAllowed) audioSource else null
                        )
                    } else {
                        callback.reject()
                    }
                }
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                change(id) { it.copy(title = title.orEmpty()) }
                manager.update(id, title = title.orEmpty())
            }
        }
    }

    private companion object {
        const val DEFAULT_PROFILE = "default"
    }
}
