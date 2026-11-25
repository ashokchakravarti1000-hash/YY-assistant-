package com.example.feature.chat

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.SourceCitation
import com.example.core.common.ToolApprovalRequest
import com.example.core.storage.ConversationEntity
import com.example.core.storage.MessageEntity
import com.example.core.ui.EmptyStatePanel
import com.example.core.ui.InstrumentCard
import com.example.ui.theme.IndigoPulse
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.NeuralTeal
import com.example.ui.theme.SaffronAmber
import com.example.ui.theme.StatusAmber
import com.example.viewmodel.YyMindViewModel
import com.example.viewmodel.parseCitationsJson

@Composable
fun ChatScreen(
    viewModel: YyMindViewModel,
    showTwoPaneList: Boolean = false,
    onNavigateScan: () -> Unit,
    onNavigateInstaller: () -> Unit
) {
    val context = LocalContext.current
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeConvId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val engineStats by viewModel.engineStats.collectAsStateWithLifecycle()
    val installedModels by viewModel.installedModels.collectAsStateWithLifecycle()
    val streamingThought by viewModel.streamingThought.collectAsStateWithLifecycle()
    val streamingAnswer by viewModel.streamingAnswer.collectAsStateWithLifecycle()
    val pendingAttachName by viewModel.pendingAttachmentName.collectAsStateWithLifecycle()
    val pendingTool by viewModel.pendingToolRequest.collectAsStateWithLifecycle()

    val activeConv = remember(conversations, activeConvId) {
        conversations.firstOrNull { it.id == activeConvId }
    }

    var inputText by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }
    var showPersonaDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var inspectedCitation by remember { mutableStateOf<SourceCitation?>(null) }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, streamingAnswer.length) {
        if (messages.isNotEmpty() || streamingAnswer.isNotEmpty()) {
            listState.animateScrollToItem(maxOf(0, messages.size))
        }
    }

    // Play-policy compliant zero-permission Photo Picker
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.attachImageToChat(uri)
    }

    // SAF document picker
    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.attachDocumentToChat(uri)
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Two-pane conversation sidebar on Expanded screens (Section 7 Responsive Behavior)
        if (showTwoPaneList) {
            Surface(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                ConversationSidebar(
                    conversations = conversations,
                    activeId = activeConvId,
                    onNew = { viewModel.createNewConversation() },
                    onSelect = { viewModel.selectConversation(it) },
                    onDelete = { viewModel.deleteConversation(it) }
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) {
            // Top Chat Control Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showModelPicker = true }
                                .testTag("chat_model_switcher")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "Switch model",
                                tint = SaffronAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = engineStats.loadedModelName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (engineStats.tokensPerSec > 0) {
                                        "${"%.1f".format(engineStats.tokensPerSec)} tok/s · ${engineStats.contextUsedTokens}/${engineStats.contextLength} ctx"
                                    } else {
                                        "Offline · Tap to switch model"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!showTwoPaneList) {
                                IconButton(
                                    onClick = { showHistoryDialog = true },
                                    modifier = Modifier.testTag("chat_history_button")
                                ) {
                                    Icon(Icons.Outlined.History, contentDescription = "Conversations")
                                }
                            }
                            IconButton(
                                onClick = { showPersonaDialog = true },
                                modifier = Modifier.testTag("chat_persona_button")
                            ) {
                                Icon(Icons.Outlined.Tune, contentDescription = "System prompt & personas")
                            }
                            IconButton(
                                onClick = {
                                    val md = viewModel.buildActiveConversationMarkdown()
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/markdown"
                                        putExtra(Intent.EXTRA_SUBJECT, activeConv?.title ?: "YY Assistant Chat")
                                        putExtra(Intent.EXTRA_TEXT, md)
                                    }
                                    context.startActivity(Intent.createChooser(share, "Export Conversation as Markdown"))
                                },
                                modifier = Modifier.testTag("chat_export_markdown_button")
                            ) {
                                Icon(Icons.Outlined.IosShare, contentDescription = "Export Markdown")
                            }
                        }
                    }

                    // Context Window Progress & Feature Toggles Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = activeConv?.reasoningEnabled == true,
                            onClick = {
                                viewModel.updateActiveConversationConfig(
                                    reasoningEnabled = !(activeConv?.reasoningEnabled ?: false)
                                )
                            },
                            label = { Text("Thinking") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(15.dp))
                            },
                            modifier = Modifier.testTag("chat_reasoning_toggle")
                        )
                        FilterChip(
                            selected = activeConv?.useRagLibrary != false,
                            onClick = {
                                viewModel.updateActiveConversationConfig(
                                    useRagLibrary = !(activeConv?.useRagLibrary ?: true)
                                )
                            },
                            label = { Text("RAG Library") },
                            leadingIcon = {
                                Icon(Icons.Outlined.FolderCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                            },
                            modifier = Modifier.testTag("chat_rag_toggle")
                        )
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.createNewConversation() },
                            label = { Text("New Chat") },
                            leadingIcon = {
                                Icon(Icons.Outlined.AddComment, contentDescription = null, modifier = Modifier.size(15.dp))
                            }
                        )
                    }

                    if (engineStats.contextUsedTokens > 0) {
                        LinearProgressIndicator(
                            progress = { engineStats.contextUsageFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .height(3.dp),
                            color = if (engineStats.contextUsageFraction >= 0.8f) StatusAmber else NeuralTeal
                        )
                    }
                }
            }

            // Messages Feed
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                if (messages.isEmpty() && streamingAnswer.isBlank() && pendingTool == null) {
                    EmptyStatePanel(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "Private Offline Chat",
                        subtitle = "Everything runs locally on your phone. Try a question, attach a PDF/image, ask about your Library, or test approval-gated tools like \"calculate (256 * 3) + 42\"."
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp),
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            MessageBubble(
                                message = msg,
                                onCitationClick = { inspectedCitation = it }
                            )
                        }

                        // Live Streaming Bubble
                        if (engineStats.isGenerating || streamingAnswer.isNotBlank() || streamingThought.isNotBlank()) {
                            item(key = "streaming_turn") {
                                MessageBubble(
                                    message = MessageEntity(
                                        id = "streaming",
                                        conversationId = activeConvId.orEmpty(),
                                        role = "assistant",
                                        content = streamingAnswer.ifBlank { "Generating tokens locally…" },
                                        thoughts = streamingThought.ifBlank { null },
                                        tokensPerSec = engineStats.tokensPerSec
                                    ),
                                    onCitationClick = {}
                                )
                            }
                        }

                        // On-Device Tool Approval Card (Section 14 #31 & Section 11)
                        pendingTool?.let { req ->
                            item(key = req.id) {
                                ToolApprovalBanner(
                                    request = req,
                                    onApprove = { viewModel.approvePendingTool() },
                                    onDeny = { viewModel.denyPendingTool() }
                                )
                            }
                        }
                    }
                }
            }

            // Pending Attachment Pill
            if (pendingAttachName != null) {
                Surface(
                    color = NeuralTeal.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Attached: $pendingAttachName (local context ready)",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeuralTeal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearPendingAttachment() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remove attachment", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Attachment Tray + Composer
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("chat_attach_image")
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = "Pick image")
                        }
                        IconButton(
                            onClick = {
                                docPicker.launch(arrayOf("application/pdf", "text/*", "application/json", "*/*"))
                            },
                            modifier = Modifier.testTag("chat_attach_doc")
                        ) {
                            Icon(Icons.Outlined.AttachFile, contentDescription = "Attach PDF or document")
                        }
                        IconButton(
                            onClick = onNavigateScan,
                            modifier = Modifier.testTag("chat_open_scanner")
                        ) {
                            Icon(Icons.Outlined.DocumentScanner, contentDescription = "Scan document")
                        }
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Ask YY Assistant offline…") },
                            maxLines = 4,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_field"),
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (engineStats.isGenerating) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable { viewModel.cancelGeneration() }
                                    .testTag("chat_stop_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.onError)
                                }
                            }
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable {
                                        if (inputText.isNotBlank()) {
                                            val toSend = inputText
                                            inputText = ""
                                            viewModel.sendChatMessage(toSend)
                                        }
                                    }
                                    .testTag("chat_send_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Send,
                                        contentDescription = "Send",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Model Switcher Dialog
    if (showModelPicker) {
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text("Switch Local Model") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val chatModels = installedModels.filter { it.modelType == "chat" || it.modelType == "vision" }
                    chatModels.forEach { m ->
                        val active = engineStats.loadedModelId == m.id
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.loadModel(m)
                                    showModelPicker = false
                                }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(m.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${m.quant} · ${m.contextLength} ctx · ${m.capabilitiesCsv}",
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
                    showModelPicker = false
                    onNavigateInstaller()
                }) { Text("Install More Models") }
            },
            dismissButton = {
                TextButton(onClick = { showModelPicker = false }) { Text("Close") }
            }
        )
    }

    // Persona & System Prompt Dialog (Section 14 #32)
    if (showPersonaDialog) {
        var customPrompt by remember(activeConv) {
            mutableStateOf(activeConv?.systemPrompt ?: viewModel.personas.first().systemPrompt)
        }
        AlertDialog(
            onDismissRequest = { showPersonaDialog = false },
            title = { Text("Persona & System Prompt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        viewModel.personas.forEach { p ->
                            FilterChip(
                                selected = customPrompt == p.systemPrompt,
                                onClick = { customPrompt = p.systemPrompt },
                                label = { Text(p.title) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customPrompt,
                        onValueChange = { customPrompt = it },
                        label = { Text("Per-chat System Prompt") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateActiveConversationConfig(systemPrompt = customPrompt)
                    showPersonaDialog = false
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showPersonaDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Conversation History Dialog (Compact / Medium screens)
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("Local Conversations") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (conversations.isEmpty()) {
                        Text("No saved conversations yet.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        conversations.take(12).forEach { c ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (c.id == activeConvId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectConversation(c.id)
                                        showHistoryDialog = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        c.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.deleteConversation(c.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Delete chat", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createNewConversation()
                    showHistoryDialog = false
                }) { Text("New Chat") }
            },
            dismissButton = {
                TextButton(onClick = { showHistoryDialog = false }) { Text("Close") }
            }
        )
    }

    // Citation Source Inspector Dialog
    inspectedCitation?.let { cite ->
        AlertDialog(
            onDismissRequest = { inspectedCitation = null },
            title = { Text(cite.documentName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Page ${cite.page} · §${cite.heading} · Match ${(cite.score * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeuralTeal
                    )
                    Text(cite.snippet, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectedCitation = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun MessageBubble(
    message: MessageEntity,
    onCitationClick: (SourceCitation) -> Unit
) {
    val isUser = message.role == "user"
    var expandThoughts by remember { mutableStateOf(false) }
    val citations = remember(message.citationsJson) { parseCitationsJson(message.citationsJson) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 580.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Collapsible Qwen3 / Phi-4 Thoughts Block (Section 6.1)
                if (!message.thoughts.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = IndigoPulse.copy(alpha = 0.12f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandThoughts = !expandThoughts }
                            .testTag("thoughts_accordion")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Psychology, contentDescription = null, tint = IndigoPulse, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Reasoning / Thoughts", style = MaterialTheme.typography.labelMedium, color = IndigoPulse)
                                }
                                Icon(
                                    if (expandThoughts) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                    contentDescription = "Toggle thoughts",
                                    tint = IndigoPulse,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            AnimatedVisibility(visible = expandThoughts) {
                                Text(
                                    text = message.thoughts,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )

                // Tappable RAG Citation Source Chips (Section 6.3)
                if (citations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        citations.forEachIndexed { idx, cite ->
                            Surface(
                                shape = CircleShape,
                                color = NeuralTeal.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, NeuralTeal.copy(alpha = 0.45f)),
                                modifier = Modifier.clickable { onCitationClick(cite) }
                            ) {
                                Text(
                                    text = "[${idx + 1}] ${cite.documentName.take(18)} p.${cite.page}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeuralTeal,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                if (!isUser && message.tokensPerSec > 0f) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${"%.1f".format(message.tokensPerSec)} tok/s · ${message.tokens} tokens · Local",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolApprovalBanner(
    request: ToolApprovalRequest,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    InstrumentCard(
        highlightBorder = SaffronAmber,
        modifier = Modifier.testTag("tool_approval_card")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Build, contentDescription = null, tint = SaffronAmber)
            Spacer(modifier = Modifier.width(8.dp))
            Text("On-Device Tool Approval Required", style = MaterialTheme.typography.titleSmall, color = SaffronAmber)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Tool: ${request.toolName} · ${request.description}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "Args: ${request.arguments}",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onApprove, modifier = Modifier.testTag("approve_tool_button")) {
                Text("Approve & Run Locally")
            }
            OutlinedButton(onClick = onDeny, modifier = Modifier.testTag("deny_tool_button")) {
                Text("Deny")
            }
        }
    }
}

@Composable
private fun ConversationSidebar(
    conversations: List<ConversationEntity>,
    activeId: String?,
    onNew: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Column(modifier = Modifier.padding(12.dp)) {
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.AddComment, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("New Offline Chat")
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(conversations, key = { it.id }) { c ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (c.id == activeId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(c.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            c.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onDelete(c.id) }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = "Delete", modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}
