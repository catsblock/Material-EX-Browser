package com.catsblock.materialex.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private val entries = listOf(
        SearchEntry("Search engine", "Choose where searches go", "Search", 1),
        SearchEntry("Suggestions", "Show suggestions while typing in the search field", "Search", 2),
        SearchEntry("Popup blocking", "Stop sites opening windows", "Privacy", 3),
        SearchEntry("Café mode", "Warm colours", "Appearance", 4)
    )

    private fun ids(query: String) = SettingsSearch.results(query, entries).map { it.payload }

    @Test
    fun blankQueryReturnsNothing() {
        assertTrue(ids("   ").isEmpty())
    }

    @Test
    fun matchesTitlesDescriptionsAndCategories() {
        assertEquals(listOf(3), ids("windows"))
        assertEquals(listOf(3), ids("privacy"))
        assertEquals(listOf(1, 2), ids("search"))
    }

    @Test
    fun everyWordMustMatchInAnyField() {
        assertEquals(listOf(2), ids("typing search"))
        assertTrue(ids("typing popup").isEmpty())
    }

    @Test
    fun ignoresCaseAndAccents() {
        assertEquals(listOf(4), ids("CAFE"))
        assertEquals(listOf(4), ids("café"))
    }

    @Test
    fun titleMatchesRankBeforeDescriptionMatchesAndTiesStayStable() {
        val list = listOf(
            SearchEntry("Other", "mentions engine here", "X", 1),
            SearchEntry("Engine", "", "X", 2),
            SearchEntry("Engine options", "", "X", 3)
        )
        assertEquals(listOf(2, 3, 1), SettingsSearch.results("engine", list).map { it.payload })
    }

    @Test
    fun overlongQueriesAreTruncated() {
        val padded = "search".padEnd(SettingsSearch.MAX_QUERY_LENGTH, ' ') + "zzz"
        assertEquals(2, SettingsSearch.results(padded, entries).size)
    }
}
