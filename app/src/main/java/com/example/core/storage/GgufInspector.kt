package com.example.core.storage

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GgufMetadata(
    val isValid: Boolean,
    val version: Int = 0,
    val tensorCount: Long = 0L,
    val kvCount: Long = 0L,
    val architecture: String = "unknown",
    val modelName: String = "",
    val quantization: String = "Q4_K_M",
    val contextLength: Int = 4096,
    val chatTemplate: String = "chatml",
    val isSupportedArchitecture: Boolean = true,
    val error: String? = null
)

/**
 * Section 8.1: Binary GGUF v2/v3 header & KV metadata inspector.
 * Safely reads magic, version, tensor/KV counts, architecture, quantization, context length,
 * and chat template without loading weight tensors into RAM.
 */
class GgufInspector {

    companion object {
        private val SUPPORTED_ARCHS = setOf(
            "llama", "qwen2", "qwen3", "qwen3moe", "gemma", "gemma2", "gemma3", "gemma3n",
            "phi3", "phi4", "mistral", "mistral3", "deepseek2", "smollm", "clip", "unknown"
        )
        private const val MAX_KV_SCAN = 128
        private const val MAX_STRING_LEN = 32_768
    }

    fun inspectFile(file: File): GgufMetadata = runCatching {
        FileInputStream(file).use { fis -> inspectStream(BufferedInputStream(fis)) }
    }.getOrElse { err ->
        GgufMetadata(isValid = false, error = "Failed to read GGUF file: ${err.message}")
    }

    fun inspectStream(rawStream: InputStream): GgufMetadata {
        return try {
            val stream = if (rawStream is BufferedInputStream) rawStream else BufferedInputStream(rawStream)
            val magic = readExact(stream, 4)
            if (String(magic, Charsets.US_ASCII) != "GGUF") {
                return GgufMetadata(isValid = false, error = "Invalid GGUF magic header.")
            }

            val version = readUInt32LE(stream).toInt()
            if (version !in 2..3) {
                return GgufMetadata(isValid = false, version = version, error = "Unsupported GGUF version v$version (expected v2 or v3).")
            }

            val tensorCount = readInt64LE(stream)
            val kvCount = readInt64LE(stream)

            var architecture = "unknown"
            var modelName = ""
            var quantization = "Q4_K_M"
            var contextLength = 4096
            var chatTemplate = "chatml"

            val toScan = minOf(kvCount.coerceAtLeast(0L), MAX_KV_SCAN.toLong()).toInt()
            for (i in 0 until toScan) {
                val key = readGgufString(stream) ?: break
                val valueType = readUInt32LE(stream).toInt()
                val parsed = readGgufValue(stream, valueType, captureString = key == "general.architecture" || key == "general.name" || key == "tokenizer.chat_template")
                when {
                    key == "general.architecture" && parsed is String -> architecture = parsed.lowercase()
                    key == "general.name" && parsed is String -> modelName = parsed
                    key == "general.file_type" && parsed is Long -> quantization = mapFileTypeToQuant(parsed.toInt())
                    key.endsWith(".context_length") && parsed is Long -> {
                        val ctx = parsed.toInt()
                        if (ctx in 512..1_048_576) contextLength = ctx
                    }
                    key == "tokenizer.chat_template" && parsed is String -> {
                        chatTemplate = detectTemplateName(parsed, architecture)
                    }
                }
            }

            if (chatTemplate == "chatml") {
                chatTemplate = inferTemplateFromArch(architecture)
            }

            val supported = architecture in SUPPORTED_ARCHS
            GgufMetadata(
                isValid = true,
                version = version,
                tensorCount = tensorCount,
                kvCount = kvCount,
                architecture = architecture,
                modelName = modelName,
                quantization = quantization,
                contextLength = contextLength,
                chatTemplate = chatTemplate,
                isSupportedArchitecture = supported,
                error = if (!supported) "Architecture '$architecture' may not be supported by this build." else null
            )
        } catch (e: EOFException) {
            GgufMetadata(isValid = false, error = "Truncated GGUF header (unexpected end of file).")
        } catch (e: Exception) {
            GgufMetadata(isValid = false, error = "GGUF parse error: ${e.message}")
        }
    }

