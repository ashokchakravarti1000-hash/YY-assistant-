package com.example.core.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import com.example.core.common.CatalogModel
import com.example.core.common.CompatibilityStatus
import com.example.core.common.DeviceTier
import com.example.core.common.HardwareProfile
import com.example.core.common.StorageBreakdown
import com.example.core.common.ThermalLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class DeviceProfiler(private val context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val _profile = MutableStateFlow(readSnapshot())
    val profile: StateFlow<HardwareProfile> = _profile.asStateFlow()

    private val _storage = MutableStateFlow(readStorageBreakdown())
    val storage: StateFlow<StorageBreakdown> = _storage.asStateFlow()

    private var thermalListenerRegistered = false
    private val thermalListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        PowerManager.OnThermalStatusChangedListener { status ->
            _profile.value = _profile.value.copy(thermalLevel = mapThermalStatus(status))
        }
    } else null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalListener?.let { listener ->
                runCatching {
                    powerManager.addThermalStatusListener(listener)
                    thermalListenerRegistered = true
                }
            }
        }
    }

    fun unregister() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListenerRegistered) {
            thermalListener?.let { listener ->
                runCatching {
                    powerManager.removeThermalStatusListener(listener)
                    thermalListenerRegistered = false
                }
            }
        }
    }

    fun refresh() {
        val cur = _profile.value
        _profile.value = readSnapshot(cur.benchmarkTokPerSec, cur.benchmarkLatencyMs, cur.benchmarkCompleted, cur.manualOverrideTier)
        _storage.value = readStorageBreakdown()
    }

    fun setManualTierOverride(tier: DeviceTier?) {
        val cur = _profile.value
        _profile.value = cur.copy(manualOverrideTier = tier, effectiveTier = tier ?: cur.detectedTier)
    }

    suspend fun runMicroBenchmark(durationMs: Long = 1600L): HardwareProfile = withContext(Dispatchers.Default) {
        val dim = 512
        val rows = 256
        val weights = ByteArray(rows * dim) { ((it * 31 + 17) % 127).toByte() }
        val input = FloatArray(dim) { (it % 19) * 0.05f }
        val output = FloatArray(rows)

        val startNs = System.nanoTime()
        var steps = 0
        val deadlineNs = startNs + durationMs * 1_000_000L
        while (System.nanoTime() < deadlineNs) {
            for (r in 0 until rows) {
                var acc = 0f
                val base = r * dim
                var c = 0
                while (c < dim) {
                    acc += weights[base + c] * input[c]
                    acc += weights[base + c + 1] * input[c + 1]
                    acc += weights[base + c + 2] * input[c + 2]
                    acc += weights[base + c + 3] * input[c + 3]
                    c += 4
                }
                output[r] = acc
            }
            steps++
        }

        val elapsedMs = max(1L, (System.nanoTime() - startNs) / 1_000_000L)
        val opsPerSec = (steps.toDouble() * rows * dim) / (elapsedMs / 1000.0)
        val estimatedTokPerSec = (opsPerSec / 6_500_000.0).toFloat().coerceIn(3.5f, 48.0f)
        val ttftMs = max(45L, (820.0 / sqrt(estimatedTokPerSec.toDouble())).toLong())

        val cur = _profile.value
        val updated = readSnapshot(estimatedTokPerSec, ttftMs, true, cur.manualOverrideTier)
        _profile.value = updated
        updated
    }

    fun checkModelCompatibility(model: CatalogModel, profile: HardwareProfile = _profile.value): CompatibilityStatus {
        val totalRamGb = profile.totalRamGb
        val estimatedFootprintBytes = (model.totalBundleBytes * 1.18).toLong() + 320_000_000L
        if (model.requires16GbGate && totalRamGb < 14.2) return CompatibilityStatus.BLOCKED
        if (estimatedFootprintBytes > profile.memoryBudgetBytes) {
            return if (model.tier <= profile.effectiveTier.level + 1 && totalRamGb >= model.minRamGb - 2) {
                CompatibilityStatus.WARNING
            } else CompatibilityStatus.BLOCKED
        }
        return when {
            model.tier <= profile.effectiveTier.level -> CompatibilityStatus.COMPATIBLE
            model.tier == profile.effectiveTier.level + 1 -> CompatibilityStatus.WARNING
            else -> CompatibilityStatus.BLOCKED
        }
    }

    fun checkCustomModelFitsMemory(sizeBytes: Long, contextTokens: Int = 4096): Boolean =
        (sizeBytes + contextTokens.toLong() * 22_000L + 250_000_000L) <= _profile.value.memoryBudgetBytes

    fun readStorageBreakdown(): StorageBreakdown {
        val filesDir = context.filesDir
        val statFs = StatFs(filesDir.absolutePath)
        return StorageBreakdown(
            modelsBytes = dirSize(File(filesDir, "models")),
            documentsBytes = dirSize(File(filesDir, "documents")),
            voicesBytes = dirSize(File(filesDir, "voices")),
            cacheBytes = dirSize(context.cacheDir),
            freeBytes = statFs.availableBytes,
            totalBytes = max(1L, statFs.totalBytes)
        )
    }

    fun clearAppCache(): Long {
        val before = dirSize(context.cacheDir)
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        _storage.value = readStorageBreakdown()
        return before
    }

    private fun readSnapshot(
        benchmarkTokPerSec: Float = 0f,
        benchmarkLatencyMs: Long = 0L,
        benchmarkCompleted: Boolean = false,
        manualOverride: DeviceTier? = null
    ): HardwareProfile {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalRamBytes = memInfo.totalMem
        val availRamBytes = memInfo.availMem
        val memoryBudgetBytes = (totalRamBytes * 0.60).toLong()

        val statFs = StatFs(context.filesDir.absolutePath)
        val totalStorageBytes = statFs.totalBytes
        val freeStorageBytes = statFs.availableBytes

        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() && it != "unknown" } ?: Build.HARDWARE
        } else Build.HARDWARE

        val abis = Build.SUPPORTED_ABIS.toList()
        val isArm64 = abis.any { it.contains("arm64-v8a", ignoreCase = true) }
        val primaryAbi = abis.firstOrNull() ?: "unknown"
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val recommendedThreads = when {
            cpuCores >= 8 -> 6
            cpuCores >= 6 -> 4
            else -> max(2, cpuCores - 1)
        }
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mapThermalStatus(powerManager.currentThermalStatus)
        } else ThermalLevel.NONE

        val totalRamGb = totalRamBytes / 1_073_741_824.0
        val totalStorageGb = totalStorageBytes / 1_073_741_824.0

        val ramTier = when {
            totalRamGb >= 14.5 -> DeviceTier.TIER_3_PRO
            totalRamGb >= 10.5 -> DeviceTier.TIER_2_BALANCED
            else -> DeviceTier.TIER_1_LITE
        }
        val storageTier = when {
            totalStorageGb >= 450.0 -> DeviceTier.TIER_3_PRO
            totalStorageGb >= 180.0 -> DeviceTier.TIER_2_BALANCED
            else -> DeviceTier.TIER_1_LITE
        }
        val benchTier = if (!benchmarkCompleted) ramTier else when {
            benchmarkTokPerSec >= 20f -> DeviceTier.TIER_3_PRO
            benchmarkTokPerSec >= 11f -> DeviceTier.TIER_2_BALANCED
            else -> DeviceTier.TIER_1_LITE
        }
        val detected = DeviceTier.fromLevel(min(ramTier.level, min(storageTier.level, benchTier.level)))

        return HardwareProfile(
            totalRamBytes = totalRamBytes,
            availRamBytes = availRamBytes,
            memoryBudgetBytes = memoryBudgetBytes,
            totalStorageBytes = totalStorageBytes,
            freeStorageBytes = freeStorageBytes,
            socModel = socModel,
            abi = primaryAbi,
            isArm64 = isArm64,
            cpuCores = cpuCores,
            recommendedThreads = recommendedThreads,
            thermalLevel = thermal,
            ramTier = ramTier,
            storageTier = storageTier,
            benchmarkTier = benchTier,
            detectedTier = detected,
            effectiveTier = manualOverride ?: detected,
            manualOverrideTier = manualOverride,
            benchmarkTokPerSec = benchmarkTokPerSec,
            benchmarkLatencyMs = benchmarkLatencyMs,
            benchmarkCompleted = benchmarkCompleted
        )
    }

    private fun mapThermalStatus(status: Int): ThermalLevel = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> ThermalLevel.NONE
        PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.LIGHT
        PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
        PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
        else -> ThermalLevel.CRITICAL
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var total = 0L
        dir.walkTopDown().forEach { if (it.isFile) total += it.length() }
        return total
    }
}
