package com.catsblock.materialex.core

import java.util.Locale

enum class DownloadStatus { ACTIVE, COMPLETED, FAILED, CANCELLED, MISSING }

object DownloadNames {
    private const val MAX_LENGTH = 120
    private const val FALLBACK = "download"
    private val encoded = Regex("filename\\*\\s*=\\s*[A-Za-z0-9-]+'[^']*'([^;]+)", RegexOption.IGNORE_CASE)
    private val quoted = Regex("filename\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val plain = Regex("filename\\s*=\\s*([^;\\s]+)", RegexOption.IGNORE_CASE)

    fun resolve(contentDisposition: String?, url: String): String {
        val fromHeader = contentDisposition?.let { header ->
            encoded.find(header)?.groupValues?.get(1)?.let(::percentDecode)
                ?: quoted.find(header)?.groupValues?.get(1)
                ?: plain.find(header)?.groupValues?.get(1)
        }
        val fromUrl = url.substringBefore('#').substringBefore('?').substringAfterLast('/').let(::percentDecode)
        return sanitize(fromHeader ?: fromUrl)
    }

    fun sanitize(raw: String): String {
        val leaf = raw.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = buildString {
            for (c in leaf) {
                if (c.isISOControl() || c in "<>:\"|?*") append('_') else append(c)
            }
        }.trim().trim('.')
        if (cleaned.isEmpty()) return FALLBACK
        return if (cleaned.length <= MAX_LENGTH) cleaned else cleaned.take(MAX_LENGTH)
    }

    private fun percentDecode(value: String): String = runCatching {
        java.net.URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    }.getOrDefault(value)
}

object ByteFormat {
    fun size(bytes: Long): String = scaled(bytes, "")

    fun speed(bytesPerSecond: Long): String = scaled(bytesPerSecond, "/s")

    private fun scaled(value: Long, suffix: String): String {
        if (value < 1024) return "$value B$suffix"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var scaled = value.toDouble()
        var unit = -1
        while (scaled >= 1024 && unit < units.lastIndex) {
            scaled /= 1024
            unit++
        }
        return String.format(Locale.ROOT, "%.1f %s%s", scaled, units[unit], suffix)
    }
}

class SpeedMeter(private val windowMs: Long = 1000) {
    private var windowStart = 0L
    private var windowBytes = 0L
    private var lastSpeed = 0L
    private var started = false

    fun sample(totalBytes: Long, nowMs: Long): Long {
        if (!started) {
            started = true
            windowStart = nowMs
            windowBytes = totalBytes
            return 0
        }
        val elapsed = nowMs - windowStart
        if (elapsed >= windowMs) {
            lastSpeed = (totalBytes - windowBytes) * 1000 / elapsed
            windowStart = nowMs
            windowBytes = totalBytes
        }
        return lastSpeed
    }
}
