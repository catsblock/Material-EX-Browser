package com.catsblock.materialex.core

import java.util.Locale

enum class DownloadRisk { EXECUTABLE, SPOOFED_NAME, MIME_MISMATCH, INSECURE_SOURCE }

object DownloadSafety {
    private val executableExtensions = setOf(
        "apk", "apks", "xapk", "aab", "exe", "msi", "bat", "cmd", "com", "scr", "pif", "jar", "js", "jse",
        "vbs", "vbe", "wsf", "ps1", "psm1", "sh", "bash", "dex", "so", "lnk", "reg", "dll", "appimage",
        "deb", "rpm", "dmg", "pkg", "app"
    )
    private val executableMimes = setOf(
        "application/x-msdownload", "application/x-msdos-program", "application/vnd.android.package-archive",
        "application/x-sh", "application/x-executable", "application/java-archive", "application/x-apple-diskimage"
    )
    private val documentExtensions = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "jpg", "jpeg", "png", "gif", "mp3", "mp4", "zip"
    )
    private val bidiControls = setOf(
        '\u202A', '\u202B', '\u202C', '\u202D', '\u202E', '\u2066', '\u2067', '\u2068', '\u2069', '\u200E', '\u200F'
    )
    private val expectedMime = mapOf(
        "pdf" to "application/pdf",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "mp3" to "audio/mpeg",
        "mp4" to "video/mp4",
        "zip" to "application/zip",
        "apk" to "application/vnd.android.package-archive"
    )
    private val genericMimes = setOf(
        "", "application/octet-stream", "binary/octet-stream", "application/force-download", "application/download"
    )

    fun extension(fileName: String): String = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)

    fun assess(fileName: String, mimeType: String, sourceUrl: String): Set<DownloadRisk> {
        val risks = linkedSetOf<DownloadRisk>()
        val ext = extension(fileName)
        val declared = mimeType.substringBefore(';').trim().lowercase(Locale.ROOT).let { if (it == "image/jpg") "image/jpeg" else it }
        if (ext in executableExtensions || declared in executableMimes) risks += DownloadRisk.EXECUTABLE
        if (fileName.any { it in bidiControls }) {
            risks += DownloadRisk.SPOOFED_NAME
        } else {
            val parts = fileName.split('.')
            if (parts.size >= 3 && parts[parts.lastIndex - 1].lowercase(Locale.ROOT) in documentExtensions && ext in executableExtensions) {
                risks += DownloadRisk.SPOOFED_NAME
            }
        }
        val expected = expectedMime[ext]
        if (declared !in genericMimes && ((expected != null && declared != expected) || (ext !in executableExtensions && declared in executableMimes))) {
            risks += DownloadRisk.MIME_MISMATCH
        }
        if (sourceUrl.startsWith("http://", ignoreCase = true)) risks += DownloadRisk.INSECURE_SOURCE
        return risks
    }

    fun needsOpenConfirmation(risks: Set<DownloadRisk>): Boolean = risks.any { it != DownloadRisk.INSECURE_SOURCE }
}

object DownloadPolicy {
    const val MAX_ACTIVE = 6
    const val GESTURE_WINDOW_MS = 15_000L

    enum class Verdict { ALLOW, NO_INTERACTION, TOO_MANY }

    fun evaluate(lastGestureAt: Long, now: Long, activeCount: Int): Verdict = when {
        activeCount >= MAX_ACTIVE -> Verdict.TOO_MANY
        lastGestureAt <= 0L || now - lastGestureAt !in 0..GESTURE_WINDOW_MS -> Verdict.NO_INTERACTION
        else -> Verdict.ALLOW
    }
}

object DownloadEta {
    fun seconds(downloaded: Long, total: Long, bytesPerSecond: Long): Long? =
        if (total > downloaded && bytesPerSecond > 0) (total - downloaded + bytesPerSecond - 1) / bytesPerSecond else null

    fun format(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = seconds % 3600 / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, secs)
        }
    }
}
