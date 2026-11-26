package com.catsblock.materialex.engine

import android.content.Context
import com.catsblock.materialex.core.ScrollChromeTracker
import com.catsblock.materialex.core.memory.MemoryBudget
import com.catsblock.materialex.core.extension.ExtensionInfo
import com.catsblock.materialex.core.extension.ExtensionUpdateStatus
import com.catsblock.materialex.core.extension.ExtensionPrompt
import com.catsblock.materialex.core.permission.PermissionBroker
import com.catsblock.materialex.core.TextScale
import com.catsblock.materialex.core.permission.ActivePermissionUse
import com.catsblock.materialex.core.permission.EPHEMERAL_PERMISSION_PROFILE
import com.catsblock.materialex.core.permission.Revocation
import com.catsblock.materialex.core.profile.ProfileRules
import com.catsblock.materialex.core.siteedit.SiteStyleRules
import com.catsblock.materialex.core.siteedit.UaChoice
import com.catsblock.materialex.core.siteedit.UaMode
import com.catsblock.materialex.core.security.BypassGate
import com.catsblock.materialex.core.security.ThreatInfo
import com.catsblock.materialex.core.security.ThreatKind
import com.catsblock.materialex.core.permission.PermissionManager
import com.catsblock.materialex.core.permission.Origin
import com.catsblock.materialex.core.permission.PermissionType
import com.catsblock.materialex.core.popup.PopupPolicy
import com.catsblock.materialex.core.popup.PopupWindow
import com.catsblock.materialex.core.tabs.TabLifecycle
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
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.MediaSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import org.mozilla.geckoview.WebRequestError
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

class BrowserEngine(context: Context, private val permissionManager: PermissionManager) {
    private val runtime: GeckoRuntime = GeckoRuntime.create(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager = TabManager()
    val siteRules = SiteStyleBridge(context, scope)
    val devTools = DevToolsBridge()
    private val siteUaApplied = HashSet<String>()
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
    private val savedStates = HashMap<String, GeckoSession.SessionState>()
    private var trimLevel = 0
    private val extensionFlow = MutableStateFlow<List<ExtensionInfo>>(emptyList())
    private val extensionPromptFlow = MutableStateFlow<ExtensionPrompt?>(null)
    private val extensionErrorFlow = MutableStateFlow(false)
    private val extensionUpdateFlow = MutableStateFlow<Map<String, ExtensionUpdateStatus>>(emptyMap())
    private var extensionDecision: GeckoResult<WebExtension.PermissionPromptResponse>? = null
    private val bypass = BypassGate()
    private val profileFlow = MutableStateFlow(ProfileRules.DEFAULT_ID)
    private var lastRealProfile = ProfileRules.DEFAULT_ID
    private val threatFlow = MutableStateFlow<Map<String, ThreatInfo>>(emptyMap())
    private var lastGestureAt = 0L
    private val fullscreenFlow = MutableStateFlow(false)
    private var fullscreenId: String? = null

    val permissions = PermissionBroker(permissionManager, DEFAULT_PROFILE, ::permissionProfileOf)
    private val permissionUse = ActivePermissionUse()

    init {
        permissionManager.onRevoked = { revocation -> scope.launch { applyRevocation(revocation) } }
        permissions.onTemporaryRevoked = { revocation -> scope.launch { applyRevocation(revocation) } }
    }

    private fun trackUse(tabId: String, uri: String, type: PermissionType) {
        val origin = Origin.normalize(uri) ?: return
        permissionUse.record(tabId, permissionProfileOf(tabId), origin, type)
    }

    private fun applyRevocation(revocation: Revocation) {
        if (revocation.origin != null) resetEngineGrant(revocation.origin, revocation.type)
        for (tabId in permissionUse.take(revocation)) {
            (sessions[tabId] ?: popupSessions[tabId])?.reload()
        }
    }

    private fun resetEngineGrant(origin: String, type: PermissionType?) {
        runtime.storageController.getPermissions(origin).accept({ stored ->
            stored?.forEach { perm ->
                val mapped = when (perm.permission) {
                    GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> PermissionType.LOCATION
                    GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> PermissionType.NOTIFICATIONS
                    GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> PermissionType.PERSISTENT_STORAGE
                    else -> null
                }
                if (mapped != null && (type == null || type == mapped)) {
                    runtime.storageController.setPermission(perm, GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT)
                }
            }
        }, { })
    }
    val osBlocked: StateFlow<Boolean> = osBlockedFlow.asStateFlow()
    val chromeVisible: StateFlow<Boolean> = chromeFlow.asStateFlow()
    val atTop: StateFlow<Boolean> = atTopFlow.asStateFlow()
    val popups: StateFlow<List<PopupWindow>> = popupFlow.asStateFlow()
    val fullscreen: StateFlow<Boolean> = fullscreenFlow.asStateFlow()
    fun applyWebDisplay(forceZoom: Boolean, textScalePercent: Int, followSystem: Boolean) {
        val settings = runtime.settings
        settings.forceUserScalableEnabled = forceZoom
        settings.fontSizeFactor = TextScale.factor(textScalePercent)
        settings.automaticFontSizeAdjustment = followSystem
    }

    var popupBlocking: Boolean = true
    var loadedLimit: Int = TabLifecycle.DEFAULT_LOADED
        set(value) {
            field = value
            enforceLoadLimit()
        }

    val threats: StateFlow<Map<String, ThreatInfo>> = threatFlow.asStateFlow()
    val extensions: StateFlow<List<ExtensionInfo>> = extensionFlow.asStateFlow()
    val extensionPrompt: StateFlow<ExtensionPrompt?> = extensionPromptFlow.asStateFlow()
    val extensionError: StateFlow<Boolean> = extensionErrorFlow.asStateFlow()
    val extensionUpdates: StateFlow<Map<String, ExtensionUpdateStatus>> = extensionUpdateFlow.asStateFlow()

    private val extensionPromptDelegate = object : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(
            extension: WebExtension,
            permissions: Array<String>,
            origins: Array<String>,
            dataCollectionPermissions: Array<String>
        ): GeckoResult<WebExtension.PermissionPromptResponse>? {
            val result = GeckoResult<WebExtension.PermissionPromptResponse>()
            extensionDecision?.complete(WebExtension.PermissionPromptResponse(false, false, false))
            extensionDecision = result
            extensionPromptFlow.value = ExtensionPrompt(
                extensionName = extension.metaData.name ?: extension.id,
                permissions = permissions.toList(),
                origins = origins.toList()
            )
            return result
        }
    }

