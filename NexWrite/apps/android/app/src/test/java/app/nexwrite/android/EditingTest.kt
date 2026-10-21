// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
package app.nexwrite.android

import app.nexwrite.android.model.*
import app.nexwrite.android.network.ApiTransport
import app.nexwrite.core.NativeBridge
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*

class EditingTest {
    @Test fun unicodeBoundariesAndExactLimits() {
        val exact = "🙂".repeat(16_384)
        assertNull(SourceGuard.error(exact))
        assertNotNull(SourceGuard.error(exact + "a"))
        assertNotNull(SourceGuard.error("\ud800"))
        assertNotNull(SourceGuard.error("\udc00"))
        assertFalse(SourceGuard.boundary("a🙂b", 2))
        assertTrue(SourceGuard.boundary("a🙂b", 3))
        val source = "🙂\u0000e\u0301\r\nمرحبا"
        assertArrayEquals(source.toByteArray(Charsets.UTF_8), NativeBridge.sourceUtf8Bytes(source))
        assertEquals(4, NativeBridge.sourceUtf8Bytes("🙂", 4).size)
        assertThrows(IllegalArgumentException::class.java) { NativeBridge.sourceUtf8Bytes("🙂", 3) }
        assertThrows(IllegalArgumentException::class.java) { NativeBridge.sourceUtf8Bytes("\ud800") }
    }
    @Test fun staleCorrectionsAndUndoPreserveMonotonicVersions() {
        val initial = Draft(source = "🙂a  b", version = 7)
        val history = EditHistory(initial)
        val target = TargetSnapshot(initial.id, 7, 3, 5, "  ", "  ", "", "", "", "")
        assertTrue(history.replace(target, " "))
        assertEquals("🙂a b", history.draft.source)
        assertEquals(8L, history.draft.version)
        assertFalse(history.replace(target, "x"))
        assertTrue(history.undo())
        assertEquals(initial.source, history.draft.source)
        assertEquals(9L, history.draft.version)
        assertFalse(history.replace(target.copy(documentId = "another"), " "))
        assertFalse(history.replace(target.copy(version = 9, start = 1, end = 2, original = "\ud83d"), ""))
    }
    @Test fun aiReplacementRetainsProvenanceAndIsOneUndoEdit() {
        val initial = Draft(source = "An original sentence.")
        val history = EditHistory(initial)
        val target = TargetSnapshot(initial.id, 0, 0, initial.source.length, initial.source, initial.source, "openai", "reviewed-model", "rewrite", "clearer")
        assertTrue(history.replace(target, "A clear sentence.", AiProvenance("id", "openai", "reviewed-model", 0)))
        assertEquals("openai", history.draft.aiProvenance.single().provider)
        assertEquals(1L, history.draft.aiProvenance.single().appliedVersion)
        assertTrue(history.undo())
        assertEquals(initial.source, history.draft.source)
        assertTrue(history.draft.aiProvenance.isEmpty())
    }
    @Test fun historyIsBoundedAndTypingInvalidatesAcceptedEditUndo() {
        val history = EditHistory(Draft(source = "x"))
        repeat(25) {
            val draft = history.draft
            assertTrue(history.replace(TargetSnapshot(draft.id, draft.version, 0, 1, "x", "x", "", "", "", ""), "x"))
        }
        repeat(20) { assertTrue(history.undo()) }
        assertFalse(history.undo())
        history.edit("a")
        assertFalse(history.canUndo)
    }
    @Test fun privateAndUnsupportedFieldsSuppressAllAssist() {
        assertFalse(SensitiveField.excluded(1, 0))
        for (value in listOf(0x81, 0x91, 0xe1, 0x80001, 0x12, 2, 3, 0)) assertTrue(SensitiveField.excluded(value, 0))
        assertTrue(SensitiveField.excluded(1, 0x1000000))
    }
    @Test fun backendResultsRejectStaleMalformedAndOversizedWireData() {
        val target = TargetSnapshot("doc", 7, 0, 5, "Hello", "Hello", "openai", "model", "rewrite", "clearer")
        val valid = buildJsonObject {
            put("request_id", "12345678-1234-1234-1234-123456789abc"); put("doc_version", 7)
            put("provider", "openai"); put("model", "model"); put("task", "rewrite"); put("preset", "clearer")
            put("original", "Hello"); put("replacement", "Hello there"); put("warnings", JsonArray(emptyList()))
        }
        assertEquals("Hello there", ApiTransport.validateResult(valid, target).getValue("replacement").jsonPrimitive.content)
        for (bad in listOf(
            JsonObject(valid + ("doc_version" to JsonPrimitive(8))),
            JsonObject(valid + ("doc_version" to JsonPrimitive("7"))),
            JsonObject(valid + ("original" to JsonPrimitive("changed"))),
            JsonObject(valid + ("request_id" to JsonPrimitive("1-1-1-1-1"))),
            JsonObject(valid + ("replacement" to JsonPrimitive("🙂".repeat(16_385)))),
            JsonObject(valid + ("replacement" to JsonPrimitive("\ud800"))),
            JsonObject(valid + ("unexpected" to JsonPrimitive(true)))
        )) assertThrows(IllegalArgumentException::class.java) { ApiTransport.validateResult(bad, target) }
    }
    @Test fun backendAddressesRequireHttpsWithoutEmbeddedCredentials() {
        assertEquals("https://example.com/api", ApiTransport.address(" https://example.com/api/ "))
        for (url in listOf("http://example.com", "https://user:secret@example.com", "https://example.com?key=a", "https://example.com#token", "example.com")) {
            assertThrows(IllegalArgumentException::class.java) { ApiTransport.address(url) }
        }
    }
}
