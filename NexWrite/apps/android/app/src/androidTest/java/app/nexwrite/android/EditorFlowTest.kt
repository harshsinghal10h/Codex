// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
package app.nexwrite.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun writeSaveAndFindInLocalLibrary() {
        lateinit var model: WritingModel
        compose.runOnIdle { model = ViewModelProvider(compose.activity)[WritingModel::class.java] }
        val title = "Android fixture " + java.util.UUID.randomUUID().toString().take(8)
        compose.onNodeWithText("New document").performClick()
        compose.onNodeWithText("Document title").performTextInput(title)
        compose.onNodeWithText("Your source").performTextInput("Hello  world 🙂")
        compose.onNodeWithTag("writing-scroll").performScrollToNode(hasText("Save"))
        compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { model.status == "Saved on this device." }
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText(title).assertIsDisplayed()
    }
}
