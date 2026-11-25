package com.example.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.DeviceTier
import com.example.core.common.formatBytes
import com.example.core.ui.HeroGradientBanner
import com.example.core.ui.InstrumentCard
import com.example.core.ui.MetricChip
import com.example.core.ui.OfflinePrivacyPill
import com.example.core.ui.SectionHeader
import com.example.core.ui.SegmentedBar
import com.example.core.ui.ThermalPill
import com.example.core.ui.TierBadge
import com.example.ui.theme.IndigoPulse
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NeuralTeal
import com.example.ui.theme.SaffronAmber
import com.example.ui.theme.StatusAmber
import com.example.viewmodel.YyMindViewModel

@Composable
fun DashboardScreen(
    viewModel: YyMindViewModel,
    onNavigateChat: () -> Unit,
    onNavigateVoice: (startTranslation: Boolean) -> Unit,
    onNavigateScan: () -> Unit,
    onNavigateLibrary: () -> Unit,
    onNavigateInstaller: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    val hardware by viewModel.hardwareProfile.collectAsStateWithLifecycle()
    val storage by viewModel.storageBreakdown.collectAsStateWithLifecycle()
    val stats by viewModel.engineStats.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val installedModels by viewModel.installedModels.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val totalChunks by viewModel.totalChunks.collectAsStateWithLifecycle()
    val isBenchRunning by viewModel.isBenchmarkRunning.collectAsStateWithLifecycle()

    var showTierOverrideDialog by remember { mutableStateOf(false) }
    var showLogoModal by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (showLogoModal) Modifier.blur(12.dp) else Modifier),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Brand Header
            item {
                HeroGradientBanner {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_yy_assistant_logo),
                                contentDescription = "YY Assistant Logo — tap to expand",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showLogoModal = true }
                                    .testTag("dashboard_logo_button")
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "YY Assistant",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OfflinePrivacyPill(airgappedMode = settings.airgappedMode)
                            }
                        }
                        IconButton(
                            onClick = onNavigateSettings,
                            modifier = Modifier.testTag("dashboard_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Open settings",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 1. Device Hardware & Tier Detection Card
            item {
                InstrumentCard(
                    modifier = Modifier.testTag("dashboard_hardware_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Memory,
                                contentDescription = "Device Hardware",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Hardware & Tier",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        ThermalPill(level = hardware.thermalLevel)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            TierBadge(
                                tier = hardware.effectiveTier,
                                isOverridden = hardware.manualOverrideTier != null
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${hardware.socModel} · ${hardware.abi} (${hardware.cpuCores} cores)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = { showTierOverrideDialog = true },
                            modifier = Modifier.testTag("tier_override_button")
                        ) {
                            Text("Override")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricChip(
                            label = "RAM (60% Budget)",
                            value = "${"%.1f".format(hardware.memoryBudgetGb)} / ${"%.1f".format(hardware.totalRamGb)} GB",
                            accent = NeuralTeal,
                            modifier = Modifier.weight(1f)
                        )
                        MetricChip(
                            label = "Free Storage",
                            value = formatBytes(hardware.freeStorageBytes),
                            accent = SaffronAmber,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hardware.benchmarkCompleted) {
                                    "Micro-benchmark: ${"%.1f".format(hardware.benchmarkTokPerSec)} tok/s · TTFT ${hardware.benchmarkLatencyMs} ms"
                                } else {
                                    "Typical speed: ${hardware.effectiveTier.speedGuide} · Target: ${hardware.effectiveTier.targetUse}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { viewModel.runMicroBenchmark() },
                            enabled = !isBenchRunning,
                            modifier = Modifier.testTag("run_micro_benchmark_button"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (isBenchRunning) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Testing…")
                            } else {
                                Icon(Icons.Outlined.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Benchmark")
                            }
                        }
                    }
                }
            }

            // 2. Active Model Card
            item {
                val isLoaded = stats.loadedModelId != null
                InstrumentCard(
                    modifier = Modifier.testTag("dashboard_active_model_card"),
                    highlightBorder = if (isLoaded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else null
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ACTIVE ON-DEVICE ENGINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stats.loadedModelName,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${stats.quant} · ${stats.contextLength} ctx (${stats.kvQuant}) · ${stats.backend.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isLoaded) {
                            OutlinedButton(
                                onClick = { viewModel.unloadActiveModel() },
                                modifier = Modifier.testTag("unload_model_button")
                            ) {
                                Icon(Icons.Rounded.Stop, contentDescription = "Unload", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Unload")
                            }
                        } else {
                            Button(
                                onClick = {
                                    val firstChat = installedModels.firstOrNull { it.modelType == "chat" || it.modelType == "vision" }
                                    if (firstChat != null) viewModel.loadModel(firstChat) else onNavigateInstaller()
                                },
                                modifier = Modifier.testTag("load_model_button")
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Load", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Load")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricChip(
                            label = "Live Speed",
                            value = if (stats.tokensPerSec > 0f) "${"%.1f".format(stats.tokensPerSec)} tok/s" else "Idle",
                            accent = SaffronAmber,
                            modifier = Modifier.weight(1f)
                        )
                        MetricChip(
                            label = "Resident RAM",
                            value = "${stats.estimatedRamMb} MB",
                            accent = NeuralTeal,
                            modifier = Modifier.weight(1f)
                        )
                        MetricChip(
                            label = "Threads",
                            value = "${stats.activeThreads} big",
                            accent = IndigoPulse,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (stats.speculativeDraftName != null || stats.loraAdapterName != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            stats.speculativeDraftName?.let {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = NeuralTeal.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "Speculative Draft: $it",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeuralTeal,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            stats.loraAdapterName?.let {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = IndigoPulse.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "LoRA: $it",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = IndigoPulse,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    stats.statusBanner?.let { banner ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = banner,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber
                        )
                    }
                }
            }

            // 3. Quick Actions Grid
            item {
                SectionHeader(
                    title = "Quick Actions",
                    subtitle = "100% local reasoning, speech, OCR & RAG"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionTile(
                            title = "New Chat",
                            subtitle = "Private LLM + tools",
                            icon = Icons.Rounded.ChatBubble,
                            accent = SaffronAmber,
                            tag = "quick_action_chat",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.createNewConversation()
                                onNavigateChat()
                            }
                        )
                        QuickActionTile(
                            title = "Voice Mode",
                            subtitle = "STT + VAD + TTS",
                            icon = Icons.Outlined.Mic,
                            accent = NeuralTeal,
                            tag = "quick_action_voice",
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateVoice(false) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionTile(
                            title = "Scan & OCR",
                            subtitle = "Camera, forms, PDF",
                            icon = Icons.Outlined.DocumentScanner,
                            accent = IndigoPulse,
                            tag = "quick_action_scan",
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateScan
                        )
                        QuickActionTile(
                            title = "Ask Library",
                            subtitle = "${documents.size} docs · $totalChunks chunks",
                            icon = Icons.Outlined.FolderCopy,
                            accent = MaterialTheme.colorScheme.primary,
                            tag = "quick_action_library",
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateLibrary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionTile(
                            title = "Translate Live",
                            subtitle = "Offline EN ↔ हिन्दी",
                            icon = Icons.Outlined.Translate,
                            accent = NeuralTeal,
                            tag = "quick_action_translate",
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigateVoice(true) }
                        )
                        QuickActionTile(
                            title = "Model Catalog",
                            subtitle = "${installedModels.size} local · 10 curated",
                            icon = Icons.Outlined.AutoAwesome,
                            accent = SaffronAmber,
                            tag = "quick_action_models",
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateInstaller
                        )
                    }
                }
            }

            // 4. Storage Meter Card
            item {
                InstrumentCard(modifier = Modifier.testTag("dashboard_storage_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Local Private Storage",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${formatBytes(storage.appTotalBytes)} used by YY Assistant · ${formatBytes(storage.freeBytes)} free",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { viewModel.clearCache() },
                            modifier = Modifier.testTag("clear_cache_button")
                        ) {
                            Icon(Icons.Outlined.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clean")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val denom = maxOf(storage.appTotalBytes, 1024L).toFloat()
                    SegmentedBar(
                        segments = listOf(
                            (storage.modelsBytes / denom) to SaffronAmber,
                            (storage.documentsBytes / denom) to NeuralTeal,
                            (storage.voicesBytes / denom) to IndigoPulse,
                            (storage.cacheBytes / denom) to StatusAmber
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StorageLegendDot("Models", formatBytes(storage.modelsBytes), SaffronAmber)
                        StorageLegendDot("Docs", formatBytes(storage.documentsBytes), NeuralTeal)
                        StorageLegendDot("Voices", formatBytes(storage.voicesBytes), IndigoPulse)
                        StorageLegendDot("Cache", formatBytes(storage.cacheBytes), StatusAmber)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    if (showTierOverrideDialog) {
        AlertDialog(
            onDismissRequest = { showTierOverrideDialog = false },
            title = { Text("Hardware Tier Override") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Detected: ${hardware.detectedTier.label} (min of RAM ${hardware.ramTier.badge}, Storage ${hardware.storageTier.badge}, Benchmark ${hardware.benchmarkTier.badge}). Overriding to a higher tier can risk out-of-memory kills.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    DeviceTier.entries.forEach { tier ->
                        val selected = hardware.effectiveTier == tier
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.overrideTier(tier)
                                    showTierOverrideDialog = false
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(tier.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${tier.ramGuide} · ${tier.defaultContext} ctx · ${tier.targetUse}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.overrideTier(null)
                    showTierOverrideDialog = false
                }) {
                    Text("Auto-Detect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTierOverrideDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showLogoModal) {
        Dialog(
            onDismissRequest = { showLogoModal = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.68f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showLogoModal = false }
                    )
                    .testTag("logo_modal_backdrop"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(24.dp)
                        .widthIn(max = 340.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.25f)),
                            shadowElevation = 16.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_yy_assistant_logo),
                                contentDescription = "YY Assistant Full Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(24.dp))
                                    .testTag("logo_modal_image")
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .size(48.dp)
                        ) {
                            IconButton(
                                onClick = { showLogoModal = false },
                                modifier = Modifier.testTag("logo_modal_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close logo preview",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "YY Assistant",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .testTag(tag)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.16f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StorageLegendDot(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = color, modifier = Modifier.size(8.dp)) {}
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.SemiBold)
        )
    }
}
