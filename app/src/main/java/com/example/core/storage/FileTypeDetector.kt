package com.example.core.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

enum class DetectedFileKind(val label: String, val isError: Boolean = false) {
    SINGLE_GGUF("GGUF Model Weights"),
    ZIP_ARCHIVE("ZIP Archive"),
    TAR_BZ2("TAR.BZ2 Archive"),
    TAR_GZ("TAR.GZ Archive"),
    WHISPER_GGML("Whisper GGML Weights"),
    ONNX_MODEL("ONNX Neural Model"),
    GIT_LFS_POINTER("Git LFS Pointer (Not Weights)", isError = true),
    HTML_PAGE("HTML Web Page (Download Error)", isError = true),
    UNKNOWN("Unrecognized File Format", isError = true)
}

data class FileDetectionResult(
    val kind: DetectedFileKind,
    val displayName: String,
    val sizeBytes: Long,
    val magicHex: String,
    val errorMessage: String? = null
)

/**
 * Section 8.1 Step 0: Magic-byte file detector that inspects the leading bytes
 * before trusting filenames or extensions. Rejects Git LFS pointers & HTML error pages.
 */
class FileTypeDetector(private val context: Context) {

    fun detect(uri: Uri): FileDetectionResult {
        val (name, size) = queryMeta(uri)
        val header = ByteArray(128)
        val readCount = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.read(header, 0, header.size)
            } ?: 0
        }.getOrDefault(0)

        if (readCount <= 0) {
            return FileDetectionResult(
                kind = DetectedFileKind.UNKNOWN,
                displayName = name,
                sizeBytes = size,
                magicHex = "",
                errorMessage = "File is empty or unreadable."
            )
        }

        val prefix = header.copyOf(readCount)
        val magicHex = prefix.take(8).joinToString(" ") { "%02X".format(it) }
        val textPreview = String(prefix, Charsets.UTF_8).trimStart()
        val lowerName = name.lowercase()

        // 1. Reject Git LFS pointer files (e.g. saved from GitHub web UI instead of raw file)
        if (textPreview.startsWith("version https://git-lfs.github.com/spec")) {
            return FileDetectionResult(
                kind = DetectedFileKind.GIT_LFS_POINTER,
                displayName = name,
                sizeBytes = size,
                magicHex = magicHex,
                errorMessage = "Rejected: This is a tiny Git LFS pointer stub, not actual model weights. Download the raw binary file instead."
            )
        }

        // 2. Reject HTML error/login pages masquerading as .gguf or .zip
        if (textPreview.startsWith("<!DOCTYPE", ignoreCase = true) ||
            textPreview.startsWith("<html", ignoreCase = true) ||
            textPreview.startsWith("<?xml", ignoreCase = true)
        ) {
            return FileDetectionResult(
                kind = DetectedFileKind.HTML_PAGE,
                displayName = name,
                sizeBytes = size,
                magicHex = magicHex,
                errorMessage = "Rejected: File contains an HTML/web page instead of model weights (likely a broken or gated download link)."
            )
        }

        // 3. GGUF magic: 'G' 'G' 'U' 'F' (47 47 55 46)
        if (readCount >= 4 &&
            prefix[0] == 0x47.toByte() &&
            prefix[1] == 0x47.toByte() &&
            prefix[2] == 0x55.toByte() &&
            prefix[3] == 0x46.toByte()
        ) {
            return FileDetectionResult(DetectedFileKind.SINGLE_GGUF, name, size, magicHex)
        }

        // 4. ZIP magic: 'P' 'K' 0x03 0x04
        if (readCount >= 4 &&
            prefix[0] == 0x50.toByte() &&
            prefix[1] == 0x4B.toByte() &&
            (prefix[2] == 0x03.toByte() || prefix[2] == 0x05.toByte()) &&
            (prefix[3] == 0x04.toByte() || prefix[3] == 0x06.toByte())
        ) {
            return FileDetectionResult(DetectedFileKind.ZIP_ARCHIVE, name, size, magicHex)
        }

        // 5. BZip2 (.tar.bz2): 'B' 'Z' 'h'
        if (readCount >= 3 &&
            prefix[0] == 0x42.toByte() &&
            prefix[1] == 0x5A.toByte() &&
            prefix[2] == 0x68.toByte()
        ) {
            return FileDetectionResult(DetectedFileKind.TAR_BZ2, name, size, magicHex)
        }

        // 6. GZip (.tar.gz): 0x1F 0x8B
        if (readCount >= 2 &&
            prefix[0] == 0x1F.toByte() &&
            prefix[1] == 0x8B.toByte()
        ) {
            return FileDetectionResult(DetectedFileKind.TAR_GZ, name, size, magicHex)
        }

        // 7. Whisper GGML magic: 'g' 'g' 'm' 'l' / 'g' 'g' 'j' 't' / 'g' 'g' 'm' 'f'
        if (readCount >= 4 &&
            ((prefix[0] == 0x67.toByte() && prefix[1] == 0x67.toByte() && prefix[2] == 0x6D.toByte()) ||
                (prefix[3] == 0x67.toByte() && prefix[2] == 0x67.toByte() && prefix[1] == 0x6D.toByte()))
        ) {
            return FileDetectionResult(DetectedFileKind.WHISPER_GGML, name, size, magicHex)
        }

        // 8. ONNX protobuf (starts with protobuf field tag 0x08 ir_version + extension .onnx)
        if (lowerName.endsWith(".onnx") && prefix[0] == 0x08.toByte()) {
            return FileDetectionResult(DetectedFileKind.ONNX_MODEL, name, size, magicHex)
        }

        return FileDetectionResult(
            kind = DetectedFileKind.UNKNOWN,
            displayName = name,
            sizeBytes = size,
            magicHex = magicHex,
            errorMessage = "Unsupported file signature ($magicHex). Choose a valid .gguf, .zip, .tar.gz, .tar.bz2, Whisper .bin, or .onnx model file."
        )
    }

    private fun queryMeta(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "local_model"
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst()) {
                    if (nameIdx >= 0) name = c.getString(nameIdx) ?: name
                    if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
                }
            }
        }
        return name to size
    }
}
