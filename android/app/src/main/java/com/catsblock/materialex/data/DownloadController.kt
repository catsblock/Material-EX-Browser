package com.catsblock.materialex.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.catsblock.materialex.core.DownloadNames
import com.catsblock.materialex.core.DownloadStatus
import com.catsblock.materialex.core.SpeedMeter
import com.catsblock.materialex.data.db.DownloadDao
import com.catsblock.materialex.data.db.DownloadRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.mozilla.geckoview.WebResponse
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

data class LiveDownload(val downloaded: Long, val total: Long, val bytesPerSecond: Long)

class DownloadController(
    private val context: Context,
    private val dao: DownloadDao,
    private val scope: CoroutineScope
) {
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val streams = ConcurrentHashMap<Long, InputStream>()
    private val cancelRequested = ConcurrentHashMap.newKeySet<Long>()
    private val liveFlow = MutableStateFlow<Map<Long, LiveDownload>>(emptyMap())

    val live: StateFlow<Map<Long, LiveDownload>> = liveFlow.asStateFlow()

    fun records(): Flow<List<DownloadRecord>> = dao.observe()

    suspend fun recoverInterrupted() = dao.failInterrupted()

    fun start(response: WebResponse) {
        val uri = response.uri
        if (!uri.startsWith("https://") && !uri.startsWith("http://")) return
        val body = response.body ?: return
        val headers = response.headers.entries.associate { it.key.lowercase() to it.value }
        val mime = headers["content-type"]?.substringBefore(';')?.trim()?.ifEmpty { null } ?: "application/octet-stream"
        val total = headers["content-length"]?.toLongOrNull() ?: -1L
        val name = DownloadNames.resolve(headers["content-disposition"], uri)
        scope.launch { run(uri, name, mime, total, body) }
    }

    fun cancel(id: Long) {
        if (!jobs.containsKey(id)) return
        cancelRequested.add(id)
        jobs[id]?.cancel()
        runCatching { streams[id]?.close() }
    }

    suspend fun delete(record: DownloadRecord) {
        cancel(record.id)
        removeFile(record.fileUri)
        dao.delete(record.id)
    }

    suspend fun clearFinished() {
        dao.finished().forEach { removeFile(it.fileUri) }
        dao.clearFinished()
    }

    suspend fun openIntent(record: DownloadRecord): Intent? {
        if (record.status != DownloadStatus.COMPLETED.name) return null
        if (!exists(record.fileUri)) {
            dao.setStatus(record.id, DownloadStatus.MISSING.name)
            return null
        }
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(record.fileUri), record.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private suspend fun run(source: String, name: String, mime: String, total: Long, body: InputStream) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (target == null) {
            body.close()
            dao.insert(
                DownloadRecord(
                    fileName = name, sourceUrl = source, mimeType = mime, fileUri = "", totalBytes = total,
                    downloadedBytes = 0, status = DownloadStatus.FAILED.name, createdAt = System.currentTimeMillis()
                )
            )
            return
        }
        val id = dao.insert(
            DownloadRecord(
                fileName = name, sourceUrl = source, mimeType = mime, fileUri = target.toString(), totalBytes = total,
                downloadedBytes = 0, status = DownloadStatus.ACTIVE.name, createdAt = System.currentTimeMillis()
            )
        )
        val self = currentCoroutineContext()
        self[Job]?.let { jobs[id] = it }
        streams[id] = body
        var copied = 0L
        var status = DownloadStatus.COMPLETED
        try {
            val meter = SpeedMeter()
            var lastPublish = 0L
            var lastPersist = 0L
            val buffer = ByteArray(BUFFER_SIZE)
            val output = resolver.openOutputStream(target) ?: throw IOException()
            output.use { sink ->
                body.use { source ->
                    while (true) {
                        self.ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        copied += read
                        val now = System.currentTimeMillis()
                        if (now - lastPublish >= PUBLISH_MS) {
                            lastPublish = now
                            liveFlow.update { it + (id to LiveDownload(copied, total, meter.sample(copied, now))) }
                        }
                        if (now - lastPersist >= PERSIST_MS) {
                            lastPersist = now
                            dao.progress(id, copied, total)
                        }
                    }
                }
            }
            if (total > 0 && copied != total) status = DownloadStatus.FAILED
        } catch (e: CancellationException) {
            status = DownloadStatus.CANCELLED
        } catch (e: IOException) {
            status = if (cancelRequested.contains(id)) DownloadStatus.CANCELLED else DownloadStatus.FAILED
        } catch (e: SecurityException) {
            status = DownloadStatus.FAILED
        } finally {
            jobs.remove(id)
            streams.remove(id)
            cancelRequested.remove(id)
            liveFlow.update { it - id }
            if (status == DownloadStatus.COMPLETED) {
                resolver.update(target, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } else {
                runCatching { resolver.delete(target, null, null) }
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                dao.finish(id, status.name, copied, if (total > 0) total else copied)
            }
        }
    }

    private fun exists(uri: String): Boolean = runCatching {
        context.contentResolver.query(Uri.parse(uri), arrayOf(MediaStore.Downloads._ID), null, null, null)?.use { it.moveToFirst() } ?: false
    }.getOrDefault(false)

    private fun removeFile(uri: String) {
        if (uri.isEmpty()) return
        runCatching { context.contentResolver.delete(Uri.parse(uri), null, null) }
    }

    private companion object {
        const val BUFFER_SIZE = 32 * 1024
        const val PUBLISH_MS = 250L
        const val PERSIST_MS = 1000L
    }
}
