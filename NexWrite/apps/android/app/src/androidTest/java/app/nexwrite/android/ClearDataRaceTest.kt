// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
package app.nexwrite.android

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClearDataRaceTest {
    @Test fun disablingAutosaveCancelsThePendingWrite() {
        val application = ApplicationProvider.getApplicationContext<NexWriteApplication>()
        runBlocking { application.store.clearDocuments(); application.store.settings("system", true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var ready = false
            repeat(30) {
                scenario.onActivity { ready = ViewModelProvider(it)[WritingModel::class.java].preferences.autosave }
                if (!ready) android.os.SystemClock.sleep(50)
            }
            assertTrue(ready)
            scenario.onActivity {
                val model = ViewModelProvider(it)[WritingModel::class.java]
                model.newDocument(); model.edit(TextFieldValue("Pending opt-out fixture")); model.settings("system", false)
                model.edit(TextFieldValue("Typing immediately after opt-out must also remain in memory"))
            }
            android.os.SystemClock.sleep(1200)
            assertTrue(runBlocking { application.store.documents().isEmpty() })
        }
    }
    @Test fun clearingWhileAutosaveIsPendingDoesNotRestoreTheDocument() {
        val application = ApplicationProvider.getApplicationContext<NexWriteApplication>()
        runBlocking { application.store.clearDocuments(); application.store.settings("system", true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var ready = false
            repeat(30) {
                scenario.onActivity { ready = ViewModelProvider(it)[WritingModel::class.java].preferences.autosave }
                if (!ready) android.os.SystemClock.sleep(50)
            }
            assertTrue(ready)
            scenario.onActivity {
                val model = ViewModelProvider(it)[WritingModel::class.java]
                model.newDocument(); model.edit(TextFieldValue("Private pending autosave fixture")); model.clearLocalData()
            }
            android.os.SystemClock.sleep(1200)
            assertTrue(runBlocking { application.store.documents().isEmpty() })
            scenario.onActivity { assertEquals("", ViewModelProvider(it)[WritingModel::class.java].draft.source) }
        }
    }
}
