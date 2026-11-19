package com.catsblock.materialex.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DownloadModelTest {
    @Test
    fun usesQuotedHeaderName() {
        assertEquals("report.pdf", DownloadNames.resolve("attachment; filename=\"report.pdf\"", "https://x.test/a"))
    }

    @Test
    fun prefersEncodedHeaderName() {
        val name = DownloadNames.resolve("attachment; filename=\"fallback.txt\"; filename*=UTF-8''na%C3%AFve%20file.txt", "https://x.test/a")
        assertEquals("na\u00efve file.txt", name)
    }

    @Test
    fun fallsBackToUrlLeaf() {
        assertEquals("photo.png", DownloadNames.resolve(null, "https://x.test/img/photo.png?size=2#top"))
        assertEquals("download", DownloadNames.resolve(null, "https://x.test/"))
    }

    @Test
    fun blocksPathTraversal() {
        assertEquals("passwd", DownloadNames.sanitize("../../etc/passwd"))
        assertEquals("evil.exe", DownloadNames.sanitize("C:\\Windows\\evil.exe"))
        assertFalse(DownloadNames.sanitize("a/../b").contains("/"))
    }

    @Test
    fun replacesReservedAndControlCharacters() {
        assertEquals("a_b_c.txt", DownloadNames.sanitize("a<b>c.txt"))
        assertEquals("a_b", DownloadNames.sanitize("a\u0000b"))
    }

    @Test
    fun limitsLengthAndRejectsDotNames() {
        assertEquals(120, DownloadNames.sanitize("x".repeat(500)).length)
        assertEquals("download", DownloadNames.sanitize(".."))
        assertEquals("download", DownloadNames.sanitize("   "))
    }

    @Test
    fun formatsSizesAndSpeeds() {
        assertEquals("512 B", ByteFormat.size(512))
        assertEquals("1.5 KB", ByteFormat.size(1536))
        assertEquals("3.0 MB/s", ByteFormat.speed(3L * 1024 * 1024))
    }

    @Test
    fun speedMeterMeasuresPerSecond() {
        val meter = SpeedMeter()
        assertEquals(0, meter.sample(0, 0))
        assertEquals(0, meter.sample(500, 400))
        assertEquals(2000, meter.sample(2000, 1000))
    }
}
