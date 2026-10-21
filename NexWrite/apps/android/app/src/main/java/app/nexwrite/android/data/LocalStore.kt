// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
package app.nexwrite.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import app.nexwrite.android.model.AiProvenance
import app.nexwrite.android.model.Draft
import app.nexwrite.android.model.SourceGuard
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Context.preferences by preferencesDataStore(name = "nexwrite_settings")

data class Preferences(
    val appearance: String = "system",
    val autosave: Boolean = false,
    val backend: String = "",
    val tokenConfigured: Boolean = false,
)

class LocalStore(private val context: Context) {
    private val database by lazy { Room.databaseBuilder(context, DocumentsDatabase::class.java, "nexwrite_documents.db").build() }
    private val theme = stringPreferencesKey("appearance")
    private val autosave = booleanPreferencesKey("autosave")
    private val backend = stringPreferencesKey("backend")
    private val protectedToken = stringPreferencesKey("access_token_ciphertext")
    val preferences: Flow<Preferences> = context.preferences.data.map {
        Preferences(it[theme] ?: "system", it[autosave] ?: false, it[backend] ?: "", !it[protectedToken].isNullOrEmpty())
    }

    suspend fun settings(appearance: String, saveAutomatically: Boolean) {
        require(appearance in setOf("system", "light", "dark"))
        context.preferences.edit { it[theme] = appearance; it[autosave] = saveAutomatically }
    }

    suspend fun connection(address: String, token: String) {
        val encrypted = if (token.isEmpty()) "" else encrypt(token)
        context.preferences.edit { it[backend] = address; if (encrypted.isEmpty()) it.remove(protectedToken) else it[protectedToken] = encrypted }
    }

    suspend fun token(): String {
        val encoded = context.preferences.data.first()[protectedToken] ?: return ""
        return decrypt(encoded)
    }

    suspend fun documents(): List<StoredDocument> = withContext(Dispatchers.IO) { database.documents().all() }

    suspend fun save(draft: Draft): StoredDocument = withContext(Dispatchers.IO) {
        require(SourceGuard.error(draft.source) == null)
        val title = draft.title.trim().ifEmpty { "Untitled document" }
        require(SourceGuard.validTitle(title))
        val stored = StoredDocument().apply {
            id = draft.id; this.title = title; source = draft.source; inputFormat = draft.format
            docVersion = draft.version; createdAt = draft.createdAt; updatedAt = java.time.Instant.now().toString()
            provenanceJson = JSONArray().apply { draft.aiProvenance.forEach { put(JSONObject().put("request_id", it.requestId).put("provider", it.provider).put("model", it.model).put("applied_version", it.appliedVersion)) } }.toString()
        }
        database.documents().save(stored)
        stored
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { database.documents().delete(id) }
    suspend fun clearDocuments() = withContext(Dispatchers.IO) { database.documents().deleteAll() }
    suspend fun clearSettings() { context.preferences.edit { it.clear() } }

    fun draft(document: StoredDocument): Draft {
        require(document.schemaVersion == 1 && document.inputFormat in setOf("plain", "markdown") && document.locale == "en-US")
        require(document.docVersion in 0..SourceGuard.MAX_VERSION && SourceGuard.validTitle(document.title) && SourceGuard.error(document.source) == null)
        val tags = runCatching { JSONArray(document.provenanceJson).let { list -> (maxOf(0, list.length() - 20) until list.length()).map { list.getJSONObject(it).let { tag -> AiProvenance(tag.getString("request_id"), tag.getString("provider"), tag.getString("model"), tag.getLong("applied_version")) } } } }.getOrDefault(emptyList())
        return Draft(document.id, document.title, document.source, document.inputFormat, document.docVersion, document.createdAt, tags)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey("nexwrite_access_token_v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("nexwrite_access_token_v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
}
