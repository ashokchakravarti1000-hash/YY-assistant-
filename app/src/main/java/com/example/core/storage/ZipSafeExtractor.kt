package com.example.core.storage

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class ClassifiedModelType(val label: String, val dbType: String) {
    CHAT_MODEL("Chat LLM (GGUF)", "chat"),
    VISION_MODEL("Vision LLM + mmproj", "vision"),
    STT_WHISPER("Whisper STT", "stt"),
    TTS_VOICE("TTS Voice Pack", "tts"),
    VAD_MODEL("Silero VAD", "vad"),
    LORA_ADAPTER("LoRA Adapter", "lora"),
    BUNDLE("Model Bundle", "chat"),
    UNSUPPORTED("Unsupported", "chat")
}

data class LocalImportPreview(
    val sourceUri: Uri,
    val fileDetection: FileDetectionResult,
    val classifiedType: ClassifiedModelType,
    val suggestedName: String,
    val entries: List<String>,
    val estimatedUncompressedBytes: Long,
    val ggufMetadata: GgufMetadata? = null,
    val hasMmproj: Boolean = false,
    val splitPartsCount: Int = 0,
    val hasEnoughDiskSpace: Boolean = true,
    val fitsRamBudget: Boolean = true,
    val safetyError: String? = null
)

data class ExtractedModelResult(
    val modelId: String,
    val primaryFilePath: String,
    val mmprojPath: String? = null,
    val totalSizeBytes: Long,
    val sha256: String,
    val ggufMetadata: GgufMetadata? = null
)

/**
 * Section 8.1: Safe Local File & ZIP Importer with:
 * - Step 0 magic-byte verification
 * - ZIP-slip path traversal rejection
 * - Max 50 files & 100:1 compression-ratio ZIP-bomb caps
 * - 1.2x free disk space check
 * - GGUF header metadata inspection
 * - Atomic extraction (.tmp -> rename) + SHA-256 hashing
 * - Export custom model back to ZIP
 */
