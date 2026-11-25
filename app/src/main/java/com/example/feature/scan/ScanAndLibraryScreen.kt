package com.example.feature.scan

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.common.formatBytes
import com.example.core.ui.EmptyStatePanel
import com.example.core.ui.InstrumentCard
import com.example.core.ui.SectionHeader
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NeuralTeal
import com.example.ui.theme.SaffronAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.viewmodel.YyMindViewModel
import java.io.File

@Composable
fun ScanAndLibraryScreen(
    viewModel: YyMindViewModel,
    initialSubTab: Int = 0, // 0 = Library, 1 = Scan & OCR
    onAskInChat: () -> Unit
) {
    val context = LocalContext.current
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val totalChunks by viewModel.totalChunks.collectAsStateWithLifecycle()
    val scanDraft by viewModel.scanDraft.collectAsStateWithLifecycle()
    val searchResults by viewModel.librarySearchResults.collectAsStateWithLifecycle()
    val hardware by viewModel.hardwareProfile.collectAsStateWithLifecycle()

    var subTab by remember(initialSubTab) { mutableIntStateOf(initialSubTab) }
    var searchQuery by remember { mutableStateOf("") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCameraUri
        if (saved && uri != null) {
            viewModel.analyzeScanImage(uri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val camDir = File(context.cacheDir, "camera").apply { mkdirs() }
            val photoFile = File(camDir, "scan_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            viewModel.showMessage("Camera permission denied — pick an image from Photo Picker instead.")
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.analyzeScanImage(uri)
    }

    val scanDocPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFileToScanPreview(uri)
    }

    val libraryDocPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.ingestDocumentUriToLibrary(uri)
    }

    fun launchCameraScan() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val camDir = File(context.cacheDir, "camera").apply { mkdirs() }
            val photoFile = File(camDir, "scan_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            TabRow(
                selectedTabIndex = subTab,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Tab(
                    selected = subTab == 0,
                    onClick = { subTab = 0 },
                    text = { Text("Private Library (${documents.size})") },
                    modifier = Modifier.testTag("tab_rag_library")
                )
                Tab(
                    selected = subTab == 1,
                    onClick = { subTab = 1 },
                    text = { Text("Scan & OCR") },
                    modifier = Modifier.testTag("tab_scan_ocr")
                )
            }

            if (subTab == 0) {
                // Private RAG Knowledge Base (Section 6.3 & 7 #10)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        InstrumentCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Encrypted RAG Knowledge Base", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "${documents.size} documents · $totalChunks vector chunks · Hybrid RRF top-${hardware.effectiveTier.topK}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { libraryDocPicker.launch(arrayOf("*/*")) },
                                    modifier = Modifier.testTag("library_import_doc_button")
                                ) {
                                    Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Import")
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Hybrid vector + keyword search…") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("library_search_input"),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                FilledTonalButton(
                                    onClick = { viewModel.searchLibrary(searchQuery) },
                                    modifier = Modifier.testTag("library_search_button")
                                ) {
                                    Icon(Icons.Outlined.Search, contentDescription = "Search")
                                }
                            }
                        }
                    }

                    if (searchResults.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Top Retrieved Citations",
                                subtitle = "Fused cosine similarity + BM25 RRF"
                            )
                        }
                        itemsIndexed(searchResults, key = { idx, it -> "${it.documentId}_${it.page}_$idx" }) { _, cite ->
                            InstrumentCard(highlightBorder = NeuralTeal.copy(alpha = 0.5f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${cite.documentName} · p.${cite.page}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = NeuralTeal
                                    )
                                    Text(
                                        "${(cite.score * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                        color = SaffronAmber
                                    )
                                }
                                Text("§ ${cite.heading}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(cite.snippet, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    if (documents.isEmpty()) {
                        item {
                            EmptyStatePanel(
                                icon = Icons.Outlined.FolderCopy,
                                title = "Your Private Library Is Empty",
                                subtitle = "Import PDF, DOCX, EPUB, HTML, TXT, Markdown, CSV, or scanned receipts. All chunking and vector indexing runs 100% locally.",
                                action = {
                                    Button(onClick = { libraryDocPicker.launch(arrayOf("*/*")) }) {
                                        Text("Add First Local Document")
                                    }
                                }
                            )
                        }
                    } else {
                        item { SectionHeader(title = "Indexed Documents") }
                        items(documents, key = { it.id }) { doc ->
                            InstrumentCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Outlined.Description, contentDescription = null, tint = SaffronAmber)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                doc.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                "${doc.pageCount}p · ${doc.chunkCount} chunks · ${formatBytes(doc.sizeBytes)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = StatusGreen.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            doc.status,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StatusGreen,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteLibraryDocument(doc.id) }) {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete document", tint = StatusRed)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Scan & Documents OCR Screen (Section 6.2 & 7 #9)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        InstrumentCard {
                            Text("Multimodal Document & Photo Scanner", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Capture receipts, forms, or pages locally. Uses native vision projector (mmproj) when Gemma 3 is active, or offline OCR + layout fallback otherwise.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { launchCameraScan() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("scan_camera_button")
                                ) {
                                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Camera")
                                }
                                FilledTonalButton(
                                    onClick = {
                                        photoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("scan_gallery_button")
                                ) {
                                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Photo")
                                }
                                OutlinedButton(
                                    onClick = { scanDocPicker.launch(arrayOf("*/*")) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("scan_file_button")
                                ) {
                                    Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("File")
                                }
                            }
                        }
                    }

                    if (scanDraft.isProcessing) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    scanDraft.selectedImageUri?.let { uri ->
                        item {
                            InstrumentCard {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Scanned preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                scanDraft.visionResult?.let { vr ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${vr.detectedDocumentStyle} · ${vr.aspectLabel} · ${vr.regions.size} layout regions",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = NeuralTeal
                                    )
                                    Text(
                                        text = vr.pipelineUsed,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    item {
                        InstrumentCard {
                            Text("Editable OCR / Document Text", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = scanDraft.documentName,
                                onValueChange = { viewModel.updateScanEditableText(scanDraft.editableText, it) },
                                label = { Text("Document Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = scanDraft.editableText,
                                onValueChange = { viewModel.updateScanEditableText(it) },
                                label = { Text("Extracted / Editable Text") },
                                placeholder = { Text("Capture a photo, pick a PDF/TXT, or paste notes here…") },
                                minLines = 5,
                                maxLines = 12,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("scan_editable_text")
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.sendScanDraftToChat()
                                        onAskInChat()
                                    },
                                    enabled = scanDraft.editableText.isNotBlank(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("scan_ask_about_button")
                                ) {
                                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ask About This")
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.addScanDraftToRagLibrary() },
                                    enabled = scanDraft.editableText.isNotBlank(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("scan_add_to_library_button")
                                ) {
                                    Icon(Icons.Outlined.LibraryAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add to Library")
                                }
                            }
                        }
                    }

                    if (scanDraft.selectedImageUri == null && scanDraft.editableText.isBlank()) {
                        item {
                            EmptyStatePanel(
                                icon = Icons.Outlined.DocumentScanner,
                                title = "Ready to Scan Offline",
                                subtitle = "Nothing scanned ever leaves your device. Prompt-injection hygiene automatically wraps extracted text as untrusted reference data."
                            )
                        }
                    }
                }
            }
        }
    }
}
