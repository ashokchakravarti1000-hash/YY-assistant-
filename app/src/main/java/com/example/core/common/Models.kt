package com.example.core.common

import java.util.Locale

enum class DeviceTier(
    val level: Int,
    val label: String,
    val badge: String,
    val defaultContext: Int,
    val topK: Int,
    val ramGuide: String,
    val speedGuide: String,
    val targetUse: String
) {
    TIER_1_LITE(1, "Tier 1 — Lite", "T1 LITE", 4096, 4, "8 GB RAM · 128 GB", "10–25 tok/s", "Quick chat, summaries, voice replies"),
    TIER_2_BALANCED(2, "Tier 2 — Balanced", "T2 BALANCED", 8192, 8, "12 GB RAM · 256 GB", "6–15 tok/s", "Reasoning, vision, document Q&A"),
    TIER_3_PRO(3, "Tier 3 — Pro", "T3 PRO", 16384, 12, "12–16 GB+ RAM · 1 TB", "2–8 tok/s", "Deep reasoning, long documents, coding");

    companion object {
        fun fromLevel(level: Int): DeviceTier = when {
            level <= 1 -> TIER_1_LITE
            level == 2 -> TIER_2_BALANCED
            else -> TIER_3_PRO
        }
    }
}

enum class ThermalLevel(val label: String, val severity: Int) {
    NONE("Cool", 0),
    LIGHT("Warm", 1),
    MODERATE("Moderate · Throttling", 2),
    SEVERE("Severe · Paused", 3),
    CRITICAL("Critical · Halted", 4)
}

enum class PerformanceProfile(
    val label: String,
    val subtitle: String,
    val threadFraction: Float,
    val maxContextCap: Int,
    val voiceSessionHintMinutes: Int
) {
    BATTERY("Battery", "Smaller model hints · 4K cap · Cooler voice", 0.5f, 4096, 10),
    BALANCED("Balanced", "Big+Prime cores · Adaptive thermal guard", 0.75f, 8192, 25),
    MAX("Max", "All performance cores · Full context window", 1.0f, 32768, 45)
}

enum class AcceleratorBackend(val label: String, val detail: String) {
    CPU_NEON("CPU (NEON / i8mm)", "Always-on arm64 SIMD fallback"),
    GPU_VULKAN("GPU (Vulkan)", "Shaders for prompt prefill & matmul"),
    NPU_QNN("NPU (QNN / NeuroPilot / LiteRT)", "Optional neural backend on flagship SoCs")
}

enum class SamplingPreset(
    val label: String,
    val temperature: Float,
    val topP: Float,
    val minP: Float,
    val repeatPenalty: Float
) {
    PRECISE("Precise", 0.2f, 0.85f, 0.05f, 1.12f),
    BALANCED("Balanced", 0.65f, 0.92f, 0.05f, 1.10f),
    CREATIVE("Creative", 0.95f, 0.96f, 0.03f, 1.06f)
}

enum class CompatibilityStatus {
    COMPATIBLE, WARNING, BLOCKED
}

data class HardwareProfile(
    val totalRamBytes: Long,
    val availRamBytes: Long,
    val memoryBudgetBytes: Long,
    val totalStorageBytes: Long,
    val freeStorageBytes: Long,
    val socModel: String,
    val abi: String,
    val isArm64: Boolean,
    val cpuCores: Int,
    val recommendedThreads: Int,
    val thermalLevel: ThermalLevel,
    val ramTier: DeviceTier,
    val storageTier: DeviceTier,
    val benchmarkTier: DeviceTier,
    val detectedTier: DeviceTier,
    val effectiveTier: DeviceTier,
    val manualOverrideTier: DeviceTier? = null,
    val benchmarkTokPerSec: Float = 0f,
    val benchmarkLatencyMs: Long = 0L,
    val benchmarkCompleted: Boolean = false
) {
    val totalRamGb: Double get() = totalRamBytes / 1_073_741_824.0
    val availRamGb: Double get() = availRamBytes / 1_073_741_824.0
    val memoryBudgetGb: Double get() = memoryBudgetBytes / 1_073_741_824.0
    val freeStorageGb: Double get() = freeStorageBytes / 1_073_741_824.0
    val totalStorageGb: Double get() = totalStorageBytes / 1_073_741_824.0
}

data class StorageBreakdown(
    val modelsBytes: Long = 0L,
    val documentsBytes: Long = 0L,
    val voicesBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val totalBytes: Long = 1L
) {
    val appTotalBytes: Long get() = modelsBytes + documentsBytes + voicesBytes + cacheBytes
}

data class CatalogModel(
    val id: String,
    val displayName: String,
    val tier: Int,
    val params: String,
    val minRamGb: Int,
    val requires16GbGate: Boolean,
    val sizeBytes: Long,
    val projectorSizeBytes: Long,
    val sha256: String,
    val format: String,
    val quant: String,
    val capabilities: List<String>,
    val contextDefault: Int,
    val contextMax: Int,
    val license: String,
    val repo: String,
    val sources: List<String>,
    val chatTemplate: String,
    val supportsThinking: Boolean,
    val isDraftCandidate: Boolean,
    val strengths: String
) {
    val totalBundleBytes: Long get() = sizeBytes + projectorSizeBytes
    val isVisionCapable: Boolean get() = capabilities.any { it.equals("vision", ignoreCase = true) }
}

data class VoiceCatalogItem(
    val id: String,
    val displayName: String,
    val category: String,
    val engine: String,
    val tier: Int,
    val sizeBytes: Long,
    val language: String,
    val license: String,
    val sourceUrl: String,
    val description: String,
    val genderPresentation: String = "",
    val pitchRange: String = "",
    val warmthTags: String = ""
)

data class DownloadProgress(
    val itemId: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val status: DownloadStatus,
    val speedBytesPerSec: Long = 0L,
    val errorMessage: String? = null
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

enum class DownloadStatus {
    QUEUED, DOWNLOADING, PAUSED, VERIFYING, COMPLETED, FAILED
}

data class SourceCitation(
    val documentId: String,
    val documentName: String,
    val heading: String,
    val page: Int,
    val snippet: String,
    val score: Float
)

data class PersonaPreset(
    val id: String,
    val title: String,
    val category: String,
    val systemPrompt: String
)

data class ToolApprovalRequest(
    val id: String,
    val toolName: String,
    val description: String,
    val arguments: String,
    val conversationId: String,
    val originalPrompt: String
)

data class BenchmarkRunResult(
    val modelName: String,
    val tokensPerSec: Float,
    val ttftMs: Long,
    val peakRamMb: Long,
    val thermalStart: ThermalLevel,
    val thermalEnd: ThermalLevel,
    val backend: AcceleratorBackend,
    val threadsUsed: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class EvalCaseResult(
    val name: String,
    val prompt: String,
    val passed: Boolean,
    val latencyMs: Long,
    val detail: String
)

enum class VoiceState {
    IDLE, LISTENING, THINKING, SPEAKING
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}
