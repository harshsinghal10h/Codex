// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
package app.nexwrite.android.model

import java.util.UUID

object SourceGuard {
    const val MAX_BYTES = 65_536
    const val AI_MAX_BYTES = 32_768
    const val MAX_VERSION = 9_007_199_254_740_991L

    fun error(text: String, maximum: Int = MAX_BYTES): String? {
        var i = 0
        while (i < text.length) {
            val value = text[i]
            if (value.isHighSurrogate()) {
                if (i + 1 >= text.length || !text[i + 1].isLowSurrogate()) return "The source contains an unpaired Unicode surrogate."
                i++
            } else if (value.isLowSurrogate()) return "The source contains an unpaired Unicode surrogate."
            i++
        }
        if (text.toByteArray(Charsets.UTF_8).size > maximum) return "This Android preview supports at most ${maximum / 1024} KiB of UTF-8 source."
        return null
    }

    fun boundary(text: String, index: Int): Boolean = index in 0..text.length &&
        !(index > 0 && index < text.length && text[index - 1].isHighSurrogate() && text[index].isLowSurrogate())

    fun validTitle(title: String): Boolean = title.trim().let { it.isNotEmpty() && it.codePointCount(0, it.length) <= 120 && error(it) == null }
}

data class Draft(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val source: String = "",
    val format: String = "plain",
    val version: Long = 0,
    val createdAt: String = java.time.Instant.now().toString(),
    val aiProvenance: List<AiProvenance> = emptyList(),
)

data class AiProvenance(val requestId: String, val provider: String, val model: String, val appliedVersion: Long)

data class TargetSnapshot(
    val documentId: String,
    val version: Long,
    val start: Int,
    val end: Int,
    val original: String,
    val requestText: String,
    val provider: String,
    val model: String,
    val task: String,
    val preset: String,
) {
    fun matches(draft: Draft): Boolean = draft.id == documentId && draft.version == version &&
        SourceGuard.boundary(draft.source, start) && SourceGuard.boundary(draft.source, end) &&
        start <= end && draft.source.substring(start, end) == original
}

class EditHistory(initial: Draft = Draft()) {
    var draft: Draft = initial
        private set
    private val history = ArrayDeque<Draft>()
    val canUndo: Boolean get() = history.isNotEmpty()

    fun open(next: Draft) { draft = next; history.clear() }

    fun edit(source: String = draft.source, title: String = draft.title, format: String = draft.format) {
        require(format in setOf("plain", "markdown"))
        if (source == draft.source && title == draft.title && format == draft.format) return
        history.clear()
        draft = draft.copy(source = source, title = title, format = format, version = nextVersion())
    }

    fun replace(snapshot: TargetSnapshot, replacement: String, ai: AiProvenance? = null): Boolean {
        if (!snapshot.matches(draft) || SourceGuard.error(replacement) != null) return false
        val next = draft.source.replaceRange(snapshot.start, snapshot.end, replacement)
        if (SourceGuard.error(next) != null) return false
        remember()
        val version = nextVersion()
        draft = draft.copy(source = next, version = version,
            aiProvenance = if (ai == null) draft.aiProvenance else (draft.aiProvenance + ai.copy(appliedVersion = version)).takeLast(20))
        return true
    }

    fun undo(): Boolean {
        if (history.isEmpty()) return false
        val version = nextVersion()
        draft = history.removeLast().copy(version = version)
        return true
    }

    private fun remember() { if (history.size == 20) history.removeFirst(); history.addLast(draft) }
    private fun nextVersion(): Long { check(draft.version < SourceGuard.MAX_VERSION) { "Document version limit reached" }; return draft.version + 1 }
}

object SensitiveField {
    // Android InputType/EditorInfo bit values kept here for deterministic JVM tests.
    fun excluded(inputType: Int, imeOptions: Int): Boolean {
        val inputClass = inputType and 0xf
        val variation = inputType and 0xff0
        return inputClass != 1 || variation in setOf(0x80, 0x90, 0xe0) ||
            (inputType and 0x80000) != 0 || (imeOptions and 0x1000000) != 0
    }
}