    fun setupExtensions() {
        runtime.webExtensionController.setPromptDelegate(extensionPromptDelegate)
        siteRules.install(runtime)
        devTools.install(runtime)
        refreshExtensions()
    }

    fun refreshExtensions() {
        runtime.webExtensionController.list().accept({ list ->
            extensionFlow.value = (list ?: emptyList()).filter { it.id != SiteStyleBridge.EXTENSION_ID && it.id != DevToolsBridge.EXTENSION_ID }.map {
                ExtensionInfo(
                    id = it.id,
                    name = it.metaData.name ?: it.id,
                    version = it.metaData.version,
                    enabled = it.metaData.enabled,
                    description = it.metaData.description.orEmpty(),
                    permissions = (it.metaData.requiredPermissions.toList() + it.metaData.grantedOptionalPermissions.toList()).distinct(),
                    origins = (it.metaData.requiredOrigins.toList() + it.metaData.grantedOptionalOrigins.toList()).distinct()
                )
            }
        }, { extensionFlow.value = emptyList() })
    }

    fun installExtension(url: String) {
        extensionErrorFlow.value = false
        runtime.webExtensionController.install(url, WebExtensionController.INSTALLATION_METHOD_MANAGER)
            .accept({ refreshExtensions() }, { extensionErrorFlow.value = true; refreshExtensions() })
    }

    fun answerExtensionPrompt(allow: Boolean) {
        extensionDecision?.complete(WebExtension.PermissionPromptResponse(allow, false, false))
        extensionDecision = null
        extensionPromptFlow.value = null
    }

    fun setExtensionEnabled(id: String, enabled: Boolean) {
        val controller = runtime.webExtensionController
        controller.list().accept({ list ->
            val target = list?.firstOrNull { it.id == id } ?: return@accept
            val action = if (enabled) controller.enable(target, WebExtensionController.EnableSource.USER)
            else controller.disable(target, WebExtensionController.EnableSource.USER)
            action.accept({ refreshExtensions() }, { refreshExtensions() })
        }, { refreshExtensions() })
    }

