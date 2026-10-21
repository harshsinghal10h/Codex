// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
package app.nexwrite.core

/** Synchronous transport to the same local Rust engine used by CLI and WASM. */
object NativeBridge {
    const val MAX_ANALYSIS_UTF8_BYTES: Int = 1_048_576
    const val EDITOR_MAX_UTF8_BYTES: Int = 64 * 1024
    const val IME_MAX_UTF8_BYTES: Int = 16 * 1024
    const val MAX_DOCUMENT_VERSION: Long = 9_007_199_254_740_991L

    // A missing/unsupported ABI must become an unavailable UI state, never a
    // fabricated analysis or an automatic upload to a remote engine.
    private val libraryLoaded: Result<Unit> = runCatching { System.loadLibrary("nw_bridge") }

    /** Strict UTF-8 bytes only. Application callers should use [analyzeJson]. */
    @JvmStatic
    external fun analyzeUtf8(
        text: ByteArray,
        docVersion: Long,
        locale: ByteArray,
        inputFormat: ByteArray,
    ): ByteArray

    /**
     * Call from a worker dispatcher, never the main thread. Cancellation and
     * stale-version disposal belong to the caller: this JNI call is synchronous.
     * [IllegalArgumentException] contains fixed validation messages only.
     */
    fun analyzeJson(
        text: String,
        docVersion: Long,
        locale: String = "en-US",
        inputFormat: String = "plain",
    ): String {
        require(docVersion in 0..MAX_DOCUMENT_VERSION) {
            "document version must be a nonnegative safe integer"
        }
        require(locale == "en-US") { "analysis supports en-US only" }
        require(inputFormat == "plain" || inputFormat == "markdown") {
            "input format must be plain or markdown"
        }
        val source = sourceUtf8Bytes(text)
        libraryLoaded.getOrThrow()
        return analyzeUtf8(
            source,
            docVersion,
            locale.toByteArray(Charsets.UTF_8),
            inputFormat.toByteArray(Charsets.UTF_8),
        ).toString(Charsets.UTF_8)
    }

    /**
     * Reject malformed UTF-16 before Java's UTF-8 encoder can replace it. The
     * lower editor/IME limits are explicit UI constraints, never truncation.
     */
    fun sourceUtf8Bytes(
        text: String,
        maximumUtf8Bytes: Int = MAX_ANALYSIS_UTF8_BYTES,
    ): ByteArray {
        require(maximumUtf8Bytes in 0..MAX_ANALYSIS_UTF8_BYTES) { "invalid source byte limit" }
        var index = 0
        var bytes = 0
        while (index < text.length) {
            val unit = text[index]
            val width = when {
                Character.isHighSurrogate(unit) -> {
                    require(index + 1 < text.length && Character.isLowSurrogate(text[index + 1])) {
                        "text contains an unpaired UTF-16 surrogate"
                    }
                    index += 1
                    4
                }
                Character.isLowSurrogate(unit) -> {
                    throw IllegalArgumentException("text contains an unpaired UTF-16 surrogate")
                }
                unit.code <= 0x7f -> 1
                unit.code <= 0x7ff -> 2
                else -> 3
            }
            require(bytes <= maximumUtf8Bytes - width) { "source exceeds UTF-8 byte limit" }
            bytes += width
            index += 1
        }
        return text.toByteArray(Charsets.UTF_8)
    }
}
