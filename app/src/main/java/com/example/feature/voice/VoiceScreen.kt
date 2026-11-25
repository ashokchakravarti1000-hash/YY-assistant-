package com.example.feature.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FrontHand
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.VoiceState
import com.example.core.ui.InstrumentCard
import com.example.core.ui.ThermalPill
import com.example.ui.theme.IndigoPulse
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NeuralTeal
import com.example.ui.theme.SaffronAmber
import com.example.ui.theme.StatusAmber
import com.example.viewmodel.YyMindViewModel

@Composable
fun VoiceScreen(
    viewModel: YyMindViewModel,
    onOpenVoiceInstaller: () -> Unit
) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val transcript by viewModel.liveTranscript.collectAsStateWithLifecycle()
    val micEnergy by viewModel.micEnergy.collectAsStateWithLifecycle()
    val bargeInCount by viewModel.bargeInCount.collectAsStateWithLifecycle()
    val lastReply by viewModel.lastVoiceReply.collectAsStateWithLifecycle()
    val voiceStatusNote by viewModel.voiceStatusNote.collectAsStateWithLifecycle()
    val voiceProfiles by viewModel.voiceProfiles.collectAsStateWithLifecycle()
    val installedModels by viewModel.installedModels.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hardware by viewModel.hardwareProfile.collectAsStateWithLifecycle()
    val translationMode by viewModel.translationMode.collectAsStateWithLifecycle()
    val translationDirection by viewModel.translationDirection.collectAsStateWithLifecycle()

    var manualUtterance by remember { mutableStateOf("") }

    val hasCustomStt = remember(installedModels) { installedModels.any { it.modelType == "stt" } }
    val hasCustomTts = remember(installedModels) { installedModels.any { it.modelType == "tts" } }
    val showOptionalBanner = !hasCustomStt && !hasCustomTts && !settings.voicePromptSkipped

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.startVoiceListening()
        } else {
            viewModel.showMessage("Microphone permission denied — you can still type prompts for spoken TTS replies.")
        }
    }

    fun launchOrToggleMic() {
        when (voiceState) {
            VoiceState.SPEAKING -> viewModel.triggerVoiceBargeIn()
            VoiceState.LISTENING -> viewModel.stopVoiceSession()
            VoiceState.THINKING -> viewModel.stopVoiceSession()
            VoiceState.IDLE -> {
                val hasPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) {
                    viewModel.startVoiceListening()
                } else {
                    micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Status Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (translationMode) "Offline Translation Mode" else "Private Voice Mode",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "AEC/NS · VAD · <200ms Barge-in · Session hint: ${settings.performanceProfile.voiceSessionHintMinutes}m",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ThermalPill(level = hardware.thermalLevel)
                }
            }

            // Section 8.2 First-time Optional Voice Add-on Card
            if (showOptionalBanner) {
                item {
                    InstrumentCard(
                        highlightBorder = SaffronAmber.copy(alpha = 0.6f),
                        modifier = Modifier.testTag("voice_optional_prompt_card")
                    ) {
                        Text("Voice Add-on Pack (Optional)", style = MaterialTheme.typography.titleMedium, color = SaffronAmber)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Voice is optional. Install a voice pack (about 100 to 400 MB) for Whisper STT + Silero VAD + Piper/Kokoro voices, or continue with zero-install Android Offline TTS.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onOpenVoiceInstaller,
                                modifier = Modifier.testTag("voice_install_pack_button")
                            ) {
                                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Install Voice Pack")
                            }
                            TextButton(
                                onClick = { viewModel.updateSettings { it.copy(voicePromptSkipped = true) } },
                                modifier = Modifier.testTag("voice_skip_pack_button")
                            ) {
                                Text("Skip (Use System TTS)")
                            }
                        }
                    }
                }
            }

            // Section 14 #33 Offline Bilingual Translation Switch
            item {
                InstrumentCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Translate, contentDescription = null, tint = NeuralTeal)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Offline Translation Mode", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Live STT → LLM → TTS bilingual conversation",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = translationMode,
                            onCheckedChange = { viewModel.toggleTranslationMode(it) },
                            modifier = Modifier.testTag("voice_translation_switch")
                        )
                    }
                    if (translationMode) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (translationDirection == "en->hi") "English → हिन्दी (Hindi)" else "हिन्दी (Hindi) → English",
                                style = MaterialTheme.typography.titleMedium,
                                color = SaffronAmber
                            )
                            FilledTonalButton(
                                onClick = { viewModel.toggleTranslationDirection() },
                                modifier = Modifier.testTag("swap_translation_direction")
                            ) {
                                Icon(Icons.Outlined.SwapHoriz, contentDescription = "Swap", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Swap")
                            }
                        }
                    }
                }
            }

            // Large Animated Neural Orb reflecting Idle -> Listening -> Thinking -> Speaking
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    VoiceNeuralOrb(
                        state = voiceState,
                        micEnergy = micEnergy,
                        onClick = { launchOrToggleMic() }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = when (voiceState) {
                            VoiceState.IDLE -> "Tap orb to speak privately"
                            VoiceState.LISTENING -> "Listening (16 kHz local audio)…"
                            VoiceState.THINKING -> "Reasoning on-device…"
                            VoiceState.SPEAKING -> "Speaking · Tap orb or Barge-in to interrupt"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Barge-in indicator & explicit trigger
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = NeuralTeal.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "Barge-in ready · Interrupts: $bargeInCount",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                color = NeuralTeal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        if (voiceState == VoiceState.SPEAKING) {
                            FilledTonalButton(
                                onClick = { viewModel.triggerVoiceBargeIn() },
                                modifier = Modifier.testTag("voice_barge_in_button")
                            ) {
                                Icon(Icons.Outlined.FrontHand, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Barge-In")
                            }
                        }
                        if (voiceState != VoiceState.IDLE) {
                            OutlinedButton(onClick = { viewModel.stopVoiceSession() }) {
                                Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stop")
                            }
                        }
                    }

                    voiceStatusNote?.let { note ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Live Transcript & Spoken Reply Card
            item {
                InstrumentCard {
                    Text("Live Transcript", style = MaterialTheme.typography.labelMedium, color = NeuralTeal)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = transcript.ifBlank { "Your spoken words appear here in real time…" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (transcript.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    if (lastReply.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("YY Assistant Spoken Reply", style = MaterialTheme.typography.labelMedium, color = SaffronAmber)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = lastReply, style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    // Graceful Fallback: Type prompt to test spoken TTS reply even without STT pack or emulator mic
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualUtterance,
                            onValueChange = { manualUtterance = it },
                            placeholder = {
                                Text(
                                    if (translationMode) "Type phrase to translate & speak…" else "Or type here to hear TTS reply…"
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("voice_text_fallback_input"),
                            shape = RoundedCornerShape(14.dp)
                        )
                        IconButton(
                            onClick = {
                                if (manualUtterance.isNotBlank()) {
                                    val q = manualUtterance
                                    manualUtterance = ""
                                    viewModel.processVoiceTurn(q)
                                }
                            },
                            modifier = Modifier.testTag("voice_text_fallback_send")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Speak reply", tint = SaffronAmber)
                        }
                    }
                }
            }

            // Active Voice Selector Bar
            item {
                InstrumentCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.RecordVoiceOver, contentDescription = null, tint = SaffronAmber)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Active Voice Profile", style = MaterialTheme.typography.titleSmall)
                        }
                        TextButton(onClick = onOpenVoiceInstaller) {
                            Text("Voice Packs")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        voiceProfiles.forEach { vp ->
                            val selected = settings.activeVoiceId == vp.id
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    viewModel.selectVoiceProfile(vp)
                                    viewModel.playVoicePreview(vp)
                                },
                                label = { Text("${vp.name} (${vp.language})") }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun VoiceNeuralOrb(
    state: VoiceState,
    micEnergy: Float,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "voice_orb")
    val pulse by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    VoiceState.IDLE -> 2400
                    VoiceState.LISTENING -> 900
                    VoiceState.THINKING -> 650
                    VoiceState.SPEAKING -> 1100
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val coreColor = when (state) {
        VoiceState.IDLE -> SaffronAmber
        VoiceState.LISTENING -> NeuralTeal
        VoiceState.THINKING -> IndigoPulse
        VoiceState.SPEAKING -> SaffronAmber
    }
    val ringColor = when (state) {
        VoiceState.IDLE -> NeuralTeal
        VoiceState.LISTENING -> SaffronAmber
        VoiceState.THINKING -> NeuralTeal
        VoiceState.SPEAKING -> IndigoPulse
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(200.dp)
            .clickable(onClick = onClick)
            .testTag("voice_neural_orb")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.30f
            val energyBoost = micEnergy * 24f

            // Outer ambient halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(coreColor.copy(alpha = 0.32f), Color.Transparent),
                    center = center,
                    radius = baseRadius * 1.65f * pulse + energyBoost
                ),
                radius = baseRadius * 1.65f * pulse + energyBoost,
                center = center
            )

            // Secondary harmonic ring
            drawCircle(
                color = ringColor.copy(alpha = 0.45f),
                radius = baseRadius * 1.24f * pulse + energyBoost * 0.6f,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // Inner glowing sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(coreColor.copy(alpha = 0.95f), coreColor.copy(alpha = 0.45f)),
                    center = center,
                    radius = baseRadius * (0.95f + micEnergy * 0.25f)
                ),
                radius = baseRadius * (0.95f + micEnergy * 0.25f),
                center = center
            )
        }

        Icon(
            imageVector = when (state) {
                VoiceState.IDLE -> Icons.Outlined.Mic
                VoiceState.LISTENING, VoiceState.SPEAKING -> Icons.Outlined.GraphicEq
                VoiceState.THINKING -> Icons.Outlined.RecordVoiceOver
            },
            contentDescription = "Toggle voice mode",
            tint = Color(0xFF0A0D14),
            modifier = Modifier.size(38.dp)
        )
    }
}