    fun checkExtensionUpdate(id: String) {
        val controller = runtime.webExtensionController
        extensionUpdateFlow.update { it + (id to ExtensionUpdateStatus.CHECKING) }
        controller.list().accept({ list ->
            val target = list?.firstOrNull { it.id == id }
            if (target == null) {
                extensionUpdateFlow.update { it + (id to ExtensionUpdateStatus.FAILED) }
                return@accept
            }
            controller.update(target).accept({ updated ->
                extensionUpdateFlow.update {
                    it + (id to if (updated != null) ExtensionUpdateStatus.UPDATED else ExtensionUpdateStatus.LATEST)
                }
                refreshExtensions()
            }, { extensionUpdateFlow.update { it + (id to ExtensionUpdateStatus.FAILED) } })
        }, { extensionUpdateFlow.update { it + (id to ExtensionUpdateStatus.FAILED) } })
    }

    fun removeExtension(id: String) {
        val controller = runtime.webExtensionController
        controller.list().accept({ list ->
            val target = list?.firstOrNull { it.id == id } ?: return@accept
            controller.uninstall(target).accept({ refreshExtensions() }, { refreshExtensions() })
        }, { refreshExtensions() })
    }

    var safeBrowsingEnabled: Boolean = true
        set(value) {
            field = value
            applySafeBrowsing()
        }

    var cookieBannerHandling: Boolean = true
        set(value) {
            field = value
            runtime.settings.contentBlocking.setCookieBannerMode(
                if (value) ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_REJECT_OR_ACCEPT
                else ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_DISABLED
            )
        }

    private fun applySafeBrowsing() {
        val on = safeBrowsingEnabled && !bypass.isActive()
        runtime.settings.contentBlocking.setSafeBrowsing(
            if (on) ContentBlocking.SafeBrowsing.DEFAULT else ContentBlocking.SafeBrowsing.NONE
        )
    }

    fun leaveThreat(id: String) {
        threatFlow.update { it - id }
        val session = sessions[id] ?: return
        if (pages.value[id]?.canGoBack == true) session.goBack() else session.loadUri("about:blank")
    }

    fun continueDespiteThreat(id: String) {
        val info = threatFlow.value[id] ?: return
        val session = sessions[id] ?: return
        bypass.begin(id, info.url)
        applySafeBrowsing()
        threatFlow.update { it - id }
        session.loadUri(info.url)
    }

    private fun endBypassFor(id: String) {
        if (bypass.endIfFor(id)) applySafeBrowsing()
    }

    private fun threatKind(code: Int): ThreatKind? = when (code) {
        WebRequestError.ERROR_SAFEBROWSING_PHISHING_URI -> ThreatKind.PHISHING
        WebRequestError.ERROR_SAFEBROWSING_MALWARE_URI -> ThreatKind.MALWARE
        WebRequestError.ERROR_SAFEBROWSING_UNWANTED_URI -> ThreatKind.UNWANTED
        WebRequestError.ERROR_SAFEBROWSING_HARMFUL_URI -> ThreatKind.HARMFUL
        else -> null
    }

    private fun threatCodeName(kind: ThreatKind): String = when (kind) {
        ThreatKind.PHISHING -> "ERROR_SAFEBROWSING_PHISHING_URI"
        ThreatKind.MALWARE -> "ERROR_SAFEBROWSING_MALWARE_URI"
        ThreatKind.UNWANTED -> "ERROR_SAFEBROWSING_UNWANTED_URI"
        ThreatKind.HARMFUL -> "ERROR_SAFEBROWSING_HARMFUL_URI"
    }

    fun trimMemory(level: Int) {
        trimLevel = level
        enforceLoadLimit()
        trimLevel = 0
    }
    var androidPermissionRequester: ((Array<String>, (Boolean) -> Unit) -> Unit)? = null
    var onDownload: ((WebResponse, Boolean) -> Unit)? = null
    var onPrivateSessionEnded: (() -> Unit)? = null
    var onPageVisited: ((url: String, title: String) -> Unit)? = null

