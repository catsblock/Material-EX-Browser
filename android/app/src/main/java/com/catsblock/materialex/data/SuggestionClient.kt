package com.catsblock.materialex.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class SuggestionClient {
    suspend fun fetch(url: String): String? {
        if (!url.startsWith("https://")) return null
        return withContext(Dispatchers.IO) {
            runInterruptible {
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = TIMEOUT_MS
                    connection.readTimeout = TIMEOUT_MS
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("Accept", "application/json")
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        null
                    } else {
                        connection.inputStream.use { String(it.readNBytes(MAX_BYTES), Charsets.UTF_8) }
                    }
                } catch (_: IOException) {
                    null
                } finally {
                    connection.disconnect()
                }
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 3000
        const val MAX_BYTES = 65_536
    }
}
