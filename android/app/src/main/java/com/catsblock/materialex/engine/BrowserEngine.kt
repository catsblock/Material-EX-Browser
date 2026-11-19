package com.catsblock.materialex.engine

import android.content.Context
import com.catsblock.materialex.core.ScrollChromeTracker
import com.catsblock.materialex.core.permission.PermissionBroker
import com.catsblock.materialex.core.permission.PermissionManager
import com.catsblock.materialex.core.permission.PermissionType
import com.catsblock.materialex.core.popup.PopupPolicy
import com.catsblock.materialex.core.popup.PopupWindow
import com.catsblock.materialex.core.tabs.TabManager
import com.catsblock.materialex.core.tabs.TabsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.MediaSession
import org.mozilla.geckoview.WebResponse

data class PageState(
    val url: String = "",
    val title: String = "",
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val isSecure: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val desktopMode: Boolean = false,
    val audible: Boolean = false
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
    private val trackers = HashMap<String, ScrollChromeTracker>()
    private val chromeFlow = MutableStateFlow(true)
    private val atTopFlow = MutableStateFlow(true)
    private val scrollTops = HashMap<String, Int>()
    private val popupFlow = MutableStateFlow<List<PopupWindow>>(emptyList())
    private val popupSessions = HashMap<String, GeckoSession>()
    private var lastGestureAt = 0L

    val permissions = PermissionBroker(permissionManager, DEFAULT_PROFILE)
    val osBlocked: StateFlow<Boolean> = osBlockedFlow.asStateFlow()
    val chromeVisible: StateFlow<Boolean> = chromeFlow.asStateFlow()
    val atTop: StateFlow<Boolean> = atTopFlow.asStateFlow()
    val popups: StateFlow<List<PopupWindow>> = popupFlow.asStateFlow()
    var popupBlocking: Boolean = true
    var androidPermissionRequester: ((Array<String>, (Boolean) -> Unit) -> Unit)? = null
    var onDownload: ((WebResponse) -> Unit)? = null
    var onPageVisited: ((url: String, title: String) -> Unit)? = null

    val tabs: StateFlow<TabsState> = manager.state
    val pageStates: StateFlow<Map<String, PageState>> = pages.asStateFlow()
    val activeSession: StateFlow<GeckoSession?> = active.asStateFlow()
    val state: StateFlow<PageState> = combine(manager.state, pages) { tabsState, map ->
        tabsState.activeId?.let { map[it] } ?: PageState()
    }.stateIn(scope, SharingStarted.Eagerly, PageState())

    fun openTab(url: String, select: Boolean = true): String {
        val tab = manager.open(url, DEFAULT_PROFILE, false, select)
        launch(tab.id, url)
        return tab.id
    }

    private fun launch(id: String, url: String) {
        val session = GeckoSession()
        attach(id, session)
        session.open(runtime)
        sessions[id] = session
        trackers[id] = ScrollChromeTracker()
        pages.update { it + (id to PageState(url = url)) }
        syncActive()
        if (url.isNotEmpty()) session.loadUri(url)
    }

    fun duplicateTab(id: String) {
        val url = pages.value[id]?.url ?: return
        val tab = manager.duplicate(id) ?: return
        launch(tab.id, url)
    }

    fun restoreClosedTab() {
        val tab = manager.restoreLastClosed() ?: return
        launch(tab.id, tab.url)
    }

    fun closeOthers(id: String) {
        manager.closeOthers(id)
        val keep = manager.state.value.tabs.map { it.id }.toSet()
        sessions.keys.filter { it !in keep }.forEach(::dropSession)
        syncActive()
    }

    fun closeTabsToRight(id: String) {
        val tabs = manager.state.value.tabs
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return
        tabs.drop(index + 1).forEach { closeTab(it.id) }
    }

    fun moveTab(id: String, toIndex: Int) = manager.move(id, toIndex)

    private fun dropSession(id: String) {
        closePopupsFor(id)
        sessions.remove(id)?.let(pendingClose::add)
        pages.update { it - id }
        lastVisit.remove(id)
        trackers.remove(id)
        scrollTops.remove(id)
    }

    fun selectTab(id: String) {
        manager.select(id)
        syncActive()
    }

    fun closeTab(id: String) {
        manager.close(id)
        dropSession(id)
        syncActive()
    }

    fun noteUserGesture() {
        lastGestureAt = System.currentTimeMillis()
    }

    fun popupSession(id: String): GeckoSession? = popupSessions[id]

    fun popupGoBack(id: String) {
        popupSessions[id]?.goBack()
    }

    fun minimizePopup(id: String) = setPopupMinimized(id, true)

    fun restorePopup(id: String) {
        val opener = popupFlow.value.firstOrNull { it.id == id }?.openerId ?: return
        popupFlow.update { list ->
            list.map {
                when {
                    it.id == id -> it.copy(minimized = false)
                    it.openerId == opener -> it.copy(minimized = true)
                    else -> it
                }
            }
        }
    }

    private fun setPopupMinimized(id: String, minimized: Boolean) {
        popupFlow.update { list -> list.map { if (it.id == id) it.copy(minimized = minimized) else it } }
    }

    fun closePopup(id: String) {
        val session = popupSessions.remove(id) ?: return
        popupFlow.update { list -> list.filterNot { it.id == id } }
        pages.update { it - id }
        trackers.remove(id)
        scrollTops.remove(id)
        lastVisit.remove(id)
        pendingClose.add(session)
        scope.launch {
            delay(POPUP_RELEASE_DELAY_MS)
            flushClosed()
        }
    }

    private fun closePopupsFor(openerId: String) {
        popupFlow.value.filter { it.openerId == openerId }.forEach { closePopup(it.id) }
    }

    private fun createPopup(openerId: String): GeckoSession? {
        val open = popupFlow.value.count { it.openerId == openerId }
        if (!PopupPolicy.allow(popupBlocking, open, lastGestureAt, System.currentTimeMillis())) return null
        val id = "popup-" + java.util.UUID.randomUUID()
        val session = GeckoSession()
        popupSessions[id] = session
        trackers[id] = ScrollChromeTracker()
        pages.update { it + (id to PageState()) }
        attach(id, session)
        popupFlow.update { list ->
            list.map { if (it.openerId == openerId) it.copy(minimized = true) else it } + PopupWindow(id, openerId)
        }
        return session
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

    fun setDesktopMode(enabled: Boolean) {
        val id = manager.state.value.activeId ?: return
        val session = sessions[id] ?: return
        session.settings.userAgentMode =
            if (enabled) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        session.settings.viewportMode =
            if (enabled) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        change(id) { it.copy(desktopMode = enabled) }
        session.reload()
    }

    fun find(query: String, forward: Boolean, onResult: (Int) -> Unit) {
        val session = active.value ?: return
        if (query.isEmpty()) {
            session.finder.clear()
            onResult(0)
            return
        }
        val flags = if (forward) 0 else GeckoSession.FINDER_FIND_BACKWARDS
        session.finder.find(query, flags).accept { result -> onResult(result?.total ?: 0) }
    }

    fun clearFind() {
        active.value?.finder?.clear()
    }

    private fun syncActive() {
        active.value = manager.state.value.activeId?.let { sessions[it] }
        chromeFlow.value = true
        atTopFlow.value = manager.state.value.activeId?.let { (scrollTops[it] ?: 0) <= 0 } ?: true
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

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                val opener = popupFlow.value.firstOrNull { it.id == id }?.openerId ?: id
                return GeckoResult.fromValue(createPopup(opener))
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
                trackers[id]?.reset()
                scrollTops[id] = 0
                if (manager.state.value.activeId == id) atTopFlow.value = true
                if (manager.state.value.activeId == id) chromeFlow.value = true
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
        session.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                scrollTops[id] = scrollY
                if (manager.state.value.activeId == id) atTopFlow.value = scrollY <= 0
                val tracker = trackers[id] ?: return
                val visible = tracker.onScroll(scrollY)
                if (manager.state.value.activeId == id && chromeFlow.value != visible) chromeFlow.value = visible
            }
        }
        session.mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
                change(id) { it.copy(audible = true) }
            }

            override fun onPause(session: GeckoSession, mediaSession: MediaSession) {
                change(id) { it.copy(audible = false) }
            }

            override fun onStop(session: GeckoSession, mediaSession: MediaSession) {
                change(id) { it.copy(audible = false) }
            }

            override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
                change(id) { it.copy(audible = false) }
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
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                onDownload?.invoke(response)
            }

            override fun onCloseRequest(session: GeckoSession) {
                if (popupSessions.containsKey(id)) closePopup(id)
            }

            override fun onTitleChange(session: GeckoSession, title: String?) {
                change(id) { it.copy(title = title.orEmpty()) }
                manager.update(id, title = title.orEmpty())
            }
        }
    }

    private companion object {
        const val DEFAULT_PROFILE = "default"
        const val POPUP_RELEASE_DELAY_MS = 450L
    }
}
