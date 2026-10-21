// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
package app.nexwrite.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nexwrite.android.model.SourceGuard
import app.nexwrite.core.NativeBridge
import app.nexwrite.protocol.AnalysisResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class ProcessTextActivity : ComponentActivity() {
    companion object { private val analysisLock = Mutex() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = runCatching { if (intent.action == Intent.ACTION_PROCESS_TEXT && intent.type == "text/plain") intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() else null }.getOrNull()
        val readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        setContent {
            NexWriteTheme {
                Surface(Modifier.fillMaxSize()) {
                    var text by rememberSaveable { mutableStateOf(source ?: "") }
                    var version by rememberSaveable { mutableLongStateOf(0) }
                    var analysis by remember { mutableStateOf<AnalysisResult?>(null) }
                    var status by remember { mutableStateOf(if (source == null) "No supported selection was received." else "Checks run locally. This selection is not saved or uploaded.") }
                    LaunchedEffect(text, version) {
                        analysis = null
                        val snapshot = text; val stamp = version
                        if (source != null && SourceGuard.error(snapshot) == null) {
                            try {
                                val result = withContext(Dispatchers.Default) { analysisLock.withLock { Json.decodeFromString<AnalysisResult>(NativeBridge.analyzeJson(snapshot, stamp)) } }
                                if (text == snapshot && version == stamp) analysis = result
                            } catch (error: CancellationException) { throw error }
                            catch (_: Throwable) { status = "Local checks unavailable. You can still preview this selection." }
                        }
                    }
                    Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("NexWrite selection", style = MaterialTheme.typography.headlineMedium)
                        Text(if (readOnly) "Read-only selection" else "Review a replacement before returning it")
                        Text(status, style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(text, { text = it; version++ }, Modifier.fillMaxWidth().heightIn(min = 240.dp), label = { Text("Selected source") }, readOnly = readOnly, isError = SourceGuard.error(text) != null)
                        analysis?.let { result ->
                            Text("${result.suggestions.size} suggestions · one local spacing rule")
                            result.suggestions.take(20).forEach { suggestion ->
                                Text(suggestion.explanation.short)
                                if (!readOnly) TextButton(onClick = {
                                    val range = suggestion.span.utf16
                                    val start = range[0].toInt(); val end = range[1].toInt()
                                    if (result.doc_version == version && SourceGuard.boundary(text, start) && SourceGuard.boundary(text, end) && start <= end && text.substring(start, end) == suggestion.original) {
                                        text = text.replaceRange(start, end, suggestion.replacements.first().text); version++
                                    }
                                }) { Text("Accept correction") }
                            }
                        }
                        if (!readOnly) Button(onClick = { setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text)); finish() }, enabled = source != null && SourceGuard.error(text) == null) { Text("Apply and return selection") }
                        OutlinedButton(onClick = { setResult(RESULT_CANCELED); finish() }) { Text("Close without returning changes") }
                    }
                }
            }
        }
    }
}
