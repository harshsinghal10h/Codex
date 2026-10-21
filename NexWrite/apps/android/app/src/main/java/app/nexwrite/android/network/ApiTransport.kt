// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
package app.nexwrite.android.network

import app.nexwrite.android.model.SourceGuard
import app.nexwrite.android.model.TargetSnapshot
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.Future
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import app.nexwrite.android.readLimited
import app.nexwrite.protocol.Capabilities
import app.nexwrite.protocol.AiResult
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.*

/** Runtime destination only. No provider key, endpoint or document is embedded. */
object ApiTransport {
    private val executor = Executors.newFixedThreadPool(2) { task -> Thread(task, "nexwrite-api").apply { isDaemon = true } }
    val json = Json { ignoreUnknownKeys = false; isLenient = false }
    fun address(value: String): String {
        val trimmed = value.trim().trimEnd('/')
        val uri = URI(trimmed)
        require(uri.scheme == "https" && !uri.host.isNullOrEmpty() && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "Use an HTTPS backend address without credentials, query or fragment."
        }
        return trimmed
    }

    suspend fun capabilities(base: String, token: String): JsonObject = exchange(base, token, "/v1/capabilities", null).also { result ->
        val typed = json.decodeFromJsonElement<Capabilities>(result)
        require(typed.protocol_version == 1L && typed.version.isNotEmpty() && typed.version.codePointCount(0, typed.version.length) <= 64 && SourceGuard.error(typed.version) == null)
        require(!typed.accounts && !typed.cloud_docs && typed.engine.rule_count in 0..4_294_967_295L)
        require(typed.engine.locales.map { it.name } == listOf("EN_US") && typed.engine.input_formats.map { it.name } == listOf("PLAIN", "MARKDOWN"))
        require(typed.tasks.map { it.name } == listOf("REWRITE", "SUMMARIZE", "COMPOSE"))
        require(typed.providers.all { profile -> Regex("^[a-z][a-z0-9_-]{0,63}$").matches(profile.id) && profile.label.isNotEmpty() && profile.label.codePointCount(0, profile.label.length) <= 80 && SourceGuard.error(profile.label) == null && profile.models.all { Regex("^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$").matches(it) } })
        require(result["protocol_version"]?.jsonPrimitive?.let { !it.isString && it.int == 1 } == true) { "This backend uses an unsupported protocol." }
        require(result["providers"] is JsonArray && result["tasks"] is JsonArray) { "The backend returned invalid capabilities." }
    }

    suspend fun rewrite(base: String, token: String, target: TargetSnapshot): JsonObject {
        require(SourceGuard.error(target.requestText, SourceGuard.AI_MAX_BYTES) == null)
        val body = buildJsonObject {
            put("text", target.requestText); put("doc_version", target.version); put("locale", "en-US")
            put("task", target.task); put("preset", target.preset); put("provider", target.provider); put("model", target.model)
            put("cloud_consent", true)
        }
        return validateResult(exchange(base, token, "/v1/rewrite", body), target)
    }

    internal fun validateResult(result: JsonObject, target: TargetSnapshot): JsonObject {
        json.decodeFromJsonElement<AiResult>(result)
        val expected = setOf("request_id", "doc_version", "provider", "model", "task", "preset", "original", "replacement", "warnings")
        require(result.keys == expected && result["warnings"] is JsonArray) { "The backend returned an invalid result." }
        require(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$").matches(result.getValue("request_id").jsonPrimitive.content))
        require(!result.getValue("doc_version").jsonPrimitive.isString && result.getValue("doc_version").jsonPrimitive.long == target.version &&
            result.getValue("provider").jsonPrimitive.content == target.provider && result.getValue("model").jsonPrimitive.content == target.model &&
            result.getValue("task").jsonPrimitive.content == target.task && result.getValue("preset").jsonPrimitive.content == target.preset &&
            result.getValue("original").jsonPrimitive.content == target.requestText) { "The backend returned a mismatched result." }
        val replacement = result.getValue("replacement").jsonPrimitive
        require(replacement.isString && SourceGuard.error(replacement.content, 65_536) == null) { "The backend returned invalid text." }
        require(result.getValue("warnings").jsonArray.size <= 64 && result.getValue("warnings").jsonArray.all { it is JsonPrimitive && it.isString && SourceGuard.error(it.content, 2048) == null && it.content.codePointCount(0, it.content.length) <= 512 })
        return result
    }

    private suspend fun exchange(base: String, token: String, path: String, body: JsonObject?): JsonObject = withTimeout(30_000) { suspendCancellableCoroutine { continuation ->
        val connection = URI(address(base) + path).toURL().openConnection() as HttpsURLConnection
        connection.connectTimeout = 15_000; connection.readTimeout = 30_000; connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json, application/problem+json")
        if (token.isNotEmpty()) connection.setRequestProperty("Authorization", "Bearer $token")
        val future = AtomicReference<Future<*>?>()
        continuation.invokeOnCancellation { connection.disconnect(); future.get()?.cancel(true) }
        future.set(executor.submit {
        try {
            if (!continuation.isActive) return@submit
            if (body != null) {
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { readLimited(it, 2_097_152) } ?: byteArrayOf()
            require(bytes.size <= 2_097_152) { "The backend response exceeded the limit." }
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            if (status !in 200..299) throw IllegalStateException(when (status) {
                401 -> "The backend access token was rejected."
                403 -> "The backend requires consent for this request."
                413 -> "This text exceeds the backend limit."
                422 -> "This backend does not support the selected request."
                429 -> "The backend rate limit was reached. Try again later."
                503 -> "The provider is not configured or is unavailable."
                504 -> "The provider timed out."
                else -> "The backend request failed (HTTP $status)."
            })
            val result = json.parseToJsonElement(text).jsonObject
            if (continuation.isActive) continuation.resume(result)
        } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
        finally { connection.disconnect() }
        })
        if (!continuation.isActive) future.get()?.cancel(true)
    } }
}
