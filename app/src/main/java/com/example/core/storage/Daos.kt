package com.example.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InstalledModelDao {
    @Query("SELECT * FROM installed_model ORDER BY lastUsedAt DESC, installedAt DESC")
    fun observeAll(): Flow<List<InstalledModelEntity>>

    @Query("SELECT * FROM installed_model ORDER BY lastUsedAt DESC, installedAt DESC")
    suspend fun getAllOnce(): List<InstalledModelEntity>

    @Query("SELECT * FROM installed_model WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): InstalledModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(model: InstalledModelEntity)

    @Update
    suspend fun update(model: InstalledModelEntity)

    @Query("UPDATE installed_model SET lastUsedAt = :ts WHERE id = :id")
    suspend fun touchLastUsed(id: String, ts: Long = System.currentTimeMillis())

    @Query("DELETE FROM installed_model WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM installed_model")
    suspend fun deleteAll()
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversation ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversation ORDER BY updatedAt DESC")
    suspend fun getAllOnce(): List<ConversationEntity>

    @Query("SELECT * FROM conversation WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query("DELETE FROM conversation WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM conversation")
    suspend fun deleteAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM message WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getForConversationOnce(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM message ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("DELETE FROM message WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: String)

    @Query("DELETE FROM message")
    suspend fun deleteAll()
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachment WHERE messageId = :messageId")
    suspend fun getForMessage(messageId: String): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: AttachmentEntity)

    @Query("DELETE FROM attachment")
    suspend fun deleteAll()
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM document ORDER BY indexedAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM document ORDER BY indexedAt DESC")
    suspend fun getAllOnce(): List<DocumentEntity>

    @Query("SELECT * FROM document WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentEntity)

    @Query("DELETE FROM document WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM document")
    suspend fun deleteAll()
}

@Dao
interface ChunkDao {
    @Query("SELECT * FROM chunk WHERE documentId = :documentId ORDER BY ordinal ASC")
    suspend fun getForDocument(documentId: String): List<ChunkEntity>

    @Query("SELECT * FROM chunk ORDER BY documentId, ordinal ASC")
    suspend fun getAllOnce(): List<ChunkEntity>

    @Query("SELECT COUNT(*) FROM chunk")
    fun observeTotalChunks(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<ChunkEntity>)

    @Query("DELETE FROM chunk WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: String)

    @Query("DELETE FROM chunk")
    suspend fun deleteAll()
}

@Dao
interface VoiceProfileDao {
    @Query("SELECT * FROM voice_profile ORDER BY isInstalled DESC, sizeBytes ASC")
    fun observeAll(): Flow<List<VoiceProfileEntity>>

    @Query("SELECT * FROM voice_profile ORDER BY isInstalled DESC, sizeBytes ASC")
    suspend fun getAllOnce(): List<VoiceProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: VoiceProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(profiles: List<VoiceProfileEntity>)

    @Query("DELETE FROM voice_profile")
    suspend fun deleteAll()
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM setting")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT value FROM setting WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)

    @Query("DELETE FROM setting")
    suspend fun deleteAll()
}