    val activeProfile: StateFlow<String> = profileFlow.asStateFlow()
    val tabs: StateFlow<TabsState> = combine(manager.state, profileFlow) { all, profile ->
        all.copy(
            tabs = all.tabs.filter { it.profileId == profile },
            recentlyClosed = all.recentlyClosed.filter { it.profileId == profile }
        )
    }.stateIn(scope, SharingStarted.Eagerly, TabsState())
    val pageStates: StateFlow<Map<String, PageState>> = pages.asStateFlow()
    val activeSession: StateFlow<GeckoSession?> = active.asStateFlow()
    val state: StateFlow<PageState> = combine(manager.state, pages) { tabsState, map ->
        tabsState.activeId?.let { map[it] } ?: PageState()
    }.stateIn(scope, SharingStarted.Eagerly, PageState())

    fun openTab(url: String, select: Boolean = true, private: Boolean = false): String {
        val profile = profileFlow.value
        val tab = manager.open(url, profile, private || profile == ProfileRules.GUEST_ID, select)
        launch(tab.id, url)
        return tab.id
    }

    private fun newSession(profileId: String, isPrivate: Boolean): GeckoSession {
        val builder = GeckoSessionSettings.Builder().usePrivateMode(isPrivate)
        ProfileRules.contextId(profileId, isPrivate)?.let { builder.contextId(it) }
        return GeckoSession(builder.build())
    }

    fun permissionProfileOf(tabId: String): String {
        val root = popupFlow.value.firstOrNull { it.id == tabId }?.openerId ?: tabId
        val tab = manager.state.value.tabs.firstOrNull { it.id == root } ?: return profileFlow.value.takeIf { ProfileRules.isRealProfile(it) } ?: lastRealProfile
        return if (tab.isPrivate || !ProfileRules.isRealProfile(tab.profileId)) EPHEMERAL_PERMISSION_PROFILE else tab.profileId
    }

    fun activePermissionProfile(): String {
        val id = manager.state.value.activeId.orEmpty()
        return permissionProfileOf(id)
    }

    private fun clearEphemeralPermissionsIfIdle() {
        scope.launch {
            if (manager.state.value.tabs.none { it.isPrivate || !ProfileRules.isRealProfile(it.profileId) }) {
                permissionManager.clearProfile(EPHEMERAL_PERMISSION_PROFILE)
                permissions.temporary.revokeProfile(EPHEMERAL_PERMISSION_PROFILE)
                onPrivateSessionEnded?.invoke()
            }
        }
    }

    private fun isPrivateId(id: String): Boolean {
        val root = popupFlow.value.firstOrNull { it.id == id }?.openerId ?: id
        return manager.state.value.tabs.firstOrNull { it.id == root }?.isPrivate == true
    }

    fun switchProfile(id: String) {
        if (profileFlow.value == id) return
        if (id != ProfileRules.GUEST_ID && !ProfileRules.isRealProfile(id)) return
        val leaving = profileFlow.value
        if (leaving != ProfileRules.GUEST_ID) lastRealProfile = leaving
        profileFlow.value = id
        val target = manager.state.value.tabs.filter { it.profileId == id }.maxByOrNull { it.lastUsedAt }
        if (target != null) selectTab(target.id) else openTab("")
        if (leaving == ProfileRules.GUEST_ID) discardProfileTabs(ProfileRules.GUEST_ID)
    }

    fun exitGuest() {
        if (profileFlow.value == ProfileRules.GUEST_ID) switchProfile(lastRealProfile)
    }

    fun deleteProfile(id: String) {
        if (!ProfileRules.canDelete(id)) return
        if (profileFlow.value == id) switchProfile(ProfileRules.DEFAULT_ID)
        if (lastRealProfile == id) lastRealProfile = ProfileRules.DEFAULT_ID
        discardProfileTabs(id)
        ProfileRules.contextId(id, false)?.let { runtime.storageController.clearDataForSessionContext(it) }
    }

    private fun discardProfileTabs(profileId: String) {
        manager.removeProfile(profileId).forEach(::dropSession)
        syncActive()
    }

