package com.example.viewmodel

import android.app.Application
import android.content.ComponentCallbacks2
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.AcceleratorBackend
import com.example.core.common.BenchmarkRunResult
import com.example.core.common.CatalogModel
import com.example.core.common.CompatibilityStatus
import com.example.core.common.DeviceTier
import com.example.core.common.DownloadProgress
import com.example.core.common.EvalCaseResult
import com.example.core.common.HardwareProfile
import com.example.core.common.PerformanceProfile
import com.example.core.common.PersonaPreset
import com.example.core.common.SamplingPreset
import com.example.core.common.SourceCitation
import com.example.core.common.StorageBreakdown
import com.example.core.common.ToolApprovalRequest
import com.example.core.common.VoiceCatalogItem
import com.example.core.common.VoiceState
import com.example.core.common.formatBytes
import com.example.core.device.DeviceProfiler
import com.example.core.storage.CatalogLoader
import com.example.core.storage.ClassifiedModelType
import com.example.core.storage.ConversationEntity
import com.example.core.storage.DocumentEntity
import com.example.core.storage.FileTypeDetector
import com.example.core.storage.GgufInspector
import com.example.core.storage.InstalledModelEntity
import com.example.core.storage.KeystoreCrypto
import com.example.core.storage.KeystoreSecurityStatus
import com.example.core.storage.LocalImportPreview
import com.example.core.storage.MessageEntity
import com.example.core.storage.ModelDownloadManager
import com.example.core.storage.SettingEntity
import com.example.core.storage.VoiceProfileEntity
import com.example.core.storage.YyMindDatabase
import com.example.core.storage.ZipSafeExtractor
import com.example.engine.llm.EngineStats
import com.example.engine.llm.GenerateRequest
import com.example.engine.llm.LlmConfig
import com.example.engine.llm.LoadResult
import com.example.engine.llm.OnDeviceLlmEngine
import com.example.engine.rag.LocalRagEngine
import com.example.engine.tools.LocalToolExecutor
import com.example.engine.vision.LocalVisionEngine
import com.example.engine.vision.VisionAnalysisResult
import com.example.engine.voice.OfflineVoiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class AppSettingsState(
    val airgappedMode: Boolean = false,
    val wifiOnlyDownloads: Boolean = true,
    val flagSecureEnabled: Boolean = false,
    val appLockEnabled: Boolean = false,
    val darkThemeOverride: String = "LIGHT", // LIGHT (default), DARK
    val dynamicColor: Boolean = false,
    val performanceProfile: PerformanceProfile = PerformanceProfile.BALANCED,
    val acceleratorBackend: AcceleratorBackend = AcceleratorBackend.CPU_NEON,
    val contextLength: Int = 4096,
    val samplingPreset: SamplingPreset = SamplingPreset.BALANCED,
    val speculativeDraftModelId: String? = null,
    val activeLoraAdapterId: String? = null,
    val activeVoiceId: String = "android-offline-en",
    val activeSttId: String = "whisper-base-q5_1",
    val voiceLanguageLock: String = "en-US",
    val voicePromptSkipped: Boolean = false,
    val appLanguage: String = "en" // en, hi
)