class ZipSafeExtractor(
    private val context: Context,
    private val fileTypeDetector: FileTypeDetector,
    private val ggufInspector: GgufInspector
) {
    companion object {
        private const val MAX_ARCHIVE_FILES = 50
        private const val MAX_COMPRESSION_RATIO = 100.0
        private const val DISK_SAFETY_MULTIPLIER = 1.2
    }

    suspend fun inspectForPreview(
        uri: Uri,
        freeDiskBytes: Long,
        fitsRamCheck: (Long) -> Boolean
    ): LocalImportPreview = withContext(Dispatchers.IO) {
        val detection = fileTypeDetector.detect(uri)
        val cleanBaseName = detection.displayName
            .substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .ifBlank { "Custom Model" }

        if (detection.kind.isError) {
            return@withContext LocalImportPreview(
                sourceUri = uri,
                fileDetection = detection,
                classifiedType = ClassifiedModelType.UNSUPPORTED,
                suggestedName = cleanBaseName,
                entries = emptyList(),
                estimatedUncompressedBytes = detection.sizeBytes,
                hasEnoughDiskSpace = true,
                fitsRamBudget = true,
                safetyError = detection.errorMessage ?: "Unsupported file format."
            )
        }

        when (detection.kind) {
            DetectedFileKind.SINGLE_GGUF -> {
                val meta = context.contentResolver.openInputStream(uri)?.use { ggufInspector.inspectStream(it) }
                val lower = detection.displayName.lowercase()
                val classified = when {
                    lower.contains("lora") -> ClassifiedModelType.LORA_ADAPTER
                    lower.contains("mmproj") -> ClassifiedModelType.VISION_MODEL
                    else -> ClassifiedModelType.CHAT_MODEL
                }
                val suggested = meta?.modelName?.takeIf { it.isNotBlank() } ?: cleanBaseName
                val enoughDisk = freeDiskBytes <= 0L || (detection.sizeBytes * DISK_SAFETY_MULTIPLIER) < freeDiskBytes
                LocalImportPreview(
                    sourceUri = uri,
                    fileDetection = detection,
                    classifiedType = classified,
                    suggestedName = suggested,
                    entries = listOf(detection.displayName),
                    estimatedUncompressedBytes = detection.sizeBytes,
                    ggufMetadata = meta,
                    hasEnoughDiskSpace = enoughDisk,
                    fitsRamBudget = fitsRamCheck(detection.sizeBytes),
                    safetyError = if (meta != null && !meta.isValid) meta.error else null
                )
            }

            DetectedFileKind.ZIP_ARCHIVE -> inspectZip(uri, detection, cleanBaseName, freeDiskBytes, fitsRamCheck)

            DetectedFileKind.WHISPER_GGML -> {
                val enoughDisk = freeDiskBytes <= 0L || (detection.sizeBytes * DISK_SAFETY_MULTIPLIER) < freeDiskBytes
                LocalImportPreview(
                    sourceUri = uri,
                    fileDetection = detection,
                    classifiedType = ClassifiedModelType.STT_WHISPER,
                    suggestedName = cleanBaseName,
                    entries = listOf(detection.displayName),
                    estimatedUncompressedBytes = detection.sizeBytes,
                    hasEnoughDiskSpace = enoughDisk,
                    fitsRamBudget = true
                )
            }

            DetectedFileKind.ONNX_MODEL -> {
                val lower = detection.displayName.lowercase()
                val type = if (lower.contains("vad") || lower.contains("silero")) {
                    ClassifiedModelType.VAD_MODEL
                } else {
                    ClassifiedModelType.TTS_VOICE
                }
                val enoughDisk = freeDiskBytes <= 0L || (detection.sizeBytes * DISK_SAFETY_MULTIPLIER) < freeDiskBytes
                LocalImportPreview(
                    sourceUri = uri,
                    fileDetection = detection,
                    classifiedType = type,
                    suggestedName = cleanBaseName,
                    entries = listOf(detection.displayName),
                    estimatedUncompressedBytes = detection.sizeBytes,
                    hasEnoughDiskSpace = enoughDisk,
                    fitsRamBudget = true
                )
            }

            DetectedFileKind.TAR_BZ2, DetectedFileKind.TAR_GZ -> {
                val estimated = (detection.sizeBytes * 1.4).toLong()
                val enoughDisk = freeDiskBytes <= 0L || (estimated * DISK_SAFETY_MULTIPLIER) < freeDiskBytes
                LocalImportPreview(
                    sourceUri = uri,
                    fileDetection = detection,
                    classifiedType = ClassifiedModelType.TTS_VOICE,
                    suggestedName = cleanBaseName,
                    entries = listOf(detection.displayName),
                    estimatedUncompressedBytes = estimated,
                    hasEnoughDiskSpace = enoughDisk,
                    fitsRamBudget = true
                )
            }

            else -> LocalImportPreview(
                sourceUri = uri,
                fileDetection = detection,
                classifiedType = ClassifiedModelType.UNSUPPORTED,
                suggestedName = cleanBaseName,
                entries = emptyList(),
                estimatedUncompressedBytes = detection.sizeBytes,
                safetyError = detection.errorMessage ?: "Unsupported file."
            )
        }
    }

    private fun inspectZip(
        uri: Uri,
        detection: FileDetectionResult,
        cleanBaseName: String,
        freeDiskBytes: Long,
        fitsRamCheck: (Long) -> Boolean
    ): LocalImportPreview {
        val entryNames = mutableListOf<String>()
        var totalUncompressed = 0L
        var ggufMeta: GgufMetadata? = null
        var hasMmproj = false
        var splitCount = 0

        try {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        // ZIP-slip guard
                        if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                            return LocalImportPreview(
                                sourceUri = uri,
                                fileDetection = detection,
                                classifiedType = ClassifiedModelType.UNSUPPORTED,
                                suggestedName = cleanBaseName,
                                entries = entryNames,
                                estimatedUncompressedBytes = totalUncompressed,
                                safetyError = "Security Block (ZIP-Slip): Archive entry '$name' attempts path traversal."
                            )
                        }

                        if (!entry.isDirectory) {
                            entryNames.add(name)
                            if (entryNames.size > MAX_ARCHIVE_FILES) {
                                return LocalImportPreview(
                                    sourceUri = uri,
                                    fileDetection = detection,
                                    classifiedType = ClassifiedModelType.UNSUPPORTED,
                                    suggestedName = cleanBaseName,
                                    entries = entryNames,
                                    estimatedUncompressedBytes = totalUncompressed,
                                    safetyError = "Security Block: Archive exceeds maximum limit of $MAX_ARCHIVE_FILES files."
                                )
                            }

                            val uncompressed = entry.size.coerceAtLeast(0L)
                            val compressed = entry.compressedSize.coerceAtLeast(1L)
                            if (entry.size > 0 && entry.compressedSize > 0 && (uncompressed.toDouble() / compressed) > MAX_COMPRESSION_RATIO) {
                                return LocalImportPreview(
                                    sourceUri = uri,
                                    fileDetection = detection,
                                    classifiedType = ClassifiedModelType.UNSUPPORTED,
                                    suggestedName = cleanBaseName,
                                    entries = entryNames,
                                    estimatedUncompressedBytes = totalUncompressed,
                                    safetyError = "Security Block (ZIP Bomb): Abnormally high compression ratio detected on '$name'."
                                )
                            }
                            totalUncompressed += uncompressed

                            val lower = name.lowercase()
                            if (lower.contains("mmproj") && lower.endsWith(".gguf")) hasMmproj = true
                            if (Regex("-\\d{5}-of-\\d{5}\\.gguf$").containsMatchIn(lower)) splitCount++
                            if (ggufMeta == null && lower.endsWith(".gguf") && !lower.contains("mmproj")) {
                                ggufMeta = ggufInspector.inspectStream(NonClosingInputStream(zis))
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            return LocalImportPreview(
                sourceUri = uri,
                fileDetection = detection,
                classifiedType = ClassifiedModelType.UNSUPPORTED,
                suggestedName = cleanBaseName,
                entries = entryNames,
                estimatedUncompressedBytes = totalUncompressed,
                safetyError = "Corrupt or unreadable ZIP archive: ${e.message}"
            )
        }

        if (totalUncompressed <= 0L) totalUncompressed = detection.sizeBytes

        val classified = classifyArchiveEntries(entryNames, hasMmproj)
        val enoughDisk = freeDiskBytes <= 0L || (totalUncompressed * DISK_SAFETY_MULTIPLIER) < freeDiskBytes
        val suggested = ggufMeta?.modelName?.takeIf { it.isNotBlank() } ?: cleanBaseName

        return LocalImportPreview(
            sourceUri = uri,
            fileDetection = detection,
            classifiedType = classified,
            suggestedName = suggested,
            entries = entryNames,
            estimatedUncompressedBytes = totalUncompressed,
            ggufMetadata = ggufMeta,
            hasMmproj = hasMmproj,
            splitPartsCount = splitCount,
            hasEnoughDiskSpace = enoughDisk,
            fitsRamBudget = if (classified == ClassifiedModelType.CHAT_MODEL || classified == ClassifiedModelType.VISION_MODEL) {
                fitsRamCheck(totalUncompressed)
            } else true,
            safetyError = if (entryNames.isEmpty()) "Archive contains no model files." else null
        )
    }

    suspend fun importModel(
        preview: LocalImportPreview,
        onProgress: (Float) -> Unit
    ): Result<ExtractedModelResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(preview.safetyError == null) { preview.safetyError ?: "Import rejected." }

            val modelId = "custom-" + UUID.randomUUID().toString().take(8)
            val isVoice = preview.classifiedType in setOf(
                ClassifiedModelType.STT_WHISPER,
                ClassifiedModelType.TTS_VOICE,
                ClassifiedModelType.VAD_MODEL
            )
            val rootDir = File(context.filesDir, if (isVoice) "voices" else "models").apply { mkdirs() }
            val tmpDir = File(rootDir, "$modelId.tmp").apply {
                deleteRecursively()
                mkdirs()
            }
            val finalDir = File(rootDir, modelId)

            val digest = MessageDigest.getInstance("SHA-256")
            var totalWritten = 0L
            var primaryFile: File? = null
            var mmprojFile: File? = null
            val expectedTotal = preview.estimatedUncompressedBytes.coerceAtLeast(1L)

            try {
                if (preview.fileDetection.kind == DetectedFileKind.ZIP_ARCHIVE) {
                    context.contentResolver.openInputStream(preview.sourceUri)?.use { raw ->
                        ZipInputStream(BufferedInputStream(raw)).use { zis ->
                            val buf = ByteArray(32 * 1024)
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory) {
                                    val safeName = entry.name.replace('\\', '/')
                                    val outFile = File(tmpDir, safeName)
                                    val canonicalTmp = tmpDir.canonicalPath
                                    require(outFile.canonicalPath.startsWith(canonicalTmp)) {
                                        "ZIP-slip blocked during extraction."
                                    }
                                    outFile.parentFile?.mkdirs()
                                    FileOutputStream(outFile).use { fos ->
                                        var n = zis.read(buf)
                                        while (n > 0) {
                                            fos.write(buf, 0, n)
                                            digest.update(buf, 0, n)
                                            totalWritten += n
                                            onProgress((totalWritten.toFloat() / expectedTotal).coerceIn(0.05f, 0.96f))
                                            n = zis.read(buf)
                                        }
                                    }
                                    val lower = outFile.name.lowercase()
                                    if (lower.contains("mmproj") && lower.endsWith(".gguf")) {
                                        mmprojFile = outFile
                                    } else if (primaryFile == null && (lower.endsWith(".gguf") || lower.endsWith(".onnx") || lower.endsWith(".bin"))) {
                                        primaryFile = outFile
                                    }
                                }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }
                    } ?: error("Failed to read source archive.")
                } else {
                    val safeName = preview.fileDetection.displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val outFile = File(tmpDir, safeName)
                    context.contentResolver.openInputStream(preview.sourceUri)?.use { input ->
                        FileOutputStream(outFile).use { fos ->
                            val buf = ByteArray(32 * 1024)
                            var n = input.read(buf)
                            while (n > 0) {
                                fos.write(buf, 0, n)
                                digest.update(buf, 0, n)
                                totalWritten += n
                                onProgress((totalWritten.toFloat() / expectedTotal).coerceIn(0.05f, 0.96f))
                                n = input.read(buf)
                            }
                        }
                    } ?: error("Failed to read local file.")
                    primaryFile = outFile
                }

                // Atomic move into final directory
                if (finalDir.exists()) finalDir.deleteRecursively()
                check(tmpDir.renameTo(finalDir)) { "Failed to atomically commit imported model directory." }

                val resolvedPrimary = primaryFile?.let {
                    File(finalDir, it.relativeTo(tmpDir).path)
                } ?: finalDir.walkTopDown().firstOrNull { it.isFile }
                    ?: error("No extracted model file found.")

                val resolvedMmproj = mmprojFile?.let { File(finalDir, it.relativeTo(tmpDir).path).absolutePath }
                val meta = if (resolvedPrimary.extension.equals("gguf", ignoreCase = true)) {
                    ggufInspector.inspectFile(resolvedPrimary)
                } else preview.ggufMetadata

                val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                onProgress(1f)

                ExtractedModelResult(
                    modelId = modelId,
                    primaryFilePath = resolvedPrimary.absolutePath,
                    mmprojPath = resolvedMmproj,
                    totalSizeBytes = totalWritten,
                    sha256 = sha256,
                    ggufMetadata = meta
                )
            } catch (e: Exception) {
                tmpDir.deleteRecursively()
                throw e
            }
        }
    }

    suspend fun exportModelToZip(modelPath: String, destUri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val source = File(modelPath)
            require(source.exists()) { "Model file does not exist on disk." }
            val parentDir = source.parentFile
            val filesToPack = if (parentDir != null && (parentDir.path.contains("/models/") || parentDir.path.contains("/voices/"))) {
                parentDir.walkTopDown().filter { it.isFile }.toList()
            } else {
                listOf(source)
            }

            var bytesWritten = 0L
            context.contentResolver.openOutputStream(destUri)?.use { raw ->
                ZipOutputStream(BufferedOutputStream(raw)).use { zos ->
                    val buf = ByteArray(32 * 1024)
                    filesToPack.forEach { file ->
                        val entryName = if (parentDir != null) file.relativeTo(parentDir).path else file.name
                        zos.putNextEntry(ZipEntry(entryName))
                        FileInputStream(file).use { fis ->
                            var n = fis.read(buf)
                            while (n > 0) {
                                zos.write(buf, 0, n)
                                bytesWritten += n
                                n = fis.read(buf)
                            }
                        }
                        zos.closeEntry()
                    }
                }
            } ?: error("Cannot open ZIP export destination.")
            bytesWritten
        }
    }

    private fun classifyArchiveEntries(entries: List<String>, hasMmproj: Boolean): ClassifiedModelType {
        val lowers = entries.map { it.lowercase() }
        return when {
            lowers.any { it.contains("lora") && it.endsWith(".gguf") } -> ClassifiedModelType.LORA_ADAPTER
            hasMmproj -> ClassifiedModelType.VISION_MODEL
            lowers.any { it.endsWith(".gguf") } -> ClassifiedModelType.CHAT_MODEL
            lowers.any { it.contains("silero") || it.contains("vad") } -> ClassifiedModelType.VAD_MODEL
            lowers.any { it.contains("whisper") || it.endsWith(".bin") } -> ClassifiedModelType.STT_WHISPER
            lowers.any { it.endsWith(".onnx") || it.contains("tokens.txt") } -> ClassifiedModelType.TTS_VOICE
            else -> ClassifiedModelType.BUNDLE
        }
    }

    private class NonClosingInputStream(private val delegate: ZipInputStream) : java.io.InputStream() {
        override fun read(): Int = delegate.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
        override fun close() { /* Keep ZipInputStream open for subsequent entries */ }
    }
}
