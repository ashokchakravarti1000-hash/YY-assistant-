package com.example.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.AcceleratorBackend
import com.example.core.common.PerformanceProfile
import com.example.core.common.SamplingPreset
import com.example.core.ui.InstrumentCard
import com.example.core.ui.MetricChip
import com.example.core.ui.SectionHeader
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NeuralTeal
import com.example.ui.theme.SaffronAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.viewmodel.YyMindViewModel

@Composable
fun SettingsScreen(
    viewModel: YyMindViewModel
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val keystoreStatus by viewModel.keystoreStatus.collectAsStateWithLifecycle()
    val voiceProfiles by viewModel.voiceProfiles.collectAsStateWithLifecycle()
    val installedModels by viewModel.installedModels.collectAsStateWithLifecycle()
    val isBenchRunning by viewModel.isBenchmarkRunning.collectAsStateWithLifecycle()
    val lastBenchmark by viewModel.lastBenchmark.collectAsStateWithLifecycle()
    val evalResults by viewModel.evalResults.collectAsStateWithLifecycle()

    var showSecureWipeDialog by remember { mutableStateOf(false) }
    var showBackupPassphraseDialog by remember { mutableStateOf<String?>(null) } // "EXPORT" or "IMPORT"
    var backupPassphrase by remember { mutableStateOf("") }
    var showBlueprintDialog by remember { mutableStateOf(false) }

    val exportArchiveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null && backupPassphrase.length >= 4) {
            viewModel.exportEncryptedArchive(backupPassphrase, uri)
            backupPassphrase = ""
        }
    }

    val importArchiveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && backupPassphrase.length >= 4) {
            viewModel.importEncryptedArchive(backupPassphrase, uri)
            backupPassphrase = ""
        }
    }

    val exportLogLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) viewModel.exportLocalLogs(uri)
    }

    val draftCandidates = remember(installedModels) {
        installedModels.filter { (it.modelType == "chat" || it.modelType == "vision") && it.tier == 1 }
    }
    val loraAdapters = remember(installedModels) {
        installedModels.filter { it.modelType == "lora" }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SectionHeader(
                    title = "Settings & Privacy Vault",
                    subtitle = "Performance · Voice Gallery · Security · Offline Benchmarks",
                    trailing = {
                        TextButton(onClick = { showBlueprintDialog = true }) {
                            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Blueprint")
                        }
                    }
                )
            }

            // 0. Appearance — Light Mode (Default) & Dark Mode Buttons
            item {
                val isLightMode = settings.darkThemeOverride != "DARK"
                InstrumentCard {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (isLightMode) "Light Mode active (default)" else "Dark Mode active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isLightMode) {
                            Button(
                                onClick = { viewModel.updateSettings { it.copy(darkThemeOverride = "LIGHT") } },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_light_mode_button")
                            ) {
                                Icon(Icons.Outlined.LightMode, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Light Mode")
                            }
                            OutlinedButton(
                                onClick = { viewModel.updateSettings { it.copy(darkThemeOverride = "DARK") } },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_dark_mode_button")
                            ) {
                                Icon(Icons.Outlined.DarkMode, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Dark Mode")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.updateSettings { it.copy(darkThemeOverride = "LIGHT") } },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_light_mode_button")
                            ) {
                                Icon(Icons.Outlined.LightMode, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Light Mode")
                            }
                            Button(
                                onClick = { viewModel.updateSettings { it.copy(darkThemeOverride = "DARK") } },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_dark_mode_button")
                            ) {
                                Icon(Icons.Outlined.DarkMode, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Dark Mode")
                            }
                        }
                    }
                }
            }

            // 1. Performance, Thermal & Accelerator Strategy (Section 10 & 14 #37 #38)
            item {
                InstrumentCard {
                    Text("Performance, Thermal & Acceleration", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Power & Thermal Profile", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PerformanceProfile.entries.forEach { prof ->
                            FilterChip(
                                selected = settings.performanceProfile == prof,
                                onClick = { viewModel.updateSettings { it.copy(performanceProfile = prof) } },
                                label = { Text(prof.label) },
                                modifier = Modifier.testTag("profile_${prof.name.lowercase()}")
                            )
                        }
                    }
                    Text(
                        text = settings.performanceProfile.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Hardware Backend (CPU / GPU / NPU)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AcceleratorBackend.entries.forEach { b ->
                            FilterChip(
                                selected = settings.acceleratorBackend == b,
                                onClick = { viewModel.updateSettings { it.copy(acceleratorBackend = b) } },
                                label = { Text(b.label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Context Window Length: ${settings.contextLength} tokens (q8_0 KV cache)", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(2048, 4096, 8192, 16384, 32768).forEach { ctx ->
                            FilterChip(
                                selected = settings.contextLength == ctx,
                                onClick = { viewModel.updateSettings { it.copy(contextLength = ctx) } },
                                label = { Text("${ctx / 1024}K") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Sampling Preset", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SamplingPreset.entries.forEach { p ->
                            FilterChip(
                                selected = settings.samplingPreset == p,
                                onClick = { viewModel.updateSettings { it.copy(samplingPreset = p) } },
                                label = { Text(p.label) }
                            )
                        }
                    }

                    // Speculative Decoding Draft Pairing (Section 10)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Speculative Decoding Draft Model (Tier 2/3 1.5–2× speed)", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = settings.speculativeDraftModelId == null,
                            onClick = { viewModel.updateSettings { it.copy(speculativeDraftModelId = null) } },
                            label = { Text("Off") }
                        )
                        draftCandidates.forEach { d ->
                            FilterChip(
                                selected = settings.speculativeDraftModelId == d.id,
                                onClick = { viewModel.updateSettings { it.copy(speculativeDraftModelId = d.id) } },
                                label = { Text(d.displayName.take(20)) }
                            )
                        }
                    }

                    // LoRA Adapter Support (Section 14 #37)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("LoRA Adapter Overlay", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = settings.activeLoraAdapterId == null,
                            onClick = { viewModel.updateSettings { it.copy(activeLoraAdapterId = null) } },
                            label = { Text("None") }
                        )
                        loraAdapters.forEach { lora ->
                            FilterChip(
                                selected = settings.activeLoraAdapterId == lora.id,
                                onClick = { viewModel.updateSettings { it.copy(activeLoraAdapterId = lora.id) } },
                                label = { Text(lora.displayName) }
                            )
                        }
                    }
                    if (loraAdapters.isEmpty()) {
                        Text(
                            text = "Import a *lora*.gguf adapter in Models → Custom to attach fine-tunes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. Model Benchmarking & Offline Quality Eval Suite (Sections 12 & 14 #34)
            item {
                InstrumentCard(highlightBorder = NeuralTeal.copy(alpha = 0.45f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Model Benchmark & Quality Evals", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Measure real device tok/s, TTFT, RAM & run 4-case offline regression suite",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.runFullModelBenchmarkAndEvals() },
                            enabled = !isBenchRunning,
                            modifier = Modifier.testTag("run_full_benchmark_button")
                        ) {
                            if (isBenchRunning) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Run Suite")
                            }
                        }
                    }

                    lastBenchmark?.let { b ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricChip("Decode", "${"%.1f".format(b.tokensPerSec)} tok/s", Modifier.weight(1f), SaffronAmber)
                            MetricChip("TTFT", "${b.ttftMs} ms", Modifier.weight(1f), NeuralTeal)
                            MetricChip("Peak RAM", "${b.peakRamMb} MB", Modifier.weight(1f))
                        }
                    }

                    if (evalResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        evalResults.forEach { ev ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = if (ev.passed) StatusGreen else StatusRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ev.name, style = MaterialTheme.typography.labelMedium)
                                    Text(ev.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${ev.latencyMs}ms", style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily))
                            }
                        }
                    }
                }
            }

            // 3. Multi-Voice TTS Gallery with Acoustic Controls & Play Sample (Section 6.5)
            item {
                InstrumentCard {
                    Text("TTS Voice Gallery (Multi-Model Selector)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Distinct acoustic profiles with live sample preview, speed, pitch, and sentence pause.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val activeVoice = voiceProfiles.firstOrNull { it.id == settings.activeVoiceId } ?: voiceProfiles.firstOrNull()

                    voiceProfiles.forEach { vp ->
                        val isSelected = vp.id == settings.activeVoiceId
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isSelected) BorderStroke(1.dp, SaffronAmber) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { viewModel.selectVoiceProfile(vp) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(vp.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${vp.engine} · ${vp.language} · ${vp.genderPresentation} · ${vp.tags}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.playVoicePreview(vp) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Play sample", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sample")
                                }
                            }
                        }
                    }

                    activeVoice?.let { av ->
                        var rate by remember(av.id, av.rate) { mutableFloatStateOf(av.rate) }
                        var pitch by remember(av.id, av.pitch) { mutableFloatStateOf(av.pitch) }
                        var pauseMs by remember(av.id, av.pauseMs) { mutableIntStateOf(av.pauseMs) }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Speed: ${"%.2f".format(rate)}×", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = rate,
                            onValueChange = { rate = it },
                            valueRange = 0.65f..1.6f,
                            onValueChangeFinished = { viewModel.updateVoiceAcousticControls(av, rate, pitch, pauseMs) }
                        )
                        Text("Pitch Shift: ${"%.2f".format(pitch)}×", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = pitch,
                            onValueChange = { pitch = it },
                            valueRange = 0.7f..1.4f,
                            onValueChangeFinished = { viewModel.updateVoiceAcousticControls(av, rate, pitch, pauseMs) }
                        )
                        Text("Sentence Pause: ${pauseMs} ms", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = pauseMs.toFloat(),
                            onValueChange = { pauseMs = it.toInt() },
                            valueRange = 50f..600f,
                            onValueChangeFinished = { viewModel.updateVoiceAcousticControls(av, rate, pitch, pauseMs) }
                        )
                    }
                }
            }

            // 4. Privacy, Security & Encrypted Backup (Section 11 & 14 #35)
            item {
                InstrumentCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Security, contentDescription = null, tint = NeuralTeal)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Security, Privacy & Encrypted Backups", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Hardware Keystore Status Pill
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = NeuralTeal.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = NeuralTeal, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Android Keystore AES-256-GCM (${keystoreStatus.wrappedKeyFingerprint})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeuralTeal
                                )
                                Text(
                                    "Zero analytics · Zero crash telemetry · Cleartext HTTP denied · allowBackup=false",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    SettingToggleRow(
                        title = "Airgapped Mode (Sideload Only)",
                        subtitle = "Hides all download buttons; enforces 100% local ZIP/GGUF import",
                        checked = settings.airgappedMode,
                        tag = "setting_airgapped_switch",
                        onCheckedChange = { checked -> viewModel.updateSettings { it.copy(airgappedMode = checked) } }
                    )
                    SettingToggleRow(
                        title = "Block Screenshots (FLAG_SECURE)",
                        subtitle = "Prevents screenshots, screen recording, and recents thumbnail capture",
                        checked = settings.flagSecureEnabled,
                        tag = "setting_flag_secure_switch",
                        onCheckedChange = { checked -> viewModel.updateSettings { it.copy(flagSecureEnabled = checked) } }
                    )
                    SettingToggleRow(
                        title = "App Lock (Device Biometric / PIN)",
                        subtitle = "Require local device authentication before opening YY Assistant",
                        checked = settings.appLockEnabled,
                        tag = "setting_app_lock_switch",
                        onCheckedChange = { checked -> viewModel.updateSettings { it.copy(appLockEnabled = checked) } }
                    )
                    SettingToggleRow(
                        title = "Material You Dynamic Color",
                        subtitle = "Adapt surfaces to wallpaper palette on Android 12+",
                        checked = settings.dynamicColor,
                        tag = "setting_dynamic_color_switch",
                        onCheckedChange = { checked -> viewModel.updateSettings { it.copy(dynamicColor = checked) } }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilledTonalButton(
                            onClick = { showBackupPassphraseDialog = "EXPORT" },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_encrypted_backup_button")
                        ) {
                            Icon(Icons.Outlined.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Vault")
                        }
                        OutlinedButton(
                            onClick = { showBackupPassphraseDialog = "IMPORT" },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_encrypted_backup_button")
                        ) {
                            Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore Vault")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { exportLogLauncher.launch("yy_assistant_local_logs.txt") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Logs")
                        }
                        if (settings.appLockEnabled) {
                            OutlinedButton(
                                onClick = { viewModel.lockAppNow() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Lock Now")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { showSecureWipeDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("secure_wipe_button")
                    ) {
                        Icon(Icons.Outlined.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Secure Wipe All Local Data")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Encrypted Local Archive Passphrase Dialog (Section 14 #35)
    showBackupPassphraseDialog?.let { mode ->
        AlertDialog(
            onDismissRequest = { showBackupPassphraseDialog = null },
            title = { Text(if (mode == "EXPORT") "Encrypt Local Archive" else "Decrypt Local Archive") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Archives are encrypted with PBKDF2-HMAC-SHA256 + AES-256-GCM entirely offline. Nothing touches the cloud.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        label = { Text("Archive Passphrase (4+ chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = backupPassphrase.length >= 4,
                    onClick = {
                        showBackupPassphraseDialog = null
                        if (mode == "EXPORT") {
                            exportArchiveLauncher.launch("yy_assistant_backup_${System.currentTimeMillis()}.yyassistant.enc")
                        } else {
                            importArchiveLauncher.launch(arrayOf("*/*"))
                        }
                    }
                ) {
                    Text(if (mode == "EXPORT") "Choose Destination" else "Pick Archive File")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupPassphraseDialog = null }) { Text("Cancel") }
            }
        )
    }

    // Secure Wipe Confirmation Dialog
    if (showSecureWipeDialog) {
        AlertDialog(
            onDismissRequest = { showSecureWipeDialog = false },
            title = { Text("Confirm Secure Wipe") },
            text = {
                Text("This permanently erases all local conversations, RAG documents, vector chunks, downloaded models, voice packs, and caches on this device.")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                    onClick = {
                        showSecureWipeDialog = false
                        viewModel.executeSecureWipe()
                    }
                ) { Text("Wipe Permanently") }
            },
            dismissButton = {
                TextButton(onClick = { showSecureWipeDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Section 15 & 16 Blueprint Architecture & Risk Mitigations Dialog
    if (showBlueprintDialog) {
        AlertDialog(
            onDismissRequest = { showBlueprintDialog = false },
            title = { Text("YY Assistant Architecture & Safety") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• Zero cloud inference: All chat, OCR, RAG, STT, and TTS run on-device.", style = MaterialTheme.typography.bodySmall)
                    Text("• OOM Protection: Strict 60% RAM budget, q8_0 KV cache, 16 GB gate for 24B–30B models, onTrimMemory cascade.", style = MaterialTheme.typography.bodySmall)
                    Text("• Thermal Guard: Adaptive thread/batch reduction at MODERATE, automatic pause at SEVERE.", style = MaterialTheme.typography.bodySmall)
                    Text("• Untrusted File Hygiene: Magic-byte Step 0 detector, ZIP-slip & bomb caps, SHA-256 verification, prompt-injection tagging, and explicit approval for local tools.", style = MaterialTheme.typography.bodySmall)
                    Text("• Licensing: Open-weight models are never redistributed inside the APK — downloaded directly from upstream hosts or sideloaded by user.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlueprintDialog = false }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