    private fun readGgufString(stream: InputStream): String? {
        val len = readInt64LE(stream)
        if (len < 0 || len > MAX_STRING_LEN) return null
        val bytes = readExact(stream, len.toInt())
        return String(bytes, Charsets.UTF_8)
    }

    private fun readGgufValue(stream: InputStream, type: Int, captureString: Boolean): Any? {
        return when (type) {
            0, 1, 7 -> readExact(stream, 1)[0].toLong() and 0xFFL // UINT8, INT8, BOOL
            2, 3 -> ByteBuffer.wrap(readExact(stream, 2)).order(ByteOrder.LITTLE_ENDIAN).short.toLong()
            4, 5 -> readUInt32LE(stream)
            6 -> ByteBuffer.wrap(readExact(stream, 4)).order(ByteOrder.LITTLE_ENDIAN).float
            8 -> {
                val len = readInt64LE(stream)
                if (len < 0) return null
                if (captureString && len <= MAX_STRING_LEN) {
                    String(readExact(stream, len.toInt()), Charsets.UTF_8)
                } else {
                    skipFully(stream, len)
                    null
                }
            }
            9 -> { // ARRAY
                val elemType = readUInt32LE(stream).toInt()
                val count = readInt64LE(stream)
                val fixedSize = fixedTypeSize(elemType)
                if (fixedSize > 0 && count >= 0) {
                    skipFully(stream, fixedSize * count)
                } else {
                    val safeCount = minOf(count.coerceAtLeast(0L), 4096L).toInt()
                    for (j in 0 until safeCount) {
                        readGgufValue(stream, elemType, captureString = false)
                    }
                }
                null
            }
            10, 11 -> readInt64LE(stream)
            12 -> ByteBuffer.wrap(readExact(stream, 8)).order(ByteOrder.LITTLE_ENDIAN).double
            else -> null
        }
    }

    private fun fixedTypeSize(type: Int): Long = when (type) {
        0, 1, 7 -> 1L
        2, 3 -> 2L
        4, 5, 6 -> 4L
        10, 11, 12 -> 8L
        else -> -1L
    }

    private fun readUInt32LE(stream: InputStream): Long {
        val b = readExact(stream, 4)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
    }

    private fun readInt64LE(stream: InputStream): Long {
        val b = readExact(stream, 8)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun readExact(stream: InputStream, length: Int): ByteArray {
        val out = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val n = stream.read(out, offset, length - offset)
            if (n < 0) throw EOFException("Unexpected EOF while reading $length bytes")
            offset += n
        }
        return out
    }

    private fun skipFully(stream: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val buf = ByteArray(4096)
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                val read = stream.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                if (read < 0) throw EOFException("Unexpected EOF while skipping")
                remaining -= read
            }
        }
    }

    private fun mapFileTypeToQuant(ft: Int): String = when (ft) {
        0 -> "F32"
        1 -> "F16"
        2 -> "Q4_0"
        7 -> "Q8_0"
        10 -> "Q2_K"
        12 -> "Q3_K_M"
        14 -> "Q4_K_S"
        15 -> "Q4_K_M"
        16 -> "Q5_K_S"
        17 -> "Q5_K_M"
        18 -> "Q6_K"
        else -> "Q4_K_M"
    }

    private fun detectTemplateName(template: String, arch: String): String = when {
        template.contains("<|im_start|>") -> "chatml"
        template.contains("<|start_header_id|>") -> "llama3"
        template.contains("<start_of_turn>") -> "gemma"
        template.contains("[INST]") -> "mistral"
        else -> inferTemplateFromArch(arch)
    }

    private fun inferTemplateFromArch(arch: String): String = when {
        arch.contains("gemma") -> "gemma"
        arch.contains("llama") -> "llama3"
        arch.contains("mistral") -> "mistral"
        else -> "chatml"
    }
}
