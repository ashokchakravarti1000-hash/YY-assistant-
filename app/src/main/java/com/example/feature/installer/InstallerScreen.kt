package com.example.feature.installer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.CatalogModel
import com.example.core.common.CompatibilityStatus
import com.example.core.common.DownloadProgress
import com.example.core.common.DownloadStatus
import com.example.core.common.SamplingPreset
import com.example.core.common.VoiceCatalogItem
import com.example.core.common.formatBytes
import com.example.core.storage.ClassifiedModelType
import com.example.core.storage.InstalledModelEntity
import com.example.core.storage.LocalImportPreview
import com.example.core.ui.CompatibilityDotBadge
import com.example.core.ui.EmptyStatePanel
import com.example.core.ui.InstrumentCard
import com.example.core.ui.OfflinePrivacyPill
import com.example.core.ui.SectionHeader
import com.example.core.ui.UnverifiedSourceChip
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NeuralTeal
import com.example.ui.theme.SaffronAmber
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.viewmodel.YyMindViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InstallerScreen(
    viewModel: YyMindViewModel,
    initialTab: Int = 0
) {
    val hardware by viewModel.hardwareProfile.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val installedModels by viewModel.installedModels.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val engineStats by viewModel.engineStats.collectAsStateWithLifecycle()
    val importPreview by viewModel.localImportPreview.collectAsStateWithLifecycle()
    val importProgress by viewModel.localImportProgress.collectAsStateWithLifecycle()

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var advancedTier1Override by remember { mutableStateOf(false) }
    var editingCustomModel by remember { mutableStateOf<InstalledModelEntity?>(null) }
    var pendingExportModel by remember { mutableStateOf<InstalledModelEntity?>(null) }

    val localFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.inspectLocalModelUri(uri)
    }

    val exportZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val target = pendingExportModel
        pendingExportModel = null
        if (uri != null && target != null) {
            viewModel.exportCustomModelToZip(target, uri)
        }
    }

    val installedById = remember(installedModels) { installedModels.associateBy { it.catalogId ?: it.id } }
    val customModels = remember(installedModels) {
        installedModels.filter { it.source == "local_zip" || it.source == "sideload" || it.source == "bundled" }
    }

    val tabLabels = listOf(
        "Lite (T1)" + if (hardware.effectiveTier.level == 1) " ★" else "",
        "Balanced (T2)" + if (hardware.effectiveTier.level == 2) " ★" else "",
        "Pro (T3)" + if (hardware.effectiveTier.level == 3) " ★" else "",
        "Custom (${customModels.size})",
        "Voice Add-on"
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            // Header + Add Local Model CTA
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Model Installer", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = "Catalog ${viewModel.catalogVersion} · 10 curated GGUF + Local ZIP/TAR",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { localFilePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.testTag("add_local_model_button")
                    ) {
                        Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Local")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OfflinePrivacyPill(airgappedMode = settings.airgappedMode)
                    if (!settings.airgappedMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Wi-Fi only", style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = settings.wifiOnlyDownloads,
                                onCheckedChange = { checked ->
                                    viewModel.updateSettings { it.copy(wifiOnlyDownloads = checked) }
                                },
                                modifier = Modifier.testTag("wifi_only_switch")
                            )
                        }
                    }
                }
            }

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                tabLabels.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(label, maxLines = 1) },
                        modifier = Modifier.testTag("installer_tab_$idx")
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    0, 1, 2 -> {
                        val tabTier = selectedTab + 1
                        val tierModels = viewModel.catalogModels.filter { it.tier == tabTier }
                        val isLockedPreview = (hardware.effectiveTier.level == 1 && tabTier > 1 && !advancedTier1Override) ||
                            (hardware.effectiveTier.level == 2 && tabTier == 3 && !advancedTier1Override)

                        if (isLockedPreview) {
                            item {
                                InstrumentCard(highlightBorder = StatusAmber.copy(alpha = 0.6f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = StatusAmber)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Tier $tabTier Preview Locked (${hardware.effectiveTier.badge} detected)",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = StatusAmber
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Models in this tier may exceed your 60% RAM budget (${"%.1f".format(hardware.memoryBudgetGb)} GB) and risk out-of-memory kills.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { advancedTier1Override = true },
                                        modifier = Modifier.testTag("installer_advanced_override_button")
                                    ) {
                                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Enable Advanced Override")
                                    }
                                }
                            }
                        }

                        items(tierModels, key = { it.id }) { model ->
                            val compat = viewModel.checkCompatibility(model)
                            val installed = installedById[model.id]
                            val dl = downloads[model.id]
                            val isRecommended = model.tier == hardware.effectiveTier.level
                            val isActive = installed != null && engineStats.loadedModelId == installed.id

                            CatalogModelCard(
                                model = model,
                                compatibility = compat,
                                isRecommended = isRecommended,
                                isLocked = isLockedPreview,
                                airgappedMode = settings.airgappedMode,
                                installed = installed,
                                isActive = isActive,
                                download = dl,
                                onDownload = { viewModel.downloadCatalogModel(model) },
                                onPause = { viewModel.pauseDownload(model.id) },
                                onSideload = { localFilePicker.launch(arrayOf("*/*")) },
                                onLoad = { installed?.let { viewModel.loadModel(it) } },
                                onVerify = { installed?.let { viewModel.verifyInstalledModel(it) } },
                                onDelete = { installed?.let { viewModel.deleteInstalledModel(it) } }
                            )
                        }
                    }

                    3 -> {
                        // Custom & Local Imported Models Tab (Section 8.1)
                        item {
                            InstrumentCard {
                                Text("Add Any Local Model (GGUF, ZIP, TAR.GZ, TAR.BZ2)", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Step 0 inspects leading magic bytes (GGUF, PK ZIP, BZh, 1F 8B), blocks Git LFS pointers & HTML error pages, enforces ZIP-slip & bomb caps, validates GGUF metadata, and computes SHA-256.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { localFilePicker.launch(arrayOf("*/*")) },
                                    modifier = Modifier.testTag("pick_local_zip_button")
                                ) {
                                    Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Pick GGUF / ZIP / TAR from Device")
                                }
                            }
                        }

                        if (customModels.isEmpty()) {
                            item {
                                EmptyStatePanel(
                                    icon = Icons.Outlined.Archive,
                                    title = "No Custom Models Yet",
                                    subtitle = "Import a GGUF file, split GGUF ZIP, LoRA adapter, or sherpa-onnx voice archive from Downloads, USB, or SD card."
                                )
                            }
                        } else {
                            items(customModels, key = { it.id }) { model ->
                                val isActive = engineStats.loadedModelId == model.id
                                CustomInstalledModelCard(
                                    model = model,
                                    isActive = isActive,
                                    onLoad = { viewModel.loadModel(model) },
                                    onEdit = { editingCustomModel = model },
                                    onVerify = { viewModel.verifyInstalledModel(model) },
                                    onExportZip = {
                                        pendingExportModel = model
                                        exportZipLauncher.launch("${model.id}.zip")
                                    },
                                    onDelete = { viewModel.deleteInstalledModel(model) }
                                )
                            }
                        }
                    }

                    4 -> {
                        // Optional Voice Add-on Pack Tab (Section 8.2)
                        val starterRecommendation = when (hardware.effectiveTier.level) {
                            1 -> "Tier 1 Starter: Whisper Base (q5_1) + Silero VAD + Piper Lessac / Rohan (Hindi)"
                            2 -> "Tier 2 Starter: Whisper Small (q5_1) + Silero VAD + Kokoro-82M (103 speakers)"
                            else -> "Tier 3 Starter: Whisper Large-v3-Turbo + Silero VAD + Kokoro-82M + Piper Hindi"
                        }

                        item {
                            InstrumentCard(highlightBorder = NeuralTeal.copy(alpha = 0.5f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = NeuralTeal)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Optional Voice Add-on Pack", style = MaterialTheme.typography.titleMedium)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$starterRecommendation. Chat & RAG work completely without voice installed; install pieces individually or import via ZIP.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val groups = listOf(
                            "stt" to "Speech Recognition (whisper.cpp GGML)",
                            "vad" to "End-of-Speech Detection (Silero VAD)",
                            "tts" to "Voices (sherpa-onnx Piper / Kokoro / Matcha)"
                        )
                        groups.forEach { (cat, title) ->
                            item {
                                SectionHeader(title = title)
                            }
                            val itemsInGroup = viewModel.voiceCatalog.filter { it.category == cat }
                            items(itemsInGroup, key = { it.id }) { pack ->
                                val installed = installedById[pack.id]
                                val dl = downloads[pack.id]
                                VoicePackCard(
                                    item = pack,
                                    installed = installed,
                                    download = dl,
                                    airgappedMode = settings.airgappedMode,
                                    deviceTier = hardware.effectiveTier.level,
                                    onDownload = { viewModel.downloadVoicePack(pack) },
                                    onPause = { viewModel.pauseDownload(pack.id) },
                                    onImportZip = { localFilePicker.launch(arrayOf("*/*")) },
                                    onDelete = { installed?.let { viewModel.deleteInstalledModel(it) } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Step 0 + Contents Scan Local Import Preview Dialog (Section 8.1)
    importPreview?.let { preview ->
        LocalImportPreviewDialog(
            preview = preview,
            progress = importProgress,
            onDismiss = { viewModel.dismissLocalImportPreview() },
            onConfirm = { name, type, tier -> viewModel.confirmLocalImport(name, type, tier) }
        )
    }

    // Edit Custom Model Dialog
    editingCustomModel?.let { model ->
        EditCustomModelDialog(
            model = model,
            onDismiss = { editingCustomModel = null },
            onSave = { name, tpl, preset, ctx, verified ->
                viewModel.updateCustomModel(model, name, tpl, preset, ctx, verified)
                editingCustomModel = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogModelCard(
    model: CatalogModel,
    compatibility: CompatibilityStatus,
    isRecommended: Boolean,
    isLocked: Boolean,
    airgappedMode: Boolean,
    installed: InstalledModelEntity?,
    isActive: Boolean,
    download: DownloadProgress?,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onSideload: () -> Unit,
    onLoad: () -> Unit,
    onVerify: () -> Unit,
    onDelete: () -> Unit
) {
    val gateReason = when {
        model.requires16GbGate && compatibility == CompatibilityStatus.BLOCKED -> "Requires 16 GB RAM"
        compatibility == CompatibilityStatus.BLOCKED -> "Exceeds 60% RAM"
        compatibility == CompatibilityStatus.WARNING -> "Tight RAM"
        else -> "Fits Budget"
    }

    InstrumentCard(
        modifier = Modifier.testTag("catalog_model_${model.id}"),
        highlightBorder = if (isActive) SaffronAmber else if (isRecommended) NeuralTeal.copy(alpha = 0.45f) else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                    if (isRecommended) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = NeuralTeal.copy(alpha = 0.16f)
                        ) {
                            Text(
                                "Recommended",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeuralTeal,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${model.params} · ${model.quant} · ${formatBytes(model.totalBundleBytes)} · ${model.minRamGb} GB+ RAM",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CompatibilityDotBadge(status = compatibility, reasonHint = gateReason)
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = model.strengths,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            model.capabilities.forEach { cap ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = cap.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
            if (model.projectorSizeBytes > 0) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SaffronAmber.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "+mmproj (${formatBytes(model.projectorSizeBytes)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = SaffronAmber,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Text(
                    text = model.license,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }

        // Download progress bar if active
        if (download != null && download.status != DownloadStatus.COMPLETED) {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { download.progressFraction },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when (download.status) {
                        DownloadStatus.VERIFYING -> "Verifying SHA-256…"
                        DownloadStatus.PAUSED -> "Paused · ${formatBytes(download.downloadedBytes)}"
                        DownloadStatus.FAILED -> download.errorMessage ?: "Failed"
                        else -> "${formatBytes(download.downloadedBytes)} / ${formatBytes(download.totalBytes)}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (download.status == DownloadStatus.FAILED) StatusRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (download.speedBytesPerSec > 0) {
                    Text(
                        text = "${formatBytes(download.speedBytesPerSec)}/s",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (installed != null) {
                Button(
                    onClick = onLoad,
                    enabled = !isActive,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (isActive) Icons.Rounded.CheckCircle else Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isActive) "Loaded" else "Load")
                }
                IconButton(onClick = onVerify) {
                    Icon(Icons.Outlined.VerifiedUser, contentDescription = "Verify SHA-256")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete model", tint = StatusRed)
                }
            } else {
                val isDownloading = download?.status == DownloadStatus.DOWNLOADING
                if (!airgappedMode) {
                    if (isDownloading) {
                        OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pause")
                        }
                    } else {
                        Button(
                            onClick = onDownload,
                            enabled = !isLocked && compatibility != CompatibilityStatus.BLOCKED,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (download?.status == DownloadStatus.PAUSED) "Resume" else "Download")
                        }
                    }
                }
                OutlinedButton(
                    onClick = onSideload,
                    enabled = !isLocked,
                    modifier = if (airgappedMode) Modifier.weight(1f) else Modifier
                ) {
                    Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (airgappedMode) "Import Model File" else "Sideload")
                }
            }
        }
    }
}

@Composable
private fun CustomInstalledModelCard(
    model: InstalledModelEntity,
    isActive: Boolean,
    onLoad: () -> Unit,
    onEdit: () -> Unit,
    onVerify: () -> Unit,
    onExportZip: () -> Unit,
    onDelete: () -> Unit
) {
    InstrumentCard(highlightBorder = if (isActive) SaffronAmber else null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${model.modelType.uppercase()} · ${model.quant} · ${formatBytes(model.sizeBytes)} · ${model.chatTemplate} · ${model.contextLength} ctx",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!model.userVerified) {
                UnverifiedSourceChip()
            } else {
                Surface(shape = RoundedCornerShape(6.dp), color = StatusGreen.copy(alpha = 0.14f)) {
                    Text(
                        text = model.source.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusGreen,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "SHA-256: ${model.sha256.take(24)}…",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (model.modelType == "chat" || model.modelType == "vision") {
                Button(onClick = onLoad, enabled = !isActive, modifier = Modifier.weight(1f)) {
                    Text(if (isActive) "Active" else "Load")
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Configure custom model")
            }
            IconButton(onClick = onVerify) {
                Icon(Icons.Outlined.VerifiedUser, contentDescription = "Verify hash")
            }
            if (model.source != "bundled") {
                IconButton(onClick = onExportZip) {
                    Icon(Icons.Outlined.Archive, contentDescription = "Export to ZIP")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = StatusRed)
                }
            }
        }
    }
}

@Composable
private fun VoicePackCard(
    item: VoiceCatalogItem,
    installed: InstalledModelEntity?,
    download: DownloadProgress?,
    airgappedMode: Boolean,
    deviceTier: Int,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onImportZip: () -> Unit,
    onDelete: () -> Unit
) {
    val tierWarn = item.tier > deviceTier
    InstrumentCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${item.engine} · ${item.language} · ${formatBytes(item.sizeBytes)} · ${item.license}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (tierWarn) {
                Surface(shape = RoundedCornerShape(6.dp), color = StatusAmber.copy(alpha = 0.16f)) {
                    Text(
                        "Tier ${item.tier}+ RAM",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusAmber,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (download != null && download.status == DownloadStatus.DOWNLOADING) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(progress = { download.progressFraction }, modifier = Modifier.fillMaxWidth())
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (installed != null) {
                Surface(shape = RoundedCornerShape(8.dp), color = StatusGreen.copy(alpha = 0.14f)) {
                    Text(
                        "Installed",
                        style = MaterialTheme.typography.labelMedium,
                        color = StatusGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = StatusRed)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            } else {
                if (!airgappedMode) {
                    val running = download?.status == DownloadStatus.DOWNLOADING
                    Button(
                        onClick = if (running) onPause else onDownload,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (running) "Pause" else "Download")
                    }
                }
                OutlinedButton(
                    onClick = onImportZip,
                    modifier = if (airgappedMode) Modifier.weight(1f) else Modifier
                ) {
                    Text("Import ZIP")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalImportPreviewDialog(
    preview: LocalImportPreview,
    progress: Float?,
    onDismiss: () -> Unit,
    onConfirm: (String, ClassifiedModelType, Int) -> Unit
) {
    var modelName by remember(preview) { mutableStateOf(preview.suggestedName) }
    var selectedType by remember(preview) { mutableStateOf(preview.classifiedType) }
    var selectedTier by remember(preview) { mutableIntStateOf(1) }

    AlertDialog(
        onDismissRequest = { if (progress == null) onDismiss() },
        title = { Text("Step 0: Local File Inspection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Detected Format: ${preview.fileDetection.kind.label} (Magic: ${preview.fileDetection.magicHex.ifBlank { "N/A" }})",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (preview.safetyError != null) StatusRed else NeuralTeal
                )
                if (preview.safetyError != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StatusRed.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = preview.safetyError,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusRed,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Uncompressed Size: ${formatBytes(preview.estimatedUncompressedBytes)} · Files: ${preview.entries.size}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    preview.ggufMetadata?.let { meta ->
                        if (meta.isValid) {
                            Text(
                                text = "GGUF v${meta.version} · Arch: ${meta.architecture} · ${meta.quantization} · ${meta.contextLength} ctx · Template: ${meta.chatTemplate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeuralTeal
                            )
                        }
                    }
                    if (!preview.fitsRamBudget) {
                        Text(
                            text = "Warning: Model + KV cache exceeds your 60% RAM budget.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber
                        )
                    }
                    if (!preview.hasEnoughDiskSpace) {
                        Text(
                            text = "Blocked: Requires 1.2× free disk space.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusRed
                        )
                    }
                    Text("Model Classification:", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            ClassifiedModelType.CHAT_MODEL,
                            ClassifiedModelType.VISION_MODEL,
                            ClassifiedModelType.STT_WHISPER,
                            ClassifiedModelType.TTS_VOICE,
                            ClassifiedModelType.LORA_ADAPTER
                        ).forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type.dbType.uppercase()) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 2, 3).forEach { t ->
                            FilterChip(
                                selected = selectedTier == t,
                                onClick = { selectedTier = t },
                                label = { Text("Tier $t") }
                            )
                        }
                    }
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text("Extracting & computing SHA-256 (${(progress * 100).toInt()}%)…", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            if (preview.safetyError == null && preview.hasEnoughDiskSpace) {
                Button(
                    onClick = { onConfirm(modelName, selectedType, selectedTier) },
                    enabled = progress == null
                ) {
                    Text("Import & Hash")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = progress == null) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditCustomModelDialog(
    model: InstalledModelEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, SamplingPreset, Int, Boolean) -> Unit
) {
    var name by remember(model) { mutableStateOf(model.displayName) }
    var template by remember(model) { mutableStateOf(model.chatTemplate) }
    var preset by remember(model) {
        mutableStateOf(runCatching { SamplingPreset.valueOf(model.samplingPreset) }.getOrDefault(SamplingPreset.BALANCED))
    }
    var ctx by remember(model) { mutableIntStateOf(model.contextLength) }
    var verified by remember(model) { mutableStateOf(model.userVerified) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Custom Model") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Model Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Chat Template", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("chatml", "llama3", "gemma", "mistral").forEach { tpl ->
                        FilterChip(selected = template == tpl, onClick = { template = tpl }, label = { Text(tpl) })
                    }
                }
                Text("Sampling Preset", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SamplingPreset.entries.forEach { p ->
                        FilterChip(selected = preset == p, onClick = { preset = p }, label = { Text(p.label) })
                    }
                }
                Text("Context Length", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2048, 4096, 8192, 16384).forEach { c ->
                        FilterChip(selected = ctx == c, onClick = { ctx = c }, label = { Text("${c / 1024}K") })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = verified, onCheckedChange = { verified = it })
                    Text("Mark source as user-verified (remove warning badge)", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, template, preset, ctx, verified) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
