package com.example.core.storage

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class KeystoreSecurityStatus(
    val alias: String,
    val isKeyProvisioned: Boolean,
    val isHardwareBackedHint: Boolean,
    val wrappedKeyFingerprint: String
)

data class RestoredArchiveData(
    val conversations: List<ConversationEntity>,
    val messages: List<MessageEntity>,
    val documents: List<DocumentEntity>,
    val chunks: List<ChunkEntity>
)

/**
 * Section 9 & 11: Hardware-backed Android Keystore AES-256-GCM key management,
 * local-only diagnostic logs (zero telemetry), PBKDF2 + AES-256-GCM offline archive backup/restore,
 * and Secure Wipe of local files.
 */
class KeystoreCrypto(private val context: Context) {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "yy_assistant_vault_master_aes256"
        private const val ARCHIVE_MAGIC = "YYM1"
        private const val PBKDF2_ITERATIONS = 120_000
        private const val SALT_BYTES = 16
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val MAX_LOG_LINES = 250
    }

    private val logLock = Any()
    private val logBuffer = ArrayDeque<String>(MAX_LOG_LINES)
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun logEvent(tag: String, message: String) {
        val entry = "[${timeFmt.format(Date())}] [$tag] $message"
        synchronized(logLock) {
            if (logBuffer.size >= MAX_LOG_LINES) {
                logBuffer.removeFirst()
            }
            logBuffer.addLast(entry)
        }
    }

    fun ensureMasterKeyStatus(): KeystoreSecurityStatus {
        val prefs = context.getSharedPreferences("yy_assistant_keystore_meta", Context.MODE_PRIVATE)
        val existingFp = prefs.getString("wrapped_key_fp", null)

        var provisioned = false
        var hardwareHint = false
        var fingerprint = existingFp

        runCatching {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val secretKey: SecretKey = if (ks.containsAlias(MASTER_KEY_ALIAS)) {
                ks.getKey(MASTER_KEY_ALIAS, null) as SecretKey
            } else {
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
                val spec = KeyGenParameterSpec.Builder(
                    MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGen.init(spec)
                keyGen.generateKey()
            }
            provisioned = true
            hardwareHint = true

            if (fingerprint == null) {
                val randomDbKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val wrapped = cipher.doFinal(randomDbKey)
                val iv = cipher.iv
                val digest = MessageDigest.getInstance("SHA-256").digest(iv + wrapped)
                fingerprint = "KS-" + digest.take(6).joinToString("") { "%02X".format(it) }
                prefs.edit()
                    .putString("wrapped_key_fp", fingerprint)
                    .putString("wrapped_db_iv", iv.joinToString("") { "%02x".format(it) })
                    .putString("wrapped_db_ct", wrapped.joinToString("") { "%02x".format(it) })
                    .apply()
            }
        }.onFailure {
            // Fallback for Robolectric/JVM environments without AndroidKeyStore provider
            if (fingerprint == null) {
                val seed = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val digest = MessageDigest.getInstance("SHA-256").digest(seed)
                fingerprint = "SW-" + digest.take(6).joinToString("") { "%02X".format(it) }
                prefs.edit().putString("wrapped_key_fp", fingerprint).apply()
            }
        }

        return KeystoreSecurityStatus(
            alias = MASTER_KEY_ALIAS,
            isKeyProvisioned = provisioned,
            isHardwareBackedHint = hardwareHint,
            wrappedKeyFingerprint = fingerprint ?: "KS-ACTIVE"
        )
    }

    fun exportEncryptedArchive(
        passphrase: String,
        conversations: List<ConversationEntity>,
        messages: List<MessageEntity>,
        documents: List<DocumentEntity>,
        chunks: List<ChunkEntity>,
        destUri: Uri
    ): Result<Int> = runCatching {
        require(passphrase.length >= 4) { "Passphrase must be at least 4 characters." }

        val root = JSONObject().apply {
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("conversations", JSONArray().apply {
                conversations.forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id)
                        put("title", c.title)
                        put("modelId", c.modelId)
                        put("systemPrompt", c.systemPrompt)
                        put("reasoningEnabled", c.reasoningEnabled)
                        put("useRagLibrary", c.useRagLibrary)
                        put("summaryPrefix", c.summaryPrefix)
                        put("createdAt", c.createdAt)
                        put("updatedAt", c.updatedAt)
                    })
                }
            })
            put("messages", JSONArray().apply {
                messages.forEach { m ->
                    put(JSONObject().apply {
                        put("id", m.id)
                        put("conversationId", m.conversationId)
                        put("role", m.role)
                        put("content", m.content)
                        put("thoughts", m.thoughts ?: "")
                        put("attachmentsJson", m.attachmentsJson)
                        put("citationsJson", m.citationsJson)
                        put("tokens", m.tokens)
                        put("tokensPerSec", m.tokensPerSec.toDouble())
                        put("createdAt", m.createdAt)
                    })
                }
            })
            put("documents", JSONArray().apply {
                documents.forEach { d ->
                    put(JSONObject().apply {
                        put("id", d.id)
                        put("name", d.name)
                        put("mime", d.mime)
                        put("uri", d.uri)
                        put("pageCount", d.pageCount)
                        put("chunkCount", d.chunkCount)
                        put("sizeBytes", d.sizeBytes)
                        put("status", d.status)
                        put("indexedAt", d.indexedAt)
                    })
                }
            })
            put("chunks", JSONArray().apply {
                chunks.forEach { ch ->
                    put(JSONObject().apply {
                        put("id", ch.id)
                        put("documentId", ch.documentId)
                        put("ordinal", ch.ordinal)
                        put("heading", ch.heading)
                        put("text", ch.text)
                        put("page", ch.page)
                        put("vectorCsv", ch.vectorCsv)
                    })
                }
            })
        }

        val plaintext = root.toString().toByteArray(Charsets.UTF_8)
        val rng = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { rng.nextBytes(it) }
        val iv = ByteArray(GCM_IV_BYTES).also { rng.nextBytes(it) }
        val key = deriveArchiveKey(passphrase, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        val magic = ARCHIVE_MAGIC.toByteArray(Charsets.US_ASCII)
        val outBytes = ByteBuffer.allocate(magic.size + salt.size + iv.size + ciphertext.size)
            .put(magic)
            .put(salt)
            .put(iv)
            .put(ciphertext)
            .array()

        context.contentResolver.openOutputStream(destUri)?.use { os ->
            os.write(outBytes)
            os.flush()
        } ?: error("Unable to open destination URI for archive export.")

        val totalCount = conversations.size + messages.size + documents.size
        logEvent("BACKUP", "Exported AES-256-GCM offline archive ($totalCount items).")
        totalCount
    }

    fun importEncryptedArchive(passphrase: String, sourceUri: Uri): Result<RestoredArchiveData> = runCatching {
        val raw = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
            ?: error("Unable to open backup archive.")

        val headerSize = 4 + SALT_BYTES + GCM_IV_BYTES
        require(raw.size > headerSize + 16) { "Archive file is truncated or invalid." }

        val magic = String(raw.copyOfRange(0, 4), Charsets.US_ASCII)
        require(magic == ARCHIVE_MAGIC) { "Unsupported archive header (expected $ARCHIVE_MAGIC)." }

        val salt = raw.copyOfRange(4, 4 + SALT_BYTES)
        val iv = raw.copyOfRange(4 + SALT_BYTES, headerSize)
        val ciphertext = raw.copyOfRange(headerSize, raw.size)

        val key = deriveArchiveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plaintext = cipher.doFinal(ciphertext)

        val root = JSONObject(String(plaintext, Charsets.UTF_8))
        val convs = root.optJSONArray("conversations")?.let { arr ->
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                ConversationEntity(
                    id = o.getString("id"),
                    title = o.optString("title", "Restored Chat"),
                    modelId = o.optString("modelId", "bundled-135m-starter"),
                    systemPrompt = o.optString("systemPrompt", ""),
                    reasoningEnabled = o.optBoolean("reasoningEnabled", false),
                    useRagLibrary = o.optBoolean("useRagLibrary", true),
                    summaryPrefix = o.optString("summaryPrefix", ""),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                )
            }
        } ?: emptyList()

        val msgs = root.optJSONArray("messages")?.let { arr ->
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                MessageEntity(
                    id = o.getString("id"),
                    conversationId = o.getString("conversationId"),
                    role = o.optString("role", "assistant"),
                    content = o.optString("content", ""),
                    thoughts = o.optString("thoughts", "").ifBlank { null },
                    attachmentsJson = o.optString("attachmentsJson", "[]"),
                    citationsJson = o.optString("citationsJson", "[]"),
                    tokens = o.optInt("tokens", 0),
                    tokensPerSec = o.optDouble("tokensPerSec", 0.0).toFloat(),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            }
        } ?: emptyList()

        val docs = root.optJSONArray("documents")?.let { arr ->
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                DocumentEntity(
                    id = o.getString("id"),
                    name = o.optString("name", "Restored Doc"),
                    mime = o.optString("mime", "text/plain"),
                    uri = o.optString("uri", ""),
                    pageCount = o.optInt("pageCount", 1),
                    chunkCount = o.optInt("chunkCount", 0),
                    sizeBytes = o.optLong("sizeBytes", 0L),
                    status = o.optString("status", "INDEXED"),
                    indexedAt = o.optLong("indexedAt", System.currentTimeMillis())
                )
            }
        } ?: emptyList()

        val chunks = root.optJSONArray("chunks")?.let { arr ->
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                ChunkEntity(
                    id = o.getString("id"),
                    documentId = o.getString("documentId"),
                    ordinal = o.optInt("ordinal", i),
                    heading = o.optString("heading", "Overview"),
                    text = o.optString("text", ""),
                    page = o.optInt("page", 1),
                    vectorCsv = o.optString("vectorCsv", "")
                )
            }
        } ?: emptyList()

        logEvent("BACKUP", "Decrypted offline archive: ${convs.size} chats, ${docs.size} documents.")
        RestoredArchiveData(convs, msgs, docs, chunks)
    }

    fun exportDiagnosticLogsToUri(destUri: Uri): Result<Unit> = runCatching {
        val snapshot = synchronized(logLock) { logBuffer.toList() }
        val payload = buildString {
            appendLine("=== YY Assistant (वाईवाई असिस्टेंट) Local Diagnostic Log ===")
            appendLine("Privacy: Zero analytics, zero telemetry, exported strictly on user request.")
            appendLine("Entries: ${snapshot.size}")
            appendLine()
            snapshot.forEach { appendLine(it) }
        }
        context.contentResolver.openOutputStream(destUri)?.use { os ->
            os.write(payload.toByteArray(Charsets.UTF_8))
            os.flush()
        } ?: error("Unable to open log export destination.")
    }

    fun secureWipeFiles() {
        listOf("models", "voices", "documents", "tmp").forEach { sub ->
            File(context.filesDir, sub).deleteRecursively()
        }
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        synchronized(logLock) { logBuffer.clear() }
        logEvent("WIPE", "Secure wipe cleared local models, voices, documents, and caches.")
    }

    private fun deriveArchiveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
