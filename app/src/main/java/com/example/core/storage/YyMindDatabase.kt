package com.example.core.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        InstalledModelEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        DocumentEntity::class,
        ChunkEntity::class,
        VoiceProfileEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class YyMindDatabase : RoomDatabase() {
    abstract fun installedModelDao(): InstalledModelDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun documentDao(): DocumentDao
    abstract fun chunkDao(): ChunkDao
    abstract fun voiceProfileDao(): VoiceProfileDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile
        private var INSTANCE: YyMindDatabase? = null

        fun getInstance(context: Context): YyMindDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    YyMindDatabase::class.java,
                    "yy_assistant_vault.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
