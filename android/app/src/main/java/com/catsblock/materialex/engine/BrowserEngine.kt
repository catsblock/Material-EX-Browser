package com.catsblock.materialex.engine

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

class BrowserEngine(context: Context) {
    private val runtime: GeckoRuntime = GeckoRuntime.create(context.applicationContext)
    private val mutableState = MutableStateFlow(PageState())

    val session: GeckoSession = GeckoSession()
    val state: StateFlow<PageState> = mutableState.asStateFlow()

    init {
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                mutableState.update { it.copy(url = url.orEmpty()) }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                mutableState.update { it.copy(canGoBack = canGoBack) }
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                mutableState.update { it.copy(canGoForward = canGoForward) }
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                mutableState.update { it.copy(isLoading = true, progress = 0) }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                mutableState.update { it.copy(isLoading = false, progress = 100) }
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                mutableState.update { it.copy(progress = progress) }
            }

            override fun onSecurityChange(
                session: GeckoSession,
                securityInfo: GeckoSession.ProgressDelegate.SecurityInformation
            ) {
                mutableState.update { it.copy(isSecure = securityInfo.isSecure) }
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                mutableState.update { it.copy(title = title.orEmpty()) }
            }
        }
        session.open(runtime)
    }

    fun load(url: String) = session.loadUri(url)

    fun goBack() = session.goBack()

    fun goForward() = session.goForward()

    fun reload() = session.reload()

    fun stop() = session.stop()
}
