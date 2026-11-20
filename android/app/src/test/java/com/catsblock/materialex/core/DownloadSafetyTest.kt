package com.catsblock.materialex.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSafetyTest {
    @Test
    fun plainPdfOverHttpsHasNoRisk() {
        assertTrue(DownloadSafety.assess("report.pdf", "application/pdf", "https://a.example/report.pdf").isEmpty())
    }

    @Test
    fun executableExtensionIsFlagged() {
        val risks = DownloadSafety.assess("setup.exe", "application/octet-stream", "https://a.example/setup.exe")
        assertTrue(DownloadRisk.EXECUTABLE in risks)
        assertTrue(DownloadSafety.needsOpenConfirmation(risks))
    }

    @Test
    fun doubleExtensionIsSpoofed() {
        val risks = DownloadSafety.assess("invoice.pdf.exe", "application/octet-stream", "https://a.example/x")
        assertTrue(DownloadRisk.SPOOFED_NAME in risks)
    }

    @Test
    fun bidiOverrideIsSpoofed() {
        val risks = DownloadSafety.assess("photo\u202Egpj.exe", "application/octet-stream", "https://a.example/x")
        assertTrue(DownloadRisk.SPOOFED_NAME in risks)
    }

    @Test
    fun declaredMimeMustMatchKnownExtension() {
        val risks = DownloadSafety.assess("holiday.png", "text/html", "https://a.example/holiday.png")
        assertTrue(DownloadRisk.MIME_MISMATCH in risks)
        assertFalse(DownloadRisk.MIME_MISMATCH in DownloadSafety.assess("holiday.jpg", "image/jpeg", "https://a.example/h"))
    }

    @Test
    fun executableMimeUnderHarmlessNameIsFlagged() {
        val risks = DownloadSafety.assess("notes.txt", "application/x-msdownload", "https://a.example/n")
        assertTrue(DownloadRisk.EXECUTABLE in risks)
        assertTrue(DownloadRisk.MIME_MISMATCH in risks)
    }

    @Test
    fun insecureSourceAloneDoesNotRequireOpenConfirmation() {
        val risks = DownloadSafety.assess("a.pdf", "application/pdf", "http://a.example/a.pdf")
        assertEquals(setOf(DownloadRisk.INSECURE_SOURCE), risks)
        assertFalse(DownloadSafety.needsOpenConfirmation(risks))
    }

    @Test
    fun downloadWithoutRecentGestureIsBlocked() {
        assertEquals(DownloadPolicy.Verdict.NO_INTERACTION, DownloadPolicy.evaluate(0L, 10_000L, 0))
        assertEquals(DownloadPolicy.Verdict.NO_INTERACTION, DownloadPolicy.evaluate(1_000L, 30_000L, 0))
        assertEquals(DownloadPolicy.Verdict.ALLOW, DownloadPolicy.evaluate(5_000L, 10_000L, 0))
        assertEquals(DownloadPolicy.Verdict.TOO_MANY, DownloadPolicy.evaluate(5_000L, 10_000L, DownloadPolicy.MAX_ACTIVE))
    }

    @Test
    fun etaRoundsUpAndFormats() {
        assertEquals(10L, DownloadEta.seconds(0, 1000, 100))
        assertNull(DownloadEta.seconds(0, -1, 100))
        assertNull(DownloadEta.seconds(0, 1000, 0))
        assertEquals("1:05", DownloadEta.format(65))
        assertEquals("1:01:01", DownloadEta.format(3661))
    }
}
