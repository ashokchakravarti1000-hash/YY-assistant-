package com.example.engine.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.core.common.VoiceState
import com.example.core.storage.VoiceProfileEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.sqrt

data class Transcript(
    val text: String,
    val isFinal: Boolean,
    val language: String = "auto"
)

data class TtsOptions(
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val pauseMs: Int = 250,
    val languageTag: String = "en-US"
)

data class AudioChunk(
    val sentence: String,
    val index: Int
)

/**
 * Section 6.4 & 6.5 Interfaces.
 */
interface SttEngine {
    fun transcribeStream(audio: Flow<ShortArray>): Flow<Transcript>
    suspend fun transcribeFile(uriString: String): Transcript
}

interface TtsEngine {
    val profile: VoiceProfileEntity?
    fun synthesize(text: String, opts: TtsOptions): Flow<AudioChunk>
    fun stop()
}

/**
 * Real Android On-Device SpeechRecognizer + Android Offline TextToSpeech + AudioRecord RMS VAD
 * Controller supporting sentence-boundary streaming, multi-voice profiles, Hindi/English locales,
 * and <200ms barge-in interruption (Sections 6.4, 6.5, 6.6, 8.2).
 */
class OfflineVoiceController(
    private val context: Context,
    private val scope: CoroutineScope
) : TtsEngine {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var vadJob: Job? = null

    private var _activeProfile: VoiceProfileEntity? = null
    override val profile: VoiceProfileEntity? get() = _activeProfile

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _micEnergy = MutableStateFlow(0f)
    val micEnergy: StateFlow<Float> = _micEnergy.asStateFlow()

    private val _bargeInCount = MutableStateFlow(0)
    val bargeInCount: StateFlow<Int> = _bargeInCount.asStateFlow()

    private val _statusNote = MutableStateFlow<String?>(null)
    val statusNote: StateFlow<String?> = _statusNote.asStateFlow()

    private var onFinalTranscriptCallback: ((String) -> Unit)? = null
    private var onBargeInCallback: (() -> Unit)? = null

    init {
        initTts()
    }

    private fun initTts() {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                ttsReady = (status == TextToSpeech.SUCCESS)
                if (ttsReady) {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _voiceState.value = VoiceState.SPEAKING
                        }

                        override fun onDone(utteranceId: String?) {
                            if (utteranceId?.endsWith("_LAST") == true) {
                                _voiceState.value = VoiceState.IDLE
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _voiceState.value = VoiceState.IDLE
                        }
                    })
                }
            }
        }
    }

    fun selectVoiceProfile(profile: VoiceProfileEntity) {
        _activeProfile = profile
        applyTtsOptions(
            TtsOptions(
                speed = profile.rate,
                pitch = profile.pitch,
                pauseMs = profile.pauseMs,
                languageTag = profile.language
            )
        )
    }

    fun playVoiceSample(profile: VoiceProfileEntity) {
        selectVoiceProfile(profile)
        val sampleText = if (profile.language.startsWith("hi", ignoreCase = true)) {
            "नमस्ते, मैं वाईवाई असिस्टेंट हूँ। यह आपकी ऑफलाइन आवाज़ का नमूना है।"
        } else {
            "Hello, I am YY Assistant running privately on your device. This is the ${profile.name} voice profile."
        }
        speakText(sampleText, TtsOptions(profile.rate, profile.pitch, profile.pauseMs, profile.language))
    }

    override fun synthesize(text: String, opts: TtsOptions): Flow<AudioChunk> = flow {
        val sentences = splitSentences(text)
        sentences.forEachIndexed { idx, s ->
            emit(AudioChunk(sentence = s, index = idx))
        }
    }

    fun speakText(text: String, opts: TtsOptions? = null) {
        val clean = text
            .replace(Regex("[*#`>_]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isBlank()) return

        val effectiveOpts = opts ?: TtsOptions(
            speed = _activeProfile?.rate ?: 1.0f,
            pitch = _activeProfile?.pitch ?: 1.0f,
            pauseMs = _activeProfile?.pauseMs ?: 250,
            languageTag = _activeProfile?.language ?: "en-US"
        )
        applyTtsOptions(effectiveOpts)

        val sentences = splitSentences(clean)
        _voiceState.value = VoiceState.SPEAKING
        if (ttsReady && tts != null) {
            sentences.forEachIndexed { idx, sentence ->
                val queueMode = if (idx == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val isLast = idx == sentences.lastIndex
                val uid = "yy_${UUID.randomUUID().toString().take(6)}" + if (isLast) "_LAST" else ""
                tts?.speak(sentence, queueMode, null, uid)
                if (!isLast && effectiveOpts.pauseMs > 0) {
                    tts?.playSilentUtterance(effectiveOpts.pauseMs.toLong(), TextToSpeech.QUEUE_ADD, "pause_$idx")
                }
            }
        } else {
            _statusNote.value = "Android TTS initializing — text reply shown below."
            _voiceState.value = VoiceState.IDLE
        }
    }

    override fun stop() {
        tts?.stop()
        stopListening()
        _voiceState.value = VoiceState.IDLE
    }

    fun triggerBargeIn(onInterrupted: () -> Unit) {
        // Immediate <200ms stop of TTS playback & return to Listening
        tts?.stop()
        _bargeInCount.value = _bargeInCount.value + 1
        _statusNote.value = "Barge-in triggered (<200ms): TTS stopped."
        onInterrupted()
        startListening(
            languageTag = _activeProfile?.language ?: "en-US",
            onFinal = onFinalTranscriptCallback ?: {},
            onBargeIn = onBargeInCallback ?: {}
        )
    }

    fun setThinkingState() {
        _voiceState.value = VoiceState.THINKING
    }

    fun startListening(
        languageTag: String,
        onFinal: (String) -> Unit,
        onBargeIn: () -> Unit
    ) {
        onFinalTranscriptCallback = onFinal
        onBargeInCallback = onBargeIn

        val hasAudioPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasAudioPerm) {
            _statusNote.value = "Microphone permission required for voice input."
            _voiceState.value = VoiceState.IDLE
            return
        }

        _liveTranscript.value = ""
        _statusNote.value = null
        _voiceState.value = VoiceState.LISTENING

        mainHandler.post {
            runCatching {
                speechRecognizer?.destroy()
                val recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                ) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    SpeechRecognizer.createSpeechRecognizer(context)
                } else null

                if (recognizer == null) {
                    _statusNote.value = "On-device SpeechRecognizer unavailable on this emulator — use quick voice prompt or type below."
                    startMicEnergyMonitor()
                    return@post
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _voiceState.value = VoiceState.LISTENING
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {
                        val norm = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _micEnergy.value = norm
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _micEnergy.value = 0f
                    }

                    override fun onError(error: Int) {
                        _micEnergy.value = 0f
                        _voiceState.value = VoiceState.IDLE
                        _statusNote.value = "Listening ended (code $error). Tap orb to speak again or type below."
                    }

                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull().orEmpty()
                        _liveTranscript.value = text
                        _micEnergy.value = 0f
                        if (text.isNotBlank()) {
                            _voiceState.value = VoiceState.THINKING
                            onFinal(text)
                        } else {
                            _voiceState.value = VoiceState.IDLE
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull().orEmpty()
                        if (partial.isNotBlank()) {
                            _liveTranscript.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
                speechRecognizer = recognizer
                recognizer.startListening(intent)
            }.onFailure {
                _statusNote.value = "Voice capture unavailable: ${it.message}"
                _voiceState.value = VoiceState.IDLE
            }
        }
    }

    fun stopListening() {
        vadJob?.cancel()
        vadJob = null
        _micEnergy.value = 0f
        mainHandler.post {
            runCatching { speechRecognizer?.stopListening() }
        }
        if (_voiceState.value == VoiceState.LISTENING) {
            _voiceState.value = VoiceState.IDLE
        }
    }

    /**
     * Real 16 kHz mono PCM AudioRecord energy monitor when SpeechRecognizer is absent on emulators.
     */
    private fun startMicEnergyMonitor() {
        vadJob?.cancel()
        vadJob = scope.launch(Dispatchers.IO) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                return@launch
            }
            val minBuf = AudioRecord.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) return@launch
                recorder.startRecording()
                val buf = ShortArray(1024)
                while (isActive && _voiceState.value == VoiceState.LISTENING) {
                    val read = recorder.read(buf, 0, buf.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        for (i in 0 until read) sumSq += buf[i].toDouble() * buf[i].toDouble()
                        val rms = sqrt(sumSq / read) / 16000.0
                        _micEnergy.value = rms.toFloat().coerceIn(0f, 1f)
                    }
                }
            } catch (_: Exception) {
                // Ignore emulator audio backend absence
            } finally {
                runCatching { recorder?.stop() }
                runCatching { recorder?.release() }
                _micEnergy.value = 0f
            }
        }
    }

    private fun applyTtsOptions(opts: TtsOptions) {
        tts?.setSpeechRate(opts.speed.coerceIn(0.6f, 1.8f))
        tts?.setPitch(opts.pitch.coerceIn(0.6f, 1.6f))
        val locale = if (opts.languageTag.startsWith("hi", ignoreCase = true)) {
            Locale("hi", "IN")
        } else {
            Locale.US
        }
        runCatching { tts?.language = locale }
    }

    private fun splitSentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?।])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(text) }

    fun release() {
        stop()
        mainHandler.post {
            runCatching { speechRecognizer?.destroy() }
        }
        runCatching { tts?.shutdown() }
    }
}

/**
 * Section 14 #36: Android Quick Settings Tile for Instant Offline Voice Mode.
 */
class VoiceQuickTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "YY Assistant Voice"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = "Offline AI"
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("yy_assistant_launch_route", "voice")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = android.app.PendingIntent.getActivity(
                this,
                1001,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pi)
        } else {
            launchLegacyTileActivity(intent)
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchLegacyTileActivity(intent: Intent) {
        startActivityAndCollapse(intent)
    }
}
