// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
package app.nexwrite.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nexwrite.android.model.SourceGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NoticesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NexWriteTheme { Surface(Modifier.fillMaxSize()) {
            var content by remember { mutableStateOf("Loading bundled notices…") }
            var page by remember { mutableIntStateOf(0) }
            LaunchedEffect(Unit) {
                content = withContext(Dispatchers.IO) { runCatching { assets.open("notices/THIRD_PARTY_NOTICES.txt").use { decodeSource(readLimited(it, 8 * 1024 * 1024)) } }.getOrDefault("Bundled notices could not be opened.") }
            }
            val chunks = remember(content) {
                buildList { var start = 0; while (start < content.length) { var end = (start + 16_000).coerceAtMost(content.length); if (!SourceGuard.boundary(content, end)) end--; add(content.substring(start, end)); start = end } }.ifEmpty { listOf("") }
            }
            Column(Modifier.systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Third-party notices", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { finish() }) { Text("Close") }
                    TextButton(onClick = { page-- }, enabled = page > 0) { Text("Previous") }
                    TextButton(onClick = { page++ }, enabled = page + 1 < chunks.size) { Text("Next") }
                }
                Text("Page ${page + 1} of ${chunks.size}", style = MaterialTheme.typography.labelMedium)
                key(page) { SelectionContainer(Modifier.weight(1f).verticalScroll(rememberScrollState())) { Text(chunks[page.coerceIn(chunks.indices)], style = MaterialTheme.typography.bodySmall) } }
            }
        } } }
    }
}
