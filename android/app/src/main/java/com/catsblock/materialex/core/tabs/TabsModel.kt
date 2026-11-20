package com.catsblock.materialex.core.tabs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class Tab(
    val id: String,
    val profileId: String,
    val url: String,
    val title: String,
    val isPrivate: Boolean,
    val createdAt: Long,
    val lastUsedAt: Long
)

data class TabsState(
    val tabs: List<Tab> = emptyList(),
    val activeId: String? = null,
    val recentlyClosed: List<Tab> = emptyList()
) {
    val active: Tab? get() = tabs.firstOrNull { it.id == activeId }
}

object TabLifecycle {
    const val MIN_LOADED = 1
    const val MAX_LOADED = 30
    const val DEFAULT_LOADED = 5

    fun loadedSet(state: TabsState, limit: Int): Set<String> {
        val cap = limit.coerceIn(MIN_LOADED, MAX_LOADED)
        val active = state.activeId?.takeIf { id -> state.tabs.any { it.id == id } }
        val others = state.tabs
            .filter { it.id != active }
            .sortedByDescending { it.lastUsedAt }
            .take(if (active != null) cap - 1 else cap)
        return buildSet {
            active?.let(::add)
            others.forEach { add(it.id) }
        }
    }
}

class TabManager(
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = System::currentTimeMillis,
    private val maxRecentlyClosed: Int = 20,
    initial: TabsState = TabsState()
) {
    private val mutable = MutableStateFlow(initial)
    val state: StateFlow<TabsState> = mutable.asStateFlow()

    @Synchronized
    fun replaceState(state: TabsState) {
        mutable.value = state
    }

    @Synchronized
    fun open(url: String, profileId: String, isPrivate: Boolean, select: Boolean = true): Tab {
        val time = now()
        val tab = Tab(newId(), profileId, url, "", isPrivate, time, time)
        val current = mutable.value
        mutable.value = current.copy(
            tabs = current.tabs + tab,
            activeId = if (select || current.activeId == null) tab.id else current.activeId
        )
        return tab
    }

    @Synchronized
    fun removeProfile(profileId: String): List<String> {
        val current = mutable.value
        val ids = current.tabs.filter { it.profileId == profileId }.map { it.id }
        mutable.value = current.copy(
            tabs = current.tabs.filterNot { it.profileId == profileId },
            recentlyClosed = current.recentlyClosed.filterNot { it.profileId == profileId },
            activeId = if (current.activeId in ids) null else current.activeId
        )
        return ids
    }

    @Synchronized
    fun select(id: String) {
        val current = mutable.value
        if (current.tabs.none { it.id == id }) return
        val time = now()
        mutable.value = current.copy(
            tabs = current.tabs.map { if (it.id == id) it.copy(lastUsedAt = time) else it },
            activeId = id
        )
    }

    @Synchronized
    fun close(id: String): Tab? {
        val current = mutable.value
        val index = current.tabs.indexOfFirst { it.id == id }
        if (index < 0) return null
        val closed = current.tabs[index]
        val remaining = current.tabs.filterIndexed { i, _ -> i != index }
        val nextActive = when {
            current.activeId != id -> current.activeId
            else -> (remaining.getOrNull(index) ?: remaining.getOrNull(index - 1))?.id
        }
        val history = if (closed.isPrivate) current.recentlyClosed
        else (listOf(closed) + current.recentlyClosed).take(maxRecentlyClosed)
        mutable.value = TabsState(remaining, nextActive, history)
        return closed
    }

    @Synchronized
    fun closeOthers(id: String) {
        val current = mutable.value
        val keep = current.tabs.firstOrNull { it.id == id } ?: return
        val closed = current.tabs.filter { it.id != id && !it.isPrivate }
        mutable.value = TabsState(
            listOf(keep),
            keep.id,
            (closed.reversed() + current.recentlyClosed).take(maxRecentlyClosed)
        )
    }

    @Synchronized
    fun duplicate(id: String): Tab? {
        val current = mutable.value
        val index = current.tabs.indexOfFirst { it.id == id }
        if (index < 0) return null
        val source = current.tabs[index]
        val time = now()
        val copy = source.copy(id = newId(), createdAt = time, lastUsedAt = time)
        val tabs = current.tabs.toMutableList().apply { add(index + 1, copy) }
        mutable.value = current.copy(tabs = tabs, activeId = copy.id)
        return copy
    }

    @Synchronized
    fun restoreLastClosed(): Tab? {
        val current = mutable.value
        val closed = current.recentlyClosed.firstOrNull() ?: return null
        val time = now()
        val restored = closed.copy(id = newId(), lastUsedAt = time)
        mutable.value = current.copy(
            tabs = current.tabs + restored,
            activeId = restored.id,
            recentlyClosed = current.recentlyClosed.drop(1)
        )
        return restored
    }

    @Synchronized
    fun move(id: String, toIndex: Int) {
        val current = mutable.value
        val from = current.tabs.indexOfFirst { it.id == id }
        if (from < 0) return
        val tabs = current.tabs.toMutableList()
        val tab = tabs.removeAt(from)
        tabs.add(toIndex.coerceIn(0, tabs.size), tab)
        mutable.value = current.copy(tabs = tabs)
    }

    @Synchronized
    fun update(id: String, url: String? = null, title: String? = null) {
        val current = mutable.value
        if (current.tabs.none { it.id == id }) return
        mutable.value = current.copy(
            tabs = current.tabs.map {
                if (it.id == id) it.copy(url = url ?: it.url, title = title ?: it.title) else it
            }
        )
    }
}
