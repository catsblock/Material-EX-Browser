package com.catsblock.materialex.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFilterTest {
    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(DownloadFilter.matches("a.zip", "https://x.example/a.zip", "  "))
    }

    @Test
    fun matchesFileNameAndHostIgnoringCaseAndAccents() {
        assertTrue(DownloadFilter.matches("Relatório Final.PDF", "https://files.example.org/r", "relatorio pdf"))
        assertTrue(DownloadFilter.matches("a.zip", "https://Files.Example.org/a.zip", "example"))
    }

    @Test
    fun everyTermMustMatchAndPathIsNotSearched() {
        assertFalse(DownloadFilter.matches("a.zip", "https://x.example/secret/a.zip", "secret"))
        assertFalse(DownloadFilter.matches("a.zip", "https://x.example/a.zip", "zip other"))
    }
}
