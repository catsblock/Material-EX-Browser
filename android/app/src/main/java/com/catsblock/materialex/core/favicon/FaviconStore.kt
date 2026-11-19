package com.catsblock.materialex.core.favicon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

object FaviconStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = LruCache<String, Bitmap>(96)
    private val failedAt = HashMap<String, Long>()
    private val inflight = HashMap<String, Deferred<Bitmap?>>()
    private const val RETRY_AFTER_MS = 10 * 60 * 1000L

    fun peek(url: String): Bitmap? {
        val host = FaviconPolicy.hostKey(url) ?: return null
        return synchronized(this) { cache.get(host) }
    }

    suspend fun get(url: String): Bitmap? {
        val host = FaviconPolicy.hostKey(url) ?: return null
        val job = synchronized(this) {
            cache.get(host)?.let { return it }
            val failed = failedAt[host]
            if (failed != null && System.currentTimeMillis() - failed < RETRY_AFTER_MS) return null
            inflight.getOrPut(host) {
                scope.async {
                    val bitmap = fetch(host)
                    synchronized(this@FaviconStore) {
                        inflight.remove(host)
                        if (bitmap != null) cache.put(host, bitmap) else failedAt[host] = System.currentTimeMillis()
                    }
                    bitmap
                }
            }
        }
        return job.await()
    }

    private fun fetch(host: String): Bitmap? {
        val connection = runCatching { URL(FaviconPolicy.iconUrl(host)).openConnection() as HttpURLConnection }
            .getOrNull() ?: return null
        return try {
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "image/*")
            if (connection.responseCode != 200 || connection.url.protocol != "https") return null
            val bytes = connection.inputStream.use { stream ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val read = stream.read(chunk)
                    if (read < 0) break
                    buffer.write(chunk, 0, read)
                    if (buffer.size() > FaviconPolicy.MAX_BYTES) return null
                }
                buffer.toByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = FaviconPolicy.sampleSize(bounds.outWidth, bounds.outHeight)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    fun clear() = synchronized(this) {
        cache.evictAll()
        failedAt.clear()
    }
}
