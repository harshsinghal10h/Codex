// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
package app.nexwrite.android

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nexwrite.android.data.DocumentsDatabase
import app.nexwrite.android.data.StoredDocument
import app.nexwrite.core.NativeBridge
import app.nexwrite.protocol.AnalysisResult
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalDocumentTest {
    @Test fun roomCrudRetainsExactUnicodeSource() {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DocumentsDatabase::class.java).allowMainThreadQueries().build()
        try {
            val document = StoredDocument().apply { id = "local-id"; title = "Unicode"; source = "🙂e\u0301\r\nمرحبا"; createdAt = "2026-10-21T00:00:00Z"; updatedAt = createdAt }
            database.documents().save(document)
            assertEquals(document.source, database.documents().find(document.id).source)
            document.title = "Renamed"; database.documents().save(document)
            assertEquals("Renamed", database.documents().all().single().title)
            database.documents().delete(document.id)
            assertTrue(database.documents().all().isEmpty())
        } finally { database.close() }
    }
    @Test fun nativeEngineHasExactVersionOffsetsAndMarkdownProtection() {
        val report = Json.decodeFromString<AnalysisResult>(NativeBridge.analyzeJson("🙂a  b `x  y`", 42, "en-US", "markdown"))
        assertEquals(42L, report.doc_version)
        assertEquals(1, report.suggestions.size)
        assertEquals(listOf(3L, 5L), report.suggestions.single().span.utf16)
        assertTrue(report.protected_regions.isNotEmpty())
    }
}
