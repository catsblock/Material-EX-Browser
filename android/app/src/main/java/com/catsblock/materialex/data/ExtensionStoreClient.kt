package com.catsblock.materialex.data

import com.catsblock.materialex.core.extension.ExtensionStore
import com.catsblock.materialex.core.extension.StorePage
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExtensionStoreClient {
    suspend fun search(query: String, page: Int): Result<StorePage> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(ExtensionStore.searchUrl(query, page)).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/json")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${connection.responseCode}")
                val body = connection.inputStream.use { stream ->
                    val bytes = stream.readNBytes(MAX_BYTES + 1)
                    if (bytes.size > MAX_BYTES) error("response too large")
                    String(bytes, Charsets.UTF_8)
                }
                ExtensionStore.parse(body)
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
    }
}
