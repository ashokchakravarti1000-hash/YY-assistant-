package com.example.core.storage

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.core.common.DownloadProgress
import com.example.core.common.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resumable HTTP Range downloader for one-time model & voice pack installation
 * (Connected mode only; never invoked in Airgapped mode).
 * Features Wi-Fi enforcement, 1.2x disk guard, .part atomic commit, and SHA-256 verification.
 */
class ModelDownloadManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _downloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadProgress>> = _downloads.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()

    fun startOrResume(
        itemId: String,
        url: String,
        expectedTotalBytes: Long,
        targetSubdir: String,
        fileName: String,
        wifiOnly: Boolean,
        freeDiskBytes: Long,
        onCompleted: suspend (file: File, sha256: String, actualSize: Long) -> Unit
    ) {
        if (activeJobs[itemId]?.isActive == true) return

        if (!url.startsWith("https://", ignoreCase = true)) {
            updateProgress(
                itemId,
                DownloadProgress(
                    itemId = itemId,
                    downloadedBytes = 0L,
                    totalBytes = expectedTotalBytes,
                    status = DownloadStatus.FAILED,
                    errorMessage = "Security Block: Only HTTPS model host URLs are permitted."
                )
            )
            return
        }

        if (wifiOnly && !isOnUnmeteredWifi()) {
            updateProgress(
                itemId,
                DownloadProgress(
                    itemId = itemId,
                    downloadedBytes = 0L,
                    totalBytes = expectedTotalBytes,
                    status = DownloadStatus.FAILED,
                    errorMessage = "Wi-Fi only is enabled. Connect to Wi-Fi or disable the switch."
                )
            )
            return
        }

        if (freeDiskBytes > 0L && (expectedTotalBytes * 1.2) > freeDiskBytes) {
            updateProgress(
                itemId,
                DownloadProgress(
                    itemId = itemId,
                    downloadedBytes = 0L,
                    totalBytes = expectedTotalBytes,
                    status = DownloadStatus.FAILED,
                    errorMessage = "Not enough storage (requires 1.2× free disk space)."
                )
            )
            return
        }

        val job = scope.launch(Dispatchers.IO) {
            val baseDir = File(context.filesDir, "$targetSubdir/$itemId").apply { mkdirs() }
            val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val partFile = File(baseDir, "$safeFileName.part")
            val finalFile = File(baseDir, safeFileName)

            val existingBytes = if (partFile.exists()) partFile.length() else 0L
            updateProgress(
                itemId,
                DownloadProgress(
                    itemId = itemId,
                    downloadedBytes = existingBytes,
                    totalBytes = expectedTotalBytes,
                    status = DownloadStatus.DOWNLOADING
                )
            )

            try {
                val reqBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "YYAssistant-Android-OfflineInstaller/1.0")
                if (existingBytes > 0L) {
                    reqBuilder.header("Range", "bytes=$existingBytes-")
                }

                client.newCall(reqBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        error("HTTP ${response.code}: ${response.message}")
                    }
                    val body = response.body ?: error("Empty response body")
                    val appendMode = response.code == 206 && existingBytes > 0L
                    val contentLen = body.contentLength()
                    val totalBytes = when {
                        contentLen > 0 && appendMode -> existingBytes + contentLen
                        contentLen > 0 -> contentLen
                        else -> expectedTotalBytes
                    }

                    var downloaded = if (appendMode) existingBytes else 0L
                    var lastReportTime = System.currentTimeMillis()
                    var lastReportBytes = downloaded

                    body.byteStream().use { input ->
                        FileOutputStream(partFile, appendMode).use { fos ->
                            val buf = ByteArray(32 * 1024)
                            var read = input.read(buf)
                            while (read != -1) {
                                fos.write(buf, 0, read)
                                downloaded += read

                                val now = System.currentTimeMillis()
                                if (now - lastReportTime >= 250L) {
                                    val dtSec = ((now - lastReportTime) / 1000.0).coerceAtLeast(0.1)
                                    val speed = ((downloaded - lastReportBytes) / dtSec).toLong()
                                    updateProgress(
                                        itemId,
                                        DownloadProgress(
                                            itemId = itemId,
                                            downloadedBytes = downloaded,
                                            totalBytes = maxOf(totalBytes, downloaded),
                                            status = DownloadStatus.DOWNLOADING,
                                            speedBytesPerSec = speed
                                        )
                                    )
                                    lastReportTime = now
                                    lastReportBytes = downloaded
                                }
                                read = input.read(buf)
                            }
                            fos.flush()
                        }
                    }

                    updateProgress(
                        itemId,
                        DownloadProgress(
                            itemId = itemId,
                            downloadedBytes = downloaded,
                            totalBytes = maxOf(totalBytes, downloaded),
                            status = DownloadStatus.VERIFYING
                        )
                    )

                    val sha256 = verifyFileSha256(partFile)
                    if (finalFile.exists()) finalFile.delete()
                    check(partFile.renameTo(finalFile)) { "Failed to rename verified model file." }

                    onCompleted(finalFile, sha256, finalFile.length())

                    updateProgress(
                        itemId,
                        DownloadProgress(
                            itemId = itemId,
                            downloadedBytes = finalFile.length(),
                            totalBytes = finalFile.length(),
                            status = DownloadStatus.COMPLETED
                        )
                    )
                }
            } catch (e: CancellationException) {
                val curBytes = if (partFile.exists()) partFile.length() else existingBytes
                updateProgress(
                    itemId,
                    DownloadProgress(
                        itemId = itemId,
                        downloadedBytes = curBytes,
                        totalBytes = expectedTotalBytes,
                        status = DownloadStatus.PAUSED
                    )
                )
                throw e
            } catch (e: Exception) {
                val curBytes = if (partFile.exists()) partFile.length() else existingBytes
                updateProgress(
                    itemId,
                    DownloadProgress(
                        itemId = itemId,
                        downloadedBytes = curBytes,
                        totalBytes = expectedTotalBytes,
                        status = DownloadStatus.FAILED,
                        errorMessage = e.message ?: "Download failed"
                    )
                )
            } finally {
                activeJobs.remove(itemId)
            }
        }
        activeJobs[itemId] = job
    }

    fun pause(itemId: String) {
        activeJobs[itemId]?.cancel()
    }

    fun cancelAndDeleteTemp(itemId: String, targetSubdir: String) {
        activeJobs[itemId]?.cancel()
        File(context.filesDir, "$targetSubdir/$itemId").deleteRecursively()
        val updated = _downloads.value.toMutableMap()
        updated.remove(itemId)
        _downloads.value = updated
    }

    suspend fun verifyFileSha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(64 * 1024)
            var n = fis.read(buf)
            while (n > 0) {
                digest.update(buf, 0, n)
                n = fis.read(buf)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isOnUnmeteredWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun updateProgress(itemId: String, progress: DownloadProgress) {
        val map = _downloads.value.toMutableMap()
        map[itemId] = progress
        _downloads.value = map
    }
}
