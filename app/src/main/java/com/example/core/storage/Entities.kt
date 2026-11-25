package com.example.core.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "installed_model")
data class InstalledModelEntity(
    @PrimaryKey val id: String,
    val catalogId: String? = null,
    val displayName: String,
    val modelType: String = "chat", // chat, vision, stt, tts, vad, lora
    val tier: Int = 1,
    val path: String,
    val mmprojPath: String? = null,
    val loraPath: String? = null,
    val sizeBytes: Long = 0L,
    val sha256: String = "",
    val quant: String = "Q4_K_M",
    val contextLength: Int = 4096,
    val chatTemplate: String = "chatml",
    val samplingPreset: String = "BALANCED",
    val capabilitiesCsv: String = "chat",
    val source: String = "catalog", // catalog, bundled, sideload, local_zip
    val userVerified: Boolean = true,
    val supportsThinking: Boolean = false,
    val installedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "conversation")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val modelId: String,
    val systemPrompt: String = "",
    val reasoningEnabled: Boolean = false,
    val useRagLibrary: Boolean = true,
    val summaryPrefix: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "message",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String, // user, assistant, system
    val content: String,
    val thoughts: String? = null,
    val attachmentsJson: String = "[]",
    val citationsJson: String = "[]",
    val tokens: Int = 0,
    val tokensPerSec: Float = 0f,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "attachment",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["messageId"])]
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val type: String, // image, pdf, audio
    val uri: String,
    val displayName: String = "",
    val ocrText: String = ""
)

@Entity(tableName = "document")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mime: String,
    val uri: String = "",
    val pageCount: Int = 1,
    val chunkCount: Int = 0,
    val sizeBytes: Long = 0L,
    val status: String = "INDEXED", // INDEXING, INDEXED, ERROR
    val indexedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chunk",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["documentId"])]
)
data class ChunkEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val ordinal: Int,
    val heading: String = "Overview",
    val text: String,
    val page: Int = 1,
    val vectorCsv: String = ""
)

@Entity(tableName = "voice_profile")
data class VoiceProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val engine: String,
    val language: String = "en-US",
    val genderPresentation: String = "Neutral",
    val pitch: Float = 1.0f,
    val rate: Float = 1.0f,
    val pauseMs: Int = 240,
    val tags: String = "",
    val previewPath: String = "",
    val sizeBytes: Long = 0L,
    val isInstalled: Boolean = false
)

@Entity(tableName = "setting")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