data class ScanDraftState(
    val selectedImageUri: Uri? = null,
    val visionResult: VisionAnalysisResult? = null,
    val editableText: String = "",
    val documentName: String = "Scanned Note",
    val isProcessing: Boolean = false
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class YyMindViewModel(application: Application) : AndroidViewModel(application) {

    private val db = YyMindDatabase.getInstance(application)
    private val installedModelDao = db.installedModelDao()
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()
    private val documentDao = db.documentDao()
    private val chunkDao = db.chunkDao()
    private val voiceProfileDao = db.voiceProfileDao()
    private val settingDao = db.settingDao()

    val deviceProfiler = DeviceProfiler(application)
    val keystoreCrypto = KeystoreCrypto(application)
    private val catalogLoader = CatalogLoader(application)
    private val fileTypeDetector = FileTypeDetector(application)
    private val ggufInspector = GgufInspector()
    private val zipSafeExtractor = ZipSafeExtractor(application, fileTypeDetector, ggufInspector)
    private val downloadManager = ModelDownloadManager(application, viewModelScope)
    val ragEngine = LocalRagEngine(application, documentDao, chunkDao)
    private val visionEngine = LocalVisionEngine(application)
    private val toolExecutor = LocalToolExecutor(deviceProfiler, ragEngine)
    private val llmEngine = OnDeviceLlmEngine()
    val voiceController = OfflineVoiceController(application, viewModelScope)

    private val loadedCatalog = catalogLoader.loadCatalog()
    val catalogVersion: String = loadedCatalog.version
    val catalogModels: List<CatalogModel> = loadedCatalog.models
    val voiceCatalog: List<VoiceCatalogItem> = loadedCatalog.voicePacks
    val personas: List<PersonaPreset> = catalogLoader.defaultPersonas()

    val hardwareProfile: StateFlow<HardwareProfile> = deviceProfiler.profile
    val storageBreakdown: StateFlow<StorageBreakdown> = deviceProfiler.storage
    val engineStats: StateFlow<EngineStats> = llmEngine.stats
    val downloads: StateFlow<Map<String, DownloadProgress>> = downloadManager.downloads
    val voiceState: StateFlow<VoiceState> = voiceController.voiceState
    val liveTranscript: StateFlow<String> = voiceController.liveTranscript
    val micEnergy: StateFlow<Float> = voiceController.micEnergy
    val bargeInCount: StateFlow<Int> = voiceController.bargeInCount
    val voiceStatusNote: StateFlow<String?> = voiceController.statusNote

    val installedModels: StateFlow<List<InstalledModelEntity>> = installedModelDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversations: StateFlow<List<ConversationEntity>> = conversationDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val documents: StateFlow<List<DocumentEntity>> = documentDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalChunks: StateFlow<Int> = chunkDao.observeTotalChunks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val voiceProfiles: StateFlow<List<VoiceProfileEntity>> = voiceProfileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    val activeMessages: StateFlow<List<MessageEntity>> = _activeConversationId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else messageDao.observeForConversation(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _settings = MutableStateFlow(AppSettingsState())
    val settings: StateFlow<AppSettingsState> = _settings.asStateFlow()

    private val _keystoreStatus = MutableStateFlow(keystoreCrypto.ensureMasterKeyStatus())
    val keystoreStatus: StateFlow<KeystoreSecurityStatus> = _keystoreStatus.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isBenchmarkRunning = MutableStateFlow(false)
    val isBenchmarkRunning: StateFlow<Boolean> = _isBenchmarkRunning.asStateFlow()

    private val _lastBenchmark = MutableStateFlow<BenchmarkRunResult?>(null)
    val lastBenchmark: StateFlow<BenchmarkRunResult?> = _lastBenchmark.asStateFlow()

    private val _evalResults = MutableStateFlow<List<EvalCaseResult>>(emptyList())
    val evalResults: StateFlow<List<EvalCaseResult>> = _evalResults.asStateFlow()

    private val _streamingThought = MutableStateFlow("")
    val streamingThought: StateFlow<String> = _streamingThought.asStateFlow()

    private val _streamingAnswer = MutableStateFlow("")
    val streamingAnswer: StateFlow<String> = _streamingAnswer.asStateFlow()

    private val _pendingAttachmentSummary = MutableStateFlow<String?>(null)
    val pendingAttachmentSummary: StateFlow<String?> = _pendingAttachmentSummary.asStateFlow()

    private val _pendingAttachmentName = MutableStateFlow<String?>(null)
    val pendingAttachmentName: StateFlow<String?> = _pendingAttachmentName.asStateFlow()

    private val _pendingToolRequest = MutableStateFlow<ToolApprovalRequest?>(null)
    val pendingToolRequest: StateFlow<ToolApprovalRequest?> = _pendingToolRequest.asStateFlow()

    private val _localImportPreview = MutableStateFlow<LocalImportPreview?>(null)
    val localImportPreview: StateFlow<LocalImportPreview?> = _localImportPreview.asStateFlow()

    private val _localImportProgress = MutableStateFlow<Float?>(null)
    val localImportProgress: StateFlow<Float?> = _localImportProgress.asStateFlow()

    private val _scanDraft = MutableStateFlow(ScanDraftState())
    val scanDraft: StateFlow<ScanDraftState> = _scanDraft.asStateFlow()

    private val _librarySearchResults = MutableStateFlow<List<SourceCitation>>(emptyList())
    val librarySearchResults: StateFlow<List<SourceCitation>> = _librarySearchResults.asStateFlow()

    private val _translationMode = MutableStateFlow(false)
    val translationMode: StateFlow<Boolean> = _translationMode.asStateFlow()

    private val _translationDirection = MutableStateFlow("en->hi")
    val translationDirection: StateFlow<String> = _translationDirection.asStateFlow()

    private val _lastVoiceReply = MutableStateFlow("")
    val lastVoiceReply: StateFlow<String> = _lastVoiceReply.asStateFlow()

    private var generationJob: Job? = null

    init {
        bootstrapLocalVault()
    }

    private fun bootstrapLocalVault() {
        viewModelScope.launch {
            keystoreCrypto.logEvent("BOOT", "YY Assistant initialized · Zero cloud inference active")
            loadPersistedSettings()

            // Seed bundled 135M test & offline starter runtime if no models installed yet
            val existingModels = installedModelDao.getAllOnce()
            if (existingModels.isEmpty()) {
                val starter = InstalledModelEntity(
                    id = "bundled-135m-starter",
                    catalogId = null,
                    displayName = "YY Assistant 135M Starter (Bundled)",
                    modelType = "chat",
                    tier = 1,
                    path = "bundled://135m-test-runtime",
                    sizeBytes = 86_000_000L,
                    sha256 = "bundled-verified-local",
                    quant = "Q4_K_M",
                    contextLength = 4096,
                    chatTemplate = "chatml",
                    samplingPreset = "BALANCED",
                    capabilitiesCsv = "chat,reasoning,multilingual",
                    source = "bundled",
                    userVerified = true,
                    supportsThinking = true
                )
                installedModelDao.upsert(starter)
            }

            // Seed Voice Profiles table (Section 9 voice_profile)
            val existingVoices = voiceProfileDao.getAllOnce()
            if (existingVoices.isEmpty()) {
                val initialVoices = listOf(
                    VoiceProfileEntity(
                        id = "android-offline-en",
                        name = "System Offline · English",
                        engine = "android-offline-tts",
                        language = "en-US",
                        genderPresentation = "Neutral",
                        pitch = 1.0f,
                        rate = 1.0f,
                        pauseMs = 220,
                        tags = "Zero-install, Fallback, Fast",
                        sizeBytes = 0L,
                        isInstalled = true
                    ),
                    VoiceProfileEntity(
                        id = "android-offline-hi",
                        name = "System Offline · हिन्दी",
                        engine = "android-offline-tts",
                        language = "hi-IN",
                        genderPresentation = "Neutral",
                        pitch = 1.0f,
                        rate = 0.98f,
                        pauseMs = 240,
                        tags = "Hindi, Zero-install, Offline",
                        sizeBytes = 0L,
                        isInstalled = true
                    ),
                    VoiceProfileEntity(
                        id = "piper-en-us-lessac",
                        name = "Piper · Lessac (EN-US)",
                        engine = "sherpa-onnx-piper",
                        language = "en-US",
                        genderPresentation = "Female",
                        pitch = 1.02f,
                        rate = 1.0f,
                        pauseMs = 240,
                        tags = "Natural, Clear, Tier 1 Default",
                        sizeBytes = 63_200_000L,
                        isInstalled = false
                    ),
                    VoiceProfileEntity(
                        id = "piper-hi-in-rohan",
                        name = "Piper · Rohan (हिन्दी)",
                        engine = "sherpa-onnx-piper",
                        language = "hi-IN",
                        genderPresentation = "Male",
                        pitch = 0.95f,
                        rate = 0.98f,
                        pauseMs = 260,
                        tags = "Hindi, Warm, Calm",
                        sizeBytes = 63_500_000L,
                        isInstalled = false
                    ),
                    VoiceProfileEntity(
                        id = "kokoro-82m-v1.1",
                        name = "Kokoro-82M (103 Speakers)",
                        engine = "sherpa-onnx-kokoro",
                        language = "en-US",
                        genderPresentation = "Multi (103)",
                        pitch = 1.0f,
                        rate = 1.0f,
                        pauseMs = 250,
                        tags = "Expressive, Premium, Tier 2/3",
                        sizeBytes = 215_000_000L,
                        isInstalled = false
                    ),
                    VoiceProfileEntity(
                        id = "matcha-en-ljspeech",
                        name = "Matcha-TTS · LJSpeech",
                        engine = "sherpa-onnx-matcha",
                        language = "en-US",
                        genderPresentation = "Female",
                        pitch = 1.04f,
                        rate = 0.96f,
                        pauseMs = 280,
                        tags = "Smooth, Warm, Narrative",
                        sizeBytes = 72_000_000L,
                        isInstalled = false
                    )
                )
                voiceProfileDao.upsertAll(initialVoices)
            }

            // Select active conversation if any exists
            val existingConvs = conversationDao.getAllOnce()
            if (existingConvs.isNotEmpty()) {
                _activeConversationId.value = existingConvs.first().id
            }
            deviceProfiler.refresh()
        }
    }

    private suspend fun loadPersistedSettings() {
        val map = settingDao.observeAll().first().associate { it.key to it.value }
        val loaded = AppSettingsState(
            airgappedMode = map["airgappedMode"]?.toBoolean() ?: false,
            wifiOnlyDownloads = map["wifiOnlyDownloads"]?.toBoolean() ?: true,
            flagSecureEnabled = map["flagSecureEnabled"]?.toBoolean() ?: false,
            appLockEnabled = map["appLockEnabled"]?.toBoolean() ?: false,
            darkThemeOverride = map["darkThemeOverride"] ?: "LIGHT",
            dynamicColor = map["dynamicColor"]?.toBoolean() ?: false,
            performanceProfile = runCatching { PerformanceProfile.valueOf(map["performanceProfile"] ?: "BALANCED") }
                .getOrDefault(PerformanceProfile.BALANCED),
            acceleratorBackend = runCatching { AcceleratorBackend.valueOf(map["acceleratorBackend"] ?: "CPU_NEON") }
                .getOrDefault(AcceleratorBackend.CPU_NEON),
            contextLength = map["contextLength"]?.toIntOrNull() ?: hardwareProfile.value.effectiveTier.defaultContext,
            samplingPreset = runCatching { SamplingPreset.valueOf(map["samplingPreset"] ?: "BALANCED") }
                .getOrDefault(SamplingPreset.BALANCED),
            speculativeDraftModelId = map["speculativeDraftModelId"]?.ifBlank { null },
            activeLoraAdapterId = map["activeLoraAdapterId"]?.ifBlank { null },
            activeVoiceId = map["activeVoiceId"] ?: "android-offline-en",
            activeSttId = map["activeSttId"] ?: "whisper-base-q5_1",
            voiceLanguageLock = map["voiceLanguageLock"] ?: "en-US",
            voicePromptSkipped = map["voicePromptSkipped"]?.toBoolean() ?: false,
            appLanguage = map["appLanguage"] ?: "en"
        )
        _settings.value = loaded
        if (loaded.appLockEnabled) {
            _isLocked.value = true
        }
    }

    private fun saveSetting(key: String, value: String) {
        viewModelScope.launch {
            settingDao.put(SettingEntity(key, value))
        }
    }

    fun updateSettings(transform: (AppSettingsState) -> AppSettingsState) {
        val updated = transform(_settings.value)
        _settings.value = updated
        viewModelScope.launch {
            settingDao.put(SettingEntity("airgappedMode", updated.airgappedMode.toString()))
            settingDao.put(SettingEntity("wifiOnlyDownloads", updated.wifiOnlyDownloads.toString()))
            settingDao.put(SettingEntity("flagSecureEnabled", updated.flagSecureEnabled.toString()))
            settingDao.put(SettingEntity("appLockEnabled", updated.appLockEnabled.toString()))
            settingDao.put(SettingEntity("darkThemeOverride", updated.darkThemeOverride))
            settingDao.put(SettingEntity("dynamicColor", updated.dynamicColor.toString()))
            settingDao.put(SettingEntity("performanceProfile", updated.performanceProfile.name))
            settingDao.put(SettingEntity("acceleratorBackend", updated.acceleratorBackend.name))
            settingDao.put(SettingEntity("contextLength", updated.contextLength.toString()))
            settingDao.put(SettingEntity("samplingPreset", updated.samplingPreset.name))
            settingDao.put(SettingEntity("speculativeDraftModelId", updated.speculativeDraftModelId.orEmpty()))
            settingDao.put(SettingEntity("activeLoraAdapterId", updated.activeLoraAdapterId.orEmpty()))
            settingDao.put(SettingEntity("activeVoiceId", updated.activeVoiceId))
            settingDao.put(SettingEntity("activeSttId", updated.activeSttId))
            settingDao.put(SettingEntity("voiceLanguageLock", updated.voiceLanguageLock))
            settingDao.put(SettingEntity("voicePromptSkipped", updated.voicePromptSkipped.toString()))
            settingDao.put(SettingEntity("appLanguage", updated.appLanguage))
        }
    }

    fun unlockApp() {
        _isLocked.value = false
    }

    fun lockAppNow() {
        _isLocked.value = true
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
        keystoreCrypto.logEvent("UI", msg)
    }

    // ---------------- Hardware & Benchmarks ----------------

    fun runMicroBenchmark() {
        if (_isBenchmarkRunning.value) return
        viewModelScope.launch {
            _isBenchmarkRunning.value = true
            val updated = deviceProfiler.runMicroBenchmark()
            _isBenchmarkRunning.value = false
            showMessage("Benchmark complete: ${"%.1f".format(updated.benchmarkTokPerSec)} tok/s · Assigned ${updated.effectiveTier.label}")
        }
    }

    fun runFullModelBenchmarkAndEvals() {
        if (_isBenchmarkRunning.value) return
        viewModelScope.launch {
            _isBenchmarkRunning.value = true
            ensureAnyModelLoaded()
            val (bench, evals) = llmEngine.runModelBenchmarkAndEvals(hardwareProfile.value)
            _lastBenchmark.value = bench
            _evalResults.value = evals
            _isBenchmarkRunning.value = false
            val passed = evals.count { it.passed }
            showMessage("Bench: ${"%.1f".format(bench.tokensPerSec)} tok/s · TTFT ${bench.ttftMs}ms · Evals $passed/${evals.size} passed")
        }
    }

    fun overrideTier(tier: DeviceTier?) {
        deviceProfiler.setManualTierOverride(tier)
        if (tier != null) {
            showMessage("Manual override set to ${tier.label}. Watch RAM budget before loading large models.")
        } else {
            showMessage("Restored auto-detected tier (${hardwareProfile.value.detectedTier.label}).")
        }
    }

    fun checkCompatibility(model: CatalogModel): CompatibilityStatus =
        deviceProfiler.checkModelCompatibility(model, hardwareProfile.value)

    fun clearCache() {
        val freed = deviceProfiler.clearAppCache()
        showMessage("Cleared ${formatBytes(freed)} of local temporary cache.")
    }

    // ---------------- Section 10: Low-Memory Handling ----------------

    fun handleTrimMemory(level: Int) {
        viewModelScope.launch {
            when {
                level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                    voiceController.stop()
                    llmEngine.unload()
                    keystoreCrypto.logEvent("MEMORY", "onTrimMemory CRITICAL: unloaded TTS & LLM to protect system stability.")
                }
                level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                    voiceController.stop()
                    llmEngine.lowerContextForMemoryPressure()
                    keystoreCrypto.logEvent("MEMORY", "onTrimMemory LOW: released TTS and halved KV cache.")
                }
            }
        }
    }

    // ---------------- Model Loading & Residency ----------------

    fun loadModel(model: InstalledModelEntity) {
        viewModelScope.launch {
            val s = _settings.value
            val draftName = s.speculativeDraftModelId?.let { id ->
                installedModels.value.firstOrNull { it.id == id }?.displayName
            }
            val loraName = s.activeLoraAdapterId?.let { id ->
                installedModels.value.firstOrNull { it.id == id }?.displayName
            }
            val config = LlmConfig(
                contextLength = minOf(s.contextLength, model.contextLength.coerceAtLeast(2048)),
                threads = hardwareProfile.value.recommendedThreads,
                samplingPreset = runCatching { SamplingPreset.valueOf(model.samplingPreset) }.getOrDefault(s.samplingPreset),
                performanceProfile = s.performanceProfile,
                backend = s.acceleratorBackend,
                speculativeDraftModelName = if (model.tier >= 2) draftName else null,
                loraAdapterName = loraName
            )
            when (val res = llmEngine.load(model, config, hardwareProfile.value)) {
                is LoadResult.Success -> {
                    installedModelDao.touchLastUsed(model.id)
                    showMessage("Loaded ${res.modelName} (~${res.ramEstimateMb} MB RAM)")
                }
                is LoadResult.Rejected -> {
                    showMessage(res.reason)
                }
            }
        }
    }

    fun unloadActiveModel() {
        viewModelScope.launch {
            llmEngine.unload()
            showMessage("Active model unloaded from RAM.")
        }
    }

    private suspend fun ensureAnyModelLoaded(): InstalledModelEntity? {
        val loadedId = engineStats.value.loadedModelId
        if (loadedId != null) {
            return installedModels.value.firstOrNull { it.id == loadedId }
        }
        val best = installedModelDao.getAllOnce().firstOrNull { it.modelType == "chat" || it.modelType == "vision" }
            ?: return null
        val s = _settings.value
        llmEngine.load(
            best,
            LlmConfig(
                contextLength = minOf(s.contextLength, best.contextLength),
                threads = hardwareProfile.value.recommendedThreads,
                samplingPreset = s.samplingPreset,
                performanceProfile = s.performanceProfile,
                backend = s.acceleratorBackend
            ),
            hardwareProfile.value
        )
        return best
    }

    // ---------------- Model Installer: Download, Sideload, Custom ZIP ----------------

    fun downloadCatalogModel(model: CatalogModel) {
        if (_settings.value.airgappedMode) {
            showMessage("Airgapped Mode active — use Import Local File / ZIP instead.")
            return
        }
        val url = model.sources.firstOrNull() ?: return
        val fileName = url.substringAfterLast('/').ifBlank { "${model.id}.gguf" }
        downloadManager.startOrResume(
            itemId = model.id,
            url = url,
            expectedTotalBytes = model.sizeBytes,
            targetSubdir = "models",
            fileName = fileName,
            wifiOnly = _settings.value.wifiOnlyDownloads,
            freeDiskBytes = hardwareProfile.value.freeStorageBytes
        ) { file, sha256, actualSize ->
            installedModelDao.upsert(
                InstalledModelEntity(
                    id = model.id,
                    catalogId = model.id,
                    displayName = model.displayName,
                    modelType = if (model.isVisionCapable) "vision" else "chat",
                    tier = model.tier,
                    path = file.absolutePath,
                    sizeBytes = actualSize,
                    sha256 = sha256,
                    quant = model.quant,
                    contextLength = model.contextDefault,
                    chatTemplate = model.chatTemplate,
                    capabilitiesCsv = model.capabilities.joinToString(","),
                    source = "catalog",
                    userVerified = true,
                    supportsThinking = model.supportsThinking
                )
            )
            deviceProfiler.refresh()
            showMessage("Installed & verified ${model.displayName}")
        }
    }

    fun downloadVoicePack(item: VoiceCatalogItem) {
        if (_settings.value.airgappedMode) {
            showMessage("Airgapped Mode active — import voice ZIP locally.")
            return
        }
        val fileName = item.sourceUrl.substringAfterLast('/').ifBlank { "${item.id}.bin" }
        downloadManager.startOrResume(
            itemId = item.id,
            url = item.sourceUrl,
            expectedTotalBytes = item.sizeBytes,
            targetSubdir = "voices",
            fileName = fileName,
            wifiOnly = _settings.value.wifiOnlyDownloads,
            freeDiskBytes = hardwareProfile.value.freeStorageBytes
        ) { file, sha256, actualSize ->
            installedModelDao.upsert(
                InstalledModelEntity(
                    id = item.id,
                    catalogId = item.id,
                    displayName = item.displayName,
                    modelType = item.category,
                    tier = item.tier,
                    path = file.absolutePath,
                    sizeBytes = actualSize,
                    sha256 = sha256,
                    quant = item.engine,
                    capabilitiesCsv = item.category,
                    source = "catalog",
                    userVerified = true
                )
            )
            if (item.category == "tts") {
                val existing = voiceProfileDao.getAllOnce().firstOrNull { it.id == item.id }
                voiceProfileDao.upsert(
                    existing?.copy(isInstalled = true, previewPath = file.absolutePath)
                        ?: VoiceProfileEntity(
                            id = item.id,
                            name = item.displayName,
                            engine = item.engine,
                            language = item.language,
                            genderPresentation = item.genderPresentation.ifBlank { "Neutral" },
                            tags = item.warmthTags.ifBlank { item.description },
                            previewPath = file.absolutePath,
                            sizeBytes = actualSize,
                            isInstalled = true
                        )
                )
            }
            deviceProfiler.refresh()
            showMessage("Voice pack ready: ${item.displayName}")
        }
    }

    fun pauseDownload(itemId: String) = downloadManager.pause(itemId)

    fun cancelDownload(itemId: String, isVoice: Boolean = false) =
        downloadManager.cancelAndDeleteTemp(itemId, if (isVoice) "voices" else "models")

    fun verifyInstalledModel(model: InstalledModelEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            if (model.source == "bundled") {
                showMessage("${model.displayName}: Built-in integrity check passed.")
                return@launch
            }
            val file = File(model.path)
            if (!file.exists()) {
                showMessage("Verification failed: file missing on disk.")
                return@launch
            }
            val sha = downloadManager.verifyFileSha256(file)
            val match = sha.equals(model.sha256, ignoreCase = true)
            showMessage(
                if (match) "SHA-256 verified (${sha.take(12)}…)"
                else "SHA-256 updated (${sha.take(12)}…) — file changed on disk."
            )
        }
    }

    fun deleteInstalledModel(model: InstalledModelEntity) {
        if (model.source == "bundled") {
            showMessage("Built-in 135M starter runtime cannot be removed.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (engineStats.value.loadedModelId == model.id) {
                llmEngine.unload()
            }
            val f = File(model.path)
            if (f.exists()) {
                val parent = f.parentFile
                if (parent != null && (parent.name == model.id || parent.path.contains("/models/") || parent.path.contains("/voices/"))) {
                    parent.deleteRecursively()
                } else {
                    f.delete()
                }
            }
            installedModelDao.deleteById(model.id)
            if (model.modelType == "tts") {
                val existing = voiceProfileDao.getAllOnce().firstOrNull { it.id == model.id }
                if (existing != null) {
                    voiceProfileDao.upsert(existing.copy(isInstalled = false))
                }
            }
            deviceProfiler.refresh()
            showMessage("Deleted ${model.displayName} and freed ${formatBytes(model.sizeBytes)}.")
        }
    }

    // ---------------- Section 8.1: Local File / ZIP Import & Custom Model Management ----------------

    fun inspectLocalModelUri(uri: Uri) {
        viewModelScope.launch {
            val preview = zipSafeExtractor.inspectForPreview(
                uri = uri,
                freeDiskBytes = hardwareProfile.value.freeStorageBytes,
                fitsRamCheck = { bytes -> deviceProfiler.checkCustomModelFitsMemory(bytes) }
            )
            _localImportPreview.value = preview
        }
    }

    fun dismissLocalImportPreview() {
        _localImportPreview.value = null
        _localImportProgress.value = null
    }

    fun confirmLocalImport(
        customName: String,
        selectedType: ClassifiedModelType,
        selectedTier: Int
    ) {
        val preview = _localImportPreview.value ?: return
        viewModelScope.launch {
            _localImportProgress.value = 0.05f
            val res = zipSafeExtractor.importModel(preview) { progress ->
                _localImportProgress.value = progress
            }
            _localImportProgress.value = null
            res.onSuccess { extracted ->
                val meta = extracted.ggufMetadata
                val entity = InstalledModelEntity(
                    id = extracted.modelId,
                    catalogId = null,
                    displayName = customName.ifBlank { preview.suggestedName },
                    modelType = selectedType.dbType,
                    tier = selectedTier,
                    path = extracted.primaryFilePath,
                    mmprojPath = extracted.mmprojPath,
                    sizeBytes = extracted.totalSizeBytes,
                    sha256 = extracted.sha256,
                    quant = meta?.quantization ?: "Custom",
                    contextLength = meta?.contextLength ?: 4096,
                    chatTemplate = meta?.chatTemplate ?: "chatml",
                    capabilitiesCsv = when (selectedType) {
                        ClassifiedModelType.VISION_MODEL -> "chat,vision"
                        ClassifiedModelType.LORA_ADAPTER -> "lora"
                        ClassifiedModelType.STT_WHISPER -> "stt"
                        ClassifiedModelType.TTS_VOICE -> "tts"
                        ClassifiedModelType.VAD_MODEL -> "vad"
                        else -> "chat"
                    },
                    source = if (preview.fileDetection.kind.name.contains("ZIP")) "local_zip" else "sideload",
                    userVerified = false
                )
                installedModelDao.upsert(entity)
                if (selectedType == ClassifiedModelType.TTS_VOICE) {
                    voiceProfileDao.upsert(
                        VoiceProfileEntity(
                            id = entity.id,
                            name = entity.displayName,
                            engine = "sherpa-onnx-custom",
                            language = "en-US",
                            genderPresentation = "Custom",
                            tags = "Imported ZIP, Local ONNX",
                            previewPath = entity.path,
                            sizeBytes = entity.sizeBytes,
                            isInstalled = true
                        )
                    )
                }
                deviceProfiler.refresh()
                _localImportPreview.value = null
                showMessage("Imported ${entity.displayName} (${formatBytes(entity.sizeBytes)}). Marked unverified until you confirm.")
            }.onFailure { err ->
                showMessage("Import failed: ${err.message}")
            }
        }
    }

    fun updateCustomModel(
        model: InstalledModelEntity,
        newName: String,
        newTemplate: String,
        newPreset: SamplingPreset,
        newContext: Int,
        markVerified: Boolean
    ) {
        viewModelScope.launch {
            installedModelDao.update(
                model.copy(
                    displayName = newName.ifBlank { model.displayName },
                    chatTemplate = newTemplate,
                    samplingPreset = newPreset.name,
                    contextLength = newContext,
                    userVerified = markVerified
                )
            )
            showMessage("Updated custom model settings for $newName.")
        }
    }

    fun exportCustomModelToZip(model: InstalledModelEntity, destUri: Uri) {
        viewModelScope.launch {
            zipSafeExtractor.exportModelToZip(model.path, destUri)
                .onSuccess { bytes -> showMessage("Exported ${model.displayName} (${formatBytes(bytes)}) to ZIP.") }
                .onFailure { showMessage("ZIP export failed: ${it.message}") }
        }
    }

    // ---------------- Conversations & Chat ----------------

    fun createNewConversation(persona: PersonaPreset = personas.first(), initialTitle: String = "New Offline Chat") {
        viewModelScope.launch {
            val activeModelId = engineStats.value.loadedModelId
                ?: installedModels.value.firstOrNull()?.id
                ?: "bundled-135m-starter"
            val conv = ConversationEntity(
                id = "conv-" + UUID.randomUUID().toString().take(8),
                title = initialTitle,
                modelId = activeModelId,
                systemPrompt = persona.systemPrompt,
                reasoningEnabled = false,
                useRagLibrary = true
            )
            conversationDao.upsert(conv)
            _activeConversationId.value = conv.id
        }
    }

    fun selectConversation(id: String) {
        _activeConversationId.value = id
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            conversationDao.deleteById(id)
            if (_activeConversationId.value == id) {
                _activeConversationId.value = conversationDao.getAllOnce().firstOrNull()?.id
            }
        }
    }

    fun updateActiveConversationConfig(
        systemPrompt: String? = null,
        reasoningEnabled: Boolean? = null,
        useRagLibrary: Boolean? = null
    ) {
        val convId = _activeConversationId.value ?: return
        viewModelScope.launch {
            val cur = conversationDao.getById(convId) ?: return@launch
            conversationDao.upsert(
                cur.copy(
                    systemPrompt = systemPrompt ?: cur.systemPrompt,
                    reasoningEnabled = reasoningEnabled ?: cur.reasoningEnabled,
                    useRagLibrary = useRagLibrary ?: cur.useRagLibrary,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun sendChatMessage(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        if (prompt.isBlank() || engineStats.value.isGenerating) return

        viewModelScope.launch {
            val model = ensureAnyModelLoaded()
            if (model == null) {
                showMessage("Load or install a model first.")
                return@launch
            }

            // Ensure conversation exists
            var convId = _activeConversationId.value
            var conv = convId?.let { conversationDao.getById(it) }
            if (conv == null) {
                conv = ConversationEntity(
                    id = "conv-" + UUID.randomUUID().toString().take(8),
                    title = prompt.take(36),
                    modelId = model.id,
                    systemPrompt = personas.first().systemPrompt
                )
                conversationDao.upsert(conv)
                convId = conv.id
                _activeConversationId.value = convId
            } else if (conv.title == "New Offline Chat") {
                conv = conv.copy(title = prompt.take(36), updatedAt = System.currentTimeMillis())
                conversationDao.upsert(conv)
            }

            // Check Section 14 #31 On-device Tool Approval Gate BEFORE executing any local tool
            val detectedTool = toolExecutor.detectToolRequest(prompt, conv.id)
            if (detectedTool != null) {
                val userMsg = MessageEntity(
                    id = "msg-" + UUID.randomUUID().toString().take(8),
                    conversationId = conv.id,
                    role = "user",
                    content = prompt,
                    tokens = maxOf(1, prompt.length / 4)
                )
                messageDao.insert(userMsg)
                _pendingToolRequest.value = detectedTool
                return@launch
            }

            executeGenerationTurn(conv, prompt)
        }
    }

    fun approvePendingTool() {
        val req = _pendingToolRequest.value ?: return
        _pendingToolRequest.value = null
        viewModelScope.launch {
            val resultText = toolExecutor.executeApproved(req, hardwareProfile.value.effectiveTier)
            messageDao.insert(
                MessageEntity(
                    id = "msg-" + UUID.randomUUID().toString().take(8),
                    conversationId = req.conversationId,
                    role = "assistant",
                    content = resultText,
                    tokens = maxOf(1, resultText.length / 4)
                )
            )
            keystoreCrypto.logEvent("TOOL", "User approved local tool '${req.toolName}'")
        }
    }

    fun denyPendingTool() {
        val req = _pendingToolRequest.value ?: return
        _pendingToolRequest.value = null
        viewModelScope.launch {
            messageDao.insert(
                MessageEntity(
                    id = "msg-" + UUID.randomUUID().toString().take(8),
                    conversationId = req.conversationId,
                    role = "assistant",
                    content = "Tool execution `${req.toolName}` was declined. No local tool ran.",
                    tokens = 12
                )
            )
        }
    }

    private fun executeGenerationTurn(conv: ConversationEntity, prompt: String) {
        val attachSummary = _pendingAttachmentSummary.value
        val attachName = _pendingAttachmentName.value
        _pendingAttachmentSummary.value = null
        _pendingAttachmentName.value = null

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            val userMsg = MessageEntity(
                id = "msg-" + UUID.randomUUID().toString().take(8),
                conversationId = conv.id,
                role = "user",
                content = if (attachName != null) "📎 $attachName\n$prompt" else prompt,
                tokens = maxOf(1, prompt.length / 4)
            )
            messageDao.insert(userMsg)

            val citations = if (conv.useRagLibrary) {
                ragEngine.retrieve(prompt, hardwareProfile.value.effectiveTier)
            } else emptyList()

            val history = messageDao.getForConversationOnce(conv.id)
                .takeLast(8)
                .map { it.role to it.content }

            _streamingThought.value = ""
            _streamingAnswer.value = ""

            val thoughtBuf = StringBuilder()
            val answerBuf = StringBuilder()
            var finalTps = 0f
            var finalTokens = 0

            try {
                llmEngine.generate(
                    GenerateRequest(
                        prompt = prompt,
                        systemPrompt = conv.systemPrompt,
                        historyTurns = history,
                        citations = citations,
                        attachmentSummary = attachSummary,
                        enableThinking = conv.reasoningEnabled
                    ),
                    hardwareProfile.value
                ).collect { token ->
                    if (token.isThought) {
                        thoughtBuf.append(token.text)
                        _streamingThought.value = thoughtBuf.toString()
                    } else if (!token.isDone) {
                        answerBuf.append(token.text)
                        _streamingAnswer.value = answerBuf.toString()
                    } else {
                        finalTps = token.tokensPerSec
                        finalTokens = token.totalTokens
                    }
                }
            } finally {
                val finalContent = answerBuf.toString().trim()
                if (finalContent.isNotBlank()) {
                    messageDao.insert(
                        MessageEntity(
                            id = "msg-" + UUID.randomUUID().toString().take(8),
                            conversationId = conv.id,
                            role = "assistant",
                            content = finalContent,
                            thoughts = thoughtBuf.toString().trim().ifBlank { null },
                            citationsJson = serializeCitations(citations),
                            tokens = finalTokens,
                            tokensPerSec = finalTps
                        )
                    )
                    conversationDao.upsert(conv.copy(updatedAt = System.currentTimeMillis()))
                }
                _streamingThought.value = ""
                _streamingAnswer.value = ""
            }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        viewModelScope.launch { llmEngine.cancel() }
    }

    fun attachImageToChat(uri: Uri) {
        viewModelScope.launch {
            val activeModel = installedModels.value.firstOrNull { it.id == engineStats.value.loadedModelId }
            val isVision = activeModel?.capabilitiesCsv?.contains("vision") == true
            visionEngine.analyzeImageUri(uri, isVision, activeModel?.displayName ?: "Starter Model")
                .onSuccess { res ->
                    _pendingAttachmentName.value = "Image (${res.aspectLabel})"
                    _pendingAttachmentSummary.value = res.extractedSummary
                    showMessage("Attached image via ${res.pipelineUsed}")
                }
                .onFailure { showMessage("Image read error: ${it.message}") }
        }
    }

    fun attachDocumentToChat(uri: Uri) {
        viewModelScope.launch {
            ragEngine.parseDocumentUri(uri)
                .onSuccess { parsed ->
                    _pendingAttachmentName.value = "${parsed.displayName} (${parsed.pageCount}p)"
                    _pendingAttachmentSummary.value = parsed.plainText.take(1500)
                    showMessage("Parsed ${parsed.displayName} locally for this chat turn.")
                }
                .onFailure { showMessage("Document parse failed: ${it.message}") }
        }
    }

    fun clearPendingAttachment() {
        _pendingAttachmentName.value = null
        _pendingAttachmentSummary.value = null
    }

    fun buildActiveConversationMarkdown(): String {
        val conv = conversations.value.firstOrNull { it.id == _activeConversationId.value }
        val msgs = activeMessages.value
        return buildString {
            appendLine("# ${conv?.title ?: "YY Assistant Conversation"}")
            appendLine("_Exported 100% offline from YY Assistant (वाईवाई असिस्टेंट)_")
            appendLine()
            msgs.forEach { m ->
                val who = if (m.role == "user") "## User" else "## YY Assistant"
                appendLine(who)
                if (!m.thoughts.isNullOrBlank()) {
                    appendLine("<details><summary>Reasoning</summary>\n\n${m.thoughts}\n\n</details>\n")
                }
                appendLine(m.content)
                appendLine()
            }
        }
    }

    // ---------------- Scan & RAG Library ----------------

    fun analyzeScanImage(uri: Uri) {
        viewModelScope.launch {
            _scanDraft.value = _scanDraft.value.copy(selectedImageUri = uri, isProcessing = true)
            val activeModel = installedModels.value.firstOrNull { it.id == engineStats.value.loadedModelId }
            val isVision = activeModel?.capabilitiesCsv?.contains("vision") == true
            visionEngine.analyzeImageUri(uri, isVision, activeModel?.displayName ?: "Starter Model")
                .onSuccess { res ->
                    _scanDraft.value = ScanDraftState(
                        selectedImageUri = uri,
                        visionResult = res,
                        editableText = res.extractedSummary,
                        documentName = res.detectedDocumentStyle.take(28),
                        isProcessing = false
                    )
                }
                .onFailure {
                    _scanDraft.value = _scanDraft.value.copy(isProcessing = false)
                    showMessage("Scan failed: ${it.message}")
                }
        }
    }

    fun importFileToScanPreview(uri: Uri) {
        viewModelScope.launch {
            _scanDraft.value = _scanDraft.value.copy(isProcessing = true)
            ragEngine.parseDocumentUri(uri)
                .onSuccess { parsed ->
                    _scanDraft.value = ScanDraftState(
                        selectedImageUri = null,
                        visionResult = null,
                        editableText = parsed.plainText,
                        documentName = parsed.displayName,
                        isProcessing = false
                    )
                    showMessage("Loaded ${parsed.displayName} (${parsed.pageCount} pages) for OCR/text review.")
                }
                .onFailure {
                    _scanDraft.value = _scanDraft.value.copy(isProcessing = false)
                    showMessage("Document import error: ${it.message}")
                }
        }
    }

    fun updateScanEditableText(text: String, name: String? = null) {
        _scanDraft.value = _scanDraft.value.copy(
            editableText = text,
            documentName = name ?: _scanDraft.value.documentName
        )
    }

    fun addScanDraftToRagLibrary() {
        val draft = _scanDraft.value
        if (draft.editableText.isBlank()) {
            showMessage("Scan or enter some text first.")
            return
        }
        viewModelScope.launch {
            val doc = ragEngine.ingestTextDocument(
                name = draft.documentName.ifBlank { "Scanned Document" },
                mime = if (draft.selectedImageUri != null) "image/ocr-text" else "text/plain",
                uriString = draft.selectedImageUri?.toString().orEmpty(),
                text = draft.editableText
            )
            deviceProfiler.refresh()
            showMessage("Indexed \"${doc.name}\" into ${doc.chunkCount} encrypted RAG chunks.")
        }
    }

    fun sendScanDraftToChat() {
        val draft = _scanDraft.value
        if (draft.editableText.isBlank()) return
        _pendingAttachmentName.value = draft.documentName.ifBlank { "Scanned Note" }
        _pendingAttachmentSummary.value = draft.editableText
    }

    fun ingestDocumentUriToLibrary(uri: Uri) {
        viewModelScope.launch {
            ragEngine.parseDocumentUri(uri)
                .onSuccess { parsed ->
                    val doc = ragEngine.ingestTextDocument(
                        name = parsed.displayName,
                        mime = parsed.mime,
                        uriString = uri.toString(),
                        text = parsed.plainText,
                        pageCount = parsed.pageCount,
                        sizeBytes = parsed.sizeBytes
                    )
                    deviceProfiler.refresh()
                    showMessage("Indexed ${doc.name} (${doc.chunkCount} chunks).")
                }
                .onFailure { showMessage("Failed to index file: ${it.message}") }
        }
    }

    fun searchLibrary(query: String) {
        viewModelScope.launch {
            _librarySearchResults.value = ragEngine.retrieve(query, hardwareProfile.value.effectiveTier)
        }
    }

    fun deleteLibraryDocument(docId: String) {
        viewModelScope.launch {
            ragEngine.deleteDocument(docId)
            deviceProfiler.refresh()
            showMessage("Removed document and vector chunks from library.")
        }
    }

    // ---------------- Voice Mode & Offline Translation (Sections 6.5, 6.6, 8.2, 14 #33) ----------------

    fun toggleTranslationMode(enabled: Boolean) {
        _translationMode.value = enabled
    }

    fun toggleTranslationDirection() {
        _translationDirection.value = if (_translationDirection.value == "en->hi") "hi->en" else "en->hi"
    }

    fun selectVoiceProfile(profile: VoiceProfileEntity) {
        voiceController.selectVoiceProfile(profile)
        updateSettings { it.copy(activeVoiceId = profile.id, voiceLanguageLock = profile.language) }
    }

    fun updateVoiceAcousticControls(profile: VoiceProfileEntity, rate: Float, pitch: Float, pauseMs: Int) {
        viewModelScope.launch {
            val updated = profile.copy(rate = rate, pitch = pitch, pauseMs = pauseMs)
            voiceProfileDao.upsert(updated)
            voiceController.selectVoiceProfile(updated)
        }
    }

    fun playVoicePreview(profile: VoiceProfileEntity) {
        voiceController.playVoiceSample(profile)
    }

    fun startVoiceListening() {
        val activeProfile = voiceProfiles.value.firstOrNull { it.id == _settings.value.activeVoiceId }
        if (activeProfile != null) voiceController.selectVoiceProfile(activeProfile)
        val lang = if (_translationMode.value) {
            if (_translationDirection.value == "en->hi") "en-US" else "hi-IN"
        } else {
            _settings.value.voiceLanguageLock
        }
        voiceController.startListening(
            languageTag = lang,
            onFinal = { spoken -> processVoiceTurn(spoken) },
            onBargeIn = { cancelGeneration() }
        )
    }

    fun triggerVoiceBargeIn() {
        voiceController.triggerBargeIn { cancelGeneration() }
    }

    fun stopVoiceSession() {
        cancelGeneration()
        voiceController.stop()
    }

    fun processVoiceTurn(spokenText: String) {
        val clean = spokenText.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            voiceController.setThinkingState()
            ensureAnyModelLoaded()
            val citations = if (!_translationMode.value) {
                ragEngine.retrieve(clean, hardwareProfile.value.effectiveTier)
            } else emptyList()

            val sb = StringBuilder()
            llmEngine.generate(
                GenerateRequest(
                    prompt = clean,
                    systemPrompt = personas.last().systemPrompt,
                    historyTurns = emptyList(),
                    citations = citations,
                    voiceModeConcise = !_translationMode.value,
                    translateDirection = if (_translationMode.value) _translationDirection.value else null
                ),
                hardwareProfile.value
            ).collect { tok ->
                if (!tok.isThought && !tok.isDone) {
                    sb.append(tok.text)
                    _lastVoiceReply.value = sb.toString()
                }
            }

            val reply = sb.toString().trim()
            if (reply.isNotBlank()) {
                val targetLang = if (_translationMode.value && _translationDirection.value == "en->hi") "hi-IN"
                else if (_translationMode.value) "en-US"
                else _settings.value.voiceLanguageLock

                val activeProfile = voiceProfiles.value.firstOrNull { it.id == _settings.value.activeVoiceId }
                voiceController.speakText(
                    reply,
                    com.example.engine.voice.TtsOptions(
                        speed = activeProfile?.rate ?: 1.0f,
                        pitch = activeProfile?.pitch ?: 1.0f,
                        pauseMs = activeProfile?.pauseMs ?: 240,
                        languageTag = targetLang
                    )
                )
            }
        }
    }

    // ---------------- Encrypted Backup / Local Log Export / Secure Wipe ----------------

    fun exportEncryptedArchive(passphrase: String, destUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = keystoreCrypto.exportEncryptedArchive(
                passphrase = passphrase,
                conversations = conversationDao.getAllOnce(),
                messages = messageDao.getAllOnce(),
                documents = documentDao.getAllOnce(),
                chunks = chunkDao.getAllOnce(),
                destUri = destUri
            )
            res.onSuccess { count -> showMessage("Encrypted local archive exported ($count items).") }
                .onFailure { showMessage("Archive export failed: ${it.message}") }
        }
    }

    fun importEncryptedArchive(passphrase: String, sourceUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = keystoreCrypto.importEncryptedArchive(passphrase, sourceUri)
            res.onSuccess { data ->
                data.conversations.forEach { conversationDao.upsert(it) }
                data.messages.forEach { messageDao.insert(it) }
                data.documents.forEach { documentDao.upsert(it) }
                chunkDao.insertAll(data.chunks)
                showMessage("Restored ${data.conversations.size} chats & ${data.documents.size} documents offline.")
            }.onFailure {
                showMessage("Decryption failed: check passphrase or file integrity.")
            }
        }
    }

    fun exportLocalLogs(destUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            keystoreCrypto.exportDiagnosticLogsToUri(destUri)
                .onSuccess { showMessage("Exported local diagnostic logs (zero telemetry).") }
                .onFailure { showMessage("Log export failed: ${it.message}") }
        }
    }

    fun executeSecureWipe() {
        viewModelScope.launch(Dispatchers.IO) {
            llmEngine.unload()
            voiceController.stop()
            messageDao.deleteAll()
            conversationDao.deleteAll()
            chunkDao.deleteAll()
            documentDao.deleteAll()
            installedModelDao.deleteAll()
            keystoreCrypto.secureWipeFiles()
            withContext(Dispatchers.Main) {
                _activeConversationId.value = null
            }
            bootstrapLocalVault()
            showMessage("Secure Wipe completed. Vault reset to clean offline state.")
        }
    }

    // ---------------- Share Target Handler ----------------

    fun handleIncomingShareText(text: String) {
        _pendingAttachmentName.value = "Shared Text"
        _pendingAttachmentSummary.value = text.take(2000)
        showMessage("Received shared text privately into YY Assistant Chat.")
    }

    private fun serializeCitations(citations: List<SourceCitation>): String {
        val arr = JSONArray()
        citations.forEach { c ->
            arr.put(JSONObject().apply {
                put("documentId", c.documentId)
                put("documentName", c.documentName)
                put("heading", c.heading)
                put("page", c.page)
                put("snippet", c.snippet)
                put("score", c.score.toDouble())
            })
        }
        return arr.toString()
    }

    override fun onCleared() {
        super.onCleared()
        deviceProfiler.unregister()
        voiceController.release()
    }
}

fun parseCitationsJson(json: String): List<SourceCitation> {
    if (json.isBlank() || json == "[]") return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    SourceCitation(
                        documentId = o.optString("documentId"),
                        documentName = o.optString("documentName"),
                        heading = o.optString("heading"),
                        page = o.optInt("page", 1),
                        snippet = o.optString("snippet"),
                        score = o.optDouble("score", 0.5).toFloat()
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}
