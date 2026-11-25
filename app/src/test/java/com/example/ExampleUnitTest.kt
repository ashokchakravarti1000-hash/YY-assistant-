package com.example

import com.example.core.common.AcceleratorBackend
import com.example.core.common.CatalogModel
import com.example.core.common.DeviceTier
import com.example.core.common.DownloadProgress
import com.example.core.common.DownloadStatus
import com.example.core.common.HardwareProfile
import com.example.core.common.PerformanceProfile
import com.example.core.common.SamplingPreset
import com.example.core.common.SourceCitation
import com.example.core.common.StorageBreakdown
import com.example.core.common.ThermalLevel
import com.example.core.common.formatBytes
import com.example.core.storage.GgufInspector
import com.example.core.storage.InstalledModelEntity
import com.example.engine.llm.GenerateRequest
import com.example.engine.llm.LlmConfig
import com.example.engine.llm.LoadResult
import com.example.engine.llm.OnDeviceLlmEngine
import com.example.engine.tools.LocalToolExecutor
import com.example.viewmodel.AppSettingsState
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ExampleUnitTest {

    private fun testHardware(
        tier: DeviceTier = DeviceTier.TIER_2_BALANCED,
        totalRamGb: Long = 12L,
        thermal: ThermalLevel = ThermalLevel.NONE
    ): HardwareProfile {
        val totalRam = totalRamGb * 1_073_741_824L
        return HardwareProfile(
            totalRamBytes = totalRam,
            availRamBytes = (totalRam * 0.7).toLong(),
            memoryBudgetBytes = (totalRam * 0.6).toLong(),
            totalStorageBytes = 256L * 1_073_741_824L,
            freeStorageBytes = 120L * 1_073_741_824L,
            socModel = "Test-SoC",
            abi = "arm64-v8a",
            isArm64 = true,
            cpuCores = 8,
            recommendedThreads = 6,
            thermalLevel = thermal,
            ramTier = tier,
            storageTier = tier,
            benchmarkTier = tier,
            detectedTier = tier,
            effectiveTier = tier
        )
    }

    @Test
    fun tierSelectionAndDataStructures_areValid() {
        assertEquals(DeviceTier.TIER_1_LITE, DeviceTier.fromLevel(1))
        assertEquals(DeviceTier.TIER_2_BALANCED, DeviceTier.fromLevel(2))
        assertEquals(DeviceTier.TIER_3_PRO, DeviceTier.fromLevel(3))
        assertEquals(4, DeviceTier.TIER_1_LITE.topK)
        assertEquals(8, DeviceTier.TIER_2_BALANCED.topK)
        assertEquals(12, DeviceTier.TIER_3_PRO.topK)

        // Default theme must be Light Mode
        val defaultSettings = AppSettingsState()
        assertEquals("LIGHT", defaultSettings.darkThemeOverride)
        assertFalse(defaultSettings.airgappedMode)

        // Formatting & storage breakdown
        assertEquals("0 B", formatBytes(0L))
        assertEquals("512 B", formatBytes(512L))
        assertEquals("1.5 MB", formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.00 GB", formatBytes(2L * 1_073_741_824L))

        val sb = StorageBreakdown(modelsBytes = 100L, documentsBytes = 50L, voicesBytes = 25L, cacheBytes = 10L)
        assertEquals(185L, sb.appTotalBytes)

        val dl = DownloadProgress("m1", downloadedBytes = 50L, totalBytes = 200L, status = DownloadStatus.DOWNLOADING)
        assertEquals(0.25f, dl.progressFraction, 0.001f)
    }

    @Test
    fun catalogJson_hasAll10Models_10VoicePacks_andValidHttpsLinks() {
        val catalogFile = listOf(
            File("src/main/assets/catalog.json"),
            File("app/src/main/assets/catalog.json")
        ).firstOrNull { it.exists() }
        assertNotNull("catalog.json must exist in assets", catalogFile)

        val text = catalogFile!!.readText()
        // Verify JSON structure with regex checks (no org.json Android stub dependency in pure JVM)
        val modelIds = Regex("\"id\":\\s*\"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toList()
        assertEquals("Expected 20 unique entries (10 models + 10 voice packs)", 20, modelIds.size)
        assertEquals("All catalog IDs must be unique", 20, modelIds.toSet().size)

        // Verify all download URLs are strictly HTTPS on allowed domains
        val urls = Regex("https?://[^\"\\s]+").findAll(text).map { it.value }.toList()
        assertTrue("Catalog must contain at least 20 download URLs", urls.size >= 20)
        val allowedHosts = listOf("https://huggingface.co/", "https://hf.co/", "https://github.com/")
        urls.forEach { url ->
            assertTrue("URL must use HTTPS on allowed host: $url", allowedHosts.any { url.startsWith(it) })
            assertFalse("URL must not contain spaces or HTML query artifacts: $url", url.contains(" "))
        }

        // Verify 2 models have requires16GbGate = true
        val gatedCount = Regex("\"requires16GbGate\":\\s*true").findAll(text).count()
        assertEquals(2, gatedCount)
    }

    @Test
    fun brandingAndMetadataSync_matchesYyAssistant() {
        val stringsFile = listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml")
        ).firstOrNull { it.exists() }
        assertNotNull(stringsFile)
        val stringsXml = stringsFile!!.readText()
        assertTrue(stringsXml.contains("<string name=\"app_name\">YY Assistant</string>"))

        val metadataFile = listOf(File("../metadata.json"), File("metadata.json")).firstOrNull { it.exists() }
        assertNotNull(metadataFile)
        val meta = metadataFile!!.readText()
        assertTrue(meta.contains("\"name\": \"YY Assistant\""))
        assertTrue(meta.contains("MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API"))
    }

    @Test
    fun ggufInspector_parsesBinaryKvMetadata_andRejectsCorruptStreams() {
        val inspector = GgufInspector()

        // Corrupt magic rejected
        assertFalse(inspector.inspectStream(ByteArrayInputStream("<!DOCTYPE html>".toByteArray())).isValid)
        assertFalse(inspector.inspectStream(ByteArrayInputStream("version https://git-lfs.github.com/spec/v1".toByteArray())).isValid)

        // Build real GGUF v3 header with 4 KV pairs
        val baos = ByteArrayOutputStream()
        fun writeU32(v: Int) = baos.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        fun writeI64(v: Long) = baos.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array())
        fun writeStr(s: String) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            writeI64(bytes.size.toLong())
            baos.write(bytes)
        }

        baos.write("GGUF".toByteArray(Charsets.US_ASCII))
        writeU32(3) // version 3
        writeI64(256L) // tensor count
        writeI64(4L) // kv count

        // KV 1: general.architecture = "qwen3" (type 8 = STRING)
        writeStr("general.architecture"); writeU32(8); writeStr("qwen3")
        // KV 2: general.name = "Qwen3 8B Local"
        writeStr("general.name"); writeU32(8); writeStr("Qwen3 8B Local")
        // KV 3: general.file_type = 15 (Q4_K_M) (type 4 = UINT32)
        writeStr("general.file_type"); writeU32(4); writeU32(15)
        // KV 4: qwen3.context_length = 8192 (type 4 = UINT32)
        writeStr("qwen3.context_length"); writeU32(4); writeU32(8192)

        val parsed = inspector.inspectStream(ByteArrayInputStream(baos.toByteArray()))
        assertTrue(parsed.isValid)
        assertEquals(3, parsed.version)
        assertEquals(256L, parsed.tensorCount)
        assertEquals("qwen3", parsed.architecture)
        assertEquals("Qwen3 8B Local", parsed.modelName)
        assertEquals("Q4_K_M", parsed.quantization)
        assertEquals(8192, parsed.contextLength)
        assertTrue(parsed.isSupportedArchitecture)
    }

    @Test
    fun llmEngine_enforcesMemoryBudget_thermalPause_streaming_andEvals() = runTest {
        val engine = OnDeviceLlmEngine()
        val starter = InstalledModelEntity(
            id = "bundled-135m-starter",
            displayName = "YY Assistant 135M Starter",
            modelType = "chat",
            tier = 1,
            path = "bundled://135m",
            sizeBytes = 86_000_000L,
            source = "bundled",
            supportsThinking = true
        )

        // 1. Oversized Tier 3 model rejected on 8 GB Tier 1 hardware
        val hugeModel = InstalledModelEntity(
            id = "qwen3-30b",
            displayName = "Qwen3 30B",
            tier = 3,
            path = "/nonexistent.gguf",
            sizeBytes = 18_600_000_000L,
            source = "catalog"
        )
        val rejected = engine.load(hugeModel, LlmConfig(), testHardware(DeviceTier.TIER_1_LITE, 8L))
        assertTrue(rejected is LoadResult.Rejected)

        // 2. Bundled starter loads cleanly
        val loaded = engine.load(
            starter,
            LlmConfig(
                contextLength = 4096,
                performanceProfile = PerformanceProfile.BALANCED,
                backend = AcceleratorBackend.CPU_NEON,
                samplingPreset = SamplingPreset.BALANCED
            ),
            testHardware(DeviceTier.TIER_2_BALANCED, 12L)
        )
        assertTrue(loaded is LoadResult.Success)
        assertEquals("bundled-135m-starter", engine.stats.value.loadedModelId)

        // 3. Severe thermal status pauses generation gracefully
        val severeTokens = engine.generate(
            GenerateRequest(prompt = "Hello", systemPrompt = "", historyTurns = emptyList()),
            testHardware(thermal = ThermalLevel.SEVERE)
        ).toList()
        assertTrue(severeTokens.any { it.text.contains("Thermal Protection Active") })
        assertTrue(engine.stats.value.isThermallyPaused)

        // 4. Offline translation EN -> HI works cleanly
        val hiTokens = engine.generate(
            GenerateRequest(
                prompt = "thank you",
                systemPrompt = "",
                historyTurns = emptyList(),
                translateDirection = "en->hi",
                enableThinking = false
            ),
            testHardware(thermal = ThermalLevel.NONE)
        ).toList()
        val hiText = hiTokens.filter { !it.isThought }.joinToString("") { it.text }
        assertTrue("Expected Hindi translation, got: $hiText", hiText.contains("धन्यवाद"))

        // 5. RAG grounded answer includes document citation
        val ragTokens = engine.generate(
            GenerateRequest(
                prompt = "What is the budget?",
                systemPrompt = "",
                historyTurns = emptyList(),
                citations = listOf(
                    SourceCitation("doc1", "Architecture.pdf", "Memory", 2, "Keep weights under 60% of RAM.", 0.92f)
                )
            ),
            testHardware(thermal = ThermalLevel.NONE)
        ).toList()
        val ragText = ragTokens.filter { !it.isThought }.joinToString("") { it.text }
        assertTrue(ragText.contains("Architecture.pdf"))

        // 6. Low-memory context reduction halves KV cache
        engine.lowerContextForMemoryPressure()
        assertEquals(2048, engine.stats.value.contextLength)

        // 7. Benchmark & 4-case eval suite all pass
        val (bench, evals) = engine.runModelBenchmarkAndEvals(testHardware())
        assertTrue(bench.tokensPerSec > 0f)
        assertEquals(4, evals.size)
        assertTrue(evals.all { it.passed })

        // 8. Unload clears resident model
        engine.unload()
        assertEquals(null, engine.stats.value.loadedModelId)
    }

    @Test
    fun safeMathExpressionEvaluator_handlesPrecedenceAndErrors() {
        assertEquals(576.0, LocalToolExecutor.evaluateMathExpression("(128 * 4) + 64"), 0.0001)
        assertEquals(512.0, LocalToolExecutor.evaluateMathExpression("2^3^2"), 0.0001)
        assertEquals(-14.0, LocalToolExecutor.evaluateMathExpression("-2 * (3 + 4)"), 0.0001)
        assertEquals(2.5, LocalToolExecutor.evaluateMathExpression("10 / 4"), 0.0001)
        assertTrue(runCatching { LocalToolExecutor.evaluateMathExpression("10 / 0") }.isFailure)
        assertTrue(runCatching { LocalToolExecutor.evaluateMathExpression("(1 + 2") }.isFailure)
    }
}