    private fun launch(id: String, url: String) {
        val tab = manager.state.value.tabs.firstOrNull { it.id == id }
        val session = newSession(tab?.profileId ?: ProfileRules.DEFAULT_ID, tab?.isPrivate == true)
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
        if (tab.profileId != profileFlow.value) profileFlow.value = tab.profileId
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

    fun moveTabBy(id: String, delta: Int) {
        val all = manager.state.value.tabs
        val tab = all.firstOrNull { it.id == id } ?: return
        val peers = all.filter { it.profileId == tab.profileId }
        val neighbor = peers.getOrNull(peers.indexOfFirst { it.id == id } + delta) ?: return
        manager.move(id, all.indexOfFirst { it.id == neighbor.id })
    }

    fun exitFullscreen() {
        val id = fullscreenId ?: return
        fullscreenId = null
        fullscreenFlow.value = false
        (sessions[id] ?: popupSessions[id])?.exitFullScreen()
    }

    private fun clearFullscreenFor(id: String) {
        if (fullscreenId == id) {
            fullscreenId = null
            fullscreenFlow.value = false
        }
    }

    private fun dropSession(id: String) {
        clearFullscreenFor(id)
        permissions.tabClosed(id)
        threatFlow.update { it - id }
        endBypassFor(id)
        closePopupsFor(id)
        sessions.remove(id)?.let(pendingClose::add)
        pages.update { it - id }
        savedStates.remove(id)
        lastVisit.remove(id)
        trackers.remove(id)
        scrollTops.remove(id)
        siteUaApplied.remove(id)
        permissionUse.dropTab(id)
        clearEphemeralPermissionsIfIdle()
    }

    fun selectTab(id: String) {
        if (fullscreenId != null && fullscreenId != id) exitFullscreen()
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
        clearFullscreenFor(id)
        permissions.tabClosed(id)
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
        val openerTab = manager.state.value.tabs.firstOrNull { it.id == openerId }
        val session = newSession(openerTab?.profileId ?: ProfileRules.DEFAULT_ID, openerTab?.isPrivate == true)
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

    fun lastUserGestureAt(): Long = lastGestureAt

    fun clearBrowsingData(cookiesAndSiteData: Boolean, cache: Boolean) {
        var flags = 0L
        if (cookiesAndSiteData) {
            flags = flags or org.mozilla.geckoview.StorageController.ClearFlags.COOKIES or
                org.mozilla.geckoview.StorageController.ClearFlags.DOM_STORAGES or
                org.mozilla.geckoview.StorageController.ClearFlags.AUTH_SESSIONS
        }
        if (cache) {
            flags = flags or org.mozilla.geckoview.StorageController.ClearFlags.ALL_CACHES
        }
        if (flags != 0L) runtime.storageController.clearData(flags)
    }

    fun load(url: String) {
        noteUserGesture()
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

    fun activeSiteHost(): String? {
        val id = manager.state.value.activeId ?: return null
        return SiteStyleRules.hostOf(pages.value[id]?.url.orEmpty())
    }

    fun userAgentFor(host: String): UaChoice = siteRules.userAgents.value[host] ?: UaChoice(UaMode.DEFAULT)

    fun applySiteUserAgent(host: String, choice: UaChoice) {
        val id = manager.state.value.activeId ?: return
        val session = sessions[id] ?: return
        scope.launch {
            siteRules.setUserAgent(host, choice.takeIf { it.mode != UaMode.DEFAULT })
            applyChoice(id, session, choice.takeIf { it.mode != UaMode.DEFAULT })
            session.reload()
        }
    }

    private fun syncSiteUserAgent(id: String, session: GeckoSession, uri: String) {
        val host = SiteStyleRules.hostOf(uri) ?: return
        val choice = siteRules.userAgents.value[host]
        if (choice != null) applyChoice(id, session, choice)
        else if (siteUaApplied.remove(id)) applyChoice(id, session, null)
    }

    private fun applyChoice(id: String, session: GeckoSession, choice: UaChoice?) {
        val desktopView = choice?.mode == UaMode.DESKTOP
        session.settings.userAgentOverride = if (choice?.mode == UaMode.CUSTOM) choice.custom else null
        session.settings.userAgentMode =
            if (desktopView) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        session.settings.viewportMode =
            if (desktopView) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        if (choice != null) siteUaApplied.add(id) else siteUaApplied.remove(id)
        change(id) { it.copy(desktopMode = desktopView) }
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

    private fun reviveActive() {
        val id = manager.state.value.activeId ?: return
        if (sessions.containsKey(id)) return
        val tab = manager.state.value.tabs.firstOrNull { it.id == id } ?: return
        val url = pages.value[id]?.url?.ifEmpty { null } ?: tab.url
        val session = newSession(tab.profileId, tab.isPrivate)
        attach(id, session)
        session.open(runtime)
        sessions[id] = session
        trackers.getOrPut(id) { ScrollChromeTracker() }
        pages.update { it + (id to (it[id] ?: PageState(url = url))) }
        val saved = savedStates.remove(id)
        if (saved != null) session.restoreState(saved) else if (url.isNotEmpty()) session.loadUri(url)
    }

    private fun enforceLoadLimit() {
        val state = manager.state.value
        val limit = MemoryBudget.effectiveLimit(loadedLimit, trimLevel)
        val keep = TabLifecycle.loadedSet(state, limit).toMutableSet()
        keep += MemoryBudget.protectedIds(
            audibleIds = pages.value.filterValues { it.audible }.keys,
            openerIds = popupFlow.value.map { it.openerId }.toSet()
        )
        state.activeId?.let(keep::add)
        sessions.keys.filter { it !in keep }.forEach { id ->
            val session = sessions.remove(id) ?: return@forEach
            session.close()
            change(id) { it.copy(isLoading = false, progress = 0, audible = false) }
        }
    }

    private fun syncActive() {
        reviveActive()
        enforceLoadLimit()
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
                val threat = threatFlow.value[id]
                val shown = if (threat != null && url.orEmpty().startsWith("data:")) threat.url else url.orEmpty()
                if (threat != null && !url.orEmpty().startsWith("data:")) threatFlow.update { it - id }
                change(id) { it.copy(url = shown) }
                manager.update(id, url = shown)
                permissions.navigated(id, shown)
                permissionUse.dropOutside(id, Origin.normalize(shown))
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_CURRENT) {
                    syncSiteUserAgent(id, session, request.uri)
                }
                return null
            }

            override fun onLoadError(
                session: GeckoSession,
                uri: String?,
                error: WebRequestError
            ): GeckoResult<String>? {
                val kind = if (error.category == WebRequestError.ERROR_CATEGORY_SAFEBROWSING) threatKind(error.code) else null
                if (kind == null || uri.isNullOrEmpty()) {
                    endBypassFor(id)
                    return null
                }
                if (bypass.isActiveFor(id)) {
                    endBypassFor(id)
                    return null
                }
                threatFlow.update {
                    it + (id to ThreatInfo(id, uri, kind, error.code, threatCodeName(kind), System.currentTimeMillis()))
                }
                return GeckoResult.fromValue("data:text/html;charset=utf-8,")
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                if (!PopupPolicy.schemeAllowed(uri)) return GeckoResult.fromValue(null)
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
            override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
                if (sessions[id] === session) savedStates[id] = sessionState
            }

            override fun onPageStart(session: GeckoSession, url: String) {
                change(id) { it.copy(isLoading = true, progress = 0) }
                trackers[id]?.reset()
                scrollTops[id] = 0
                if (manager.state.value.activeId == id) atTopFlow.value = true
                if (manager.state.value.activeId == id) chromeFlow.value = true
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                endBypassFor(id)
                change(id) { it.copy(isLoading = false, progress = 100) }
                val page = pages.value[id]
                if (success && page != null && page.url.isNotEmpty() && lastVisit[id] != page.url && !isPrivateId(id)) {
                    lastVisit[id] = page.url
                    onPageVisited?.invoke(page.url, page.title)
                }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                val previous = pages.value[id]?.progress ?: 0
                if (progress >= 100 || progress < previous || progress - previous >= PROGRESS_STEP) {
                    change(id) { it.copy(progress = progress) }
                }
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
                    val allowed = permissions.resolve(perm.uri, type, id)
                    if (allowed) trackUse(id, perm.uri, type)
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
                    val videoAllowed = videoSource != null && permissions.resolve(uri, PermissionType.CAMERA, id)
                    val audioAllowed = audioSource != null && permissions.resolve(uri, PermissionType.MICROPHONE, id)
                    if (videoAllowed) trackUse(id, uri, PermissionType.CAMERA)
                    if (audioAllowed) trackUse(id, uri, PermissionType.MICROPHONE)
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
                onDownload?.invoke(response, isPrivateId(id))
            }

            override fun onCloseRequest(session: GeckoSession) {
                if (popupSessions.containsKey(id)) closePopup(id)
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                if (fullScreen) {
                    val foreground = !popupSessions.containsKey(id) && manager.state.value.activeId == id
                    if (!foreground) {
                        session.exitFullScreen()
                        return
                    }
                    fullscreenId = id
                    fullscreenFlow.value = true
                } else {
                    clearFullscreenFor(id)
                }
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
        const val PROGRESS_STEP = 5
    }
}
