// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
package app.nexwrite.android

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import app.nexwrite.android.data.StoredDocument
import app.nexwrite.android.model.SourceGuard
import app.nexwrite.android.model.TargetSnapshot
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

class MainActivity : ComponentActivity() {
    private lateinit var model: WritingModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        model = ViewModelProvider(this)[WritingModel::class.java]
        if (savedInstanceState == null) receive(intent)
        setContent { NexWriteTheme(model.preferences.appearance) { WritingApp(model) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receive(intent) }
    private fun receive(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND || intent.type !in setOf("text/plain", "text/markdown")) return
        val text = runCatching { intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() }.getOrNull()
        if (text == null) model.message("This share did not contain supported text.") else model.receive(text)
    }
}

@Composable
fun NexWriteTheme(appearance: String = "system", content: @Composable () -> Unit) {
    val dark = appearance == "dark" || (appearance == "system" && isSystemInDarkTheme())
    val colors = if (dark) darkColorScheme(primary = Color(0xffc7ec88), onPrimary = Color(0xff23330c), background = Color(0xff121711), surface = Color(0xff1c231b), surfaceVariant = Color(0xff293326))
        else lightColorScheme(primary = Color(0xff41651b), onPrimary = Color.White, background = Color(0xfff5f7ed), surface = Color(0xffffffff), surfaceVariant = Color(0xffe6eddb))
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun WritingApp(model: WritingModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmNew by remember { mutableStateOf(false) }
    var clearData by remember { mutableStateOf(false) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { decodeSource(readLimited(it, SourceGuard.MAX_BYTES)) } ?: error("Unreadable") } }
                .onSuccess { model.receive(it) }.onFailure { model.message("Could not import. Choose a UTF-8 text file of at most 64 KiB.") }
        }
    }
    var exportSource by rememberSaveable { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val snapshot = exportSource
        exportSource = ""
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(snapshot.toByteArray(Charsets.UTF_8)) } ?: error("Unwritable") } }
                .onSuccess { model.message("Exported source.") }.onFailure { model.message("Export failed. Your source is still in the editor.") }
        }
    }
    val pages = listOf("Library", "Write", "Assist", "Settings")
    Scaffold(
        topBar = { Surface(tonalElevation = 2.dp) { Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("NexWrite", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("A little space to think clearly", style = MaterialTheme.typography.labelMedium) }
            Text("PREVIEW", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
        } } },
        bottomBar = { NavigationBar { pages.forEachIndexed { index, name -> NavigationBarItem(selected = model.page == name, onClick = { model.page = name }, icon = { Text(listOf("▤", "✎", "✧", "⚙")[index]) }, label = { Text(name) }) } } },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize().testTag("writing-scroll"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text(model.page, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold) }
            when (model.page) {
                "Library" -> {
                    item { Note("Your words, on your device", "Only documents you save appear here. No account or network is needed.") }
                    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { if (model.dirty) confirmNew = true else model.newDocument() }) { Text("New document") }
                        OutlinedButton(onClick = { import.launch(arrayOf("text/plain", "text/markdown")) }) { Text("Import") }
                    } }
                    item { LibraryList(model) }
                }
                "Write" -> {
                    item { Editor(model) }
                    item { FlowActions(listOf("Save" to { model.save() }, "Import" to { import.launch(arrayOf("text/plain", "text/markdown")) }, "Export" to {
                        if (SourceGuard.error(model.draft.source) == null) { exportSource = model.draft.source; export.launch((model.draft.title.ifBlank { "NexWrite document" }).replace(Regex("[^\\p{L}\\p{N} _-]"), "").take(70) + if (model.draft.format == "markdown") ".md" else ".txt") }
                    })) }
                    item { OutlinedButton(onClick = { model.undo() }, enabled = model.canUndo) { Text("Undo accepted change") } }
                    item { AnalysisCards(model) }
                }
                "Assist" -> item { AssistPanel(model) }
                "Settings" -> {
                    item { SettingsPanel(model) }
                    item { Note("Local analysis", "One original spacing rule, source counts and labelled English readability estimates. This preview supports en-US plain text and Markdown; protected Markdown content is excluded.") }
                    item { Note("Android keyboard", "Enable NexWrite in system settings, then choose it as your keyboard. Private, password and no-suggestions fields keep checks off. No typing history is collected.") }
                    item { FlowActions(listOf("Enable keyboard" to { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }, "Choose keyboard" to { (context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() })) }
                    item { OutlinedButton(onClick = { context.startActivity(Intent(context, NoticesActivity::class.java)) }) { Text("Third-party licenses and notices") } }
                    item { OutlinedButton(onClick = { clearData = true }) { Text("Clear saved documents and settings") } }
                    item { Note("About this preview", "NexWrite 0.2.0-preview.1 · Native Android editor and keyboard. AI needs your own configured backend. Other native platforms and cloud document sync are still in development. All rights reserved.") }
                }
            }
            item { Text(model.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
    if (model.pendingReceived != null) AlertDialog(onDismissRequest = model::cancelReceived, title = { Text("Replace your unsaved draft?") }, text = { Text("The imported or shared source is ready. Save or export your current draft first if you need a copy.") }, confirmButton = { TextButton(onClick = model::confirmReceived) { Text("Discard and import") } }, dismissButton = { TextButton(onClick = model::cancelReceived) { Text("Keep editing") } })
    if (confirmNew) AlertDialog(onDismissRequest = { confirmNew = false }, title = { Text("Start another document?") }, text = { Text("Unsaved source stays only in memory. Save or export it first if you need a copy.") }, confirmButton = { TextButton(onClick = { confirmNew = false; model.newDocument() }) { Text("Discard and start") } }, dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("Keep editing") } })
    if (clearData) AlertDialog(onDismissRequest = { clearData = false }, title = { Text("Clear this app’s local data?") }, text = { Text("Saved documents, connection settings and your current draft will be removed from this device.") }, confirmButton = { TextButton(onClick = { clearData = false; model.clearLocalData() }) { Text("Clear data") } }, dismissButton = { TextButton(onClick = { clearData = false }) { Text("Cancel") } })
}

@Composable
private fun LibraryList(model: WritingModel) {
    var search by remember { mutableStateOf("") }
    var delete by remember { mutableStateOf<StoredDocument?>(null) }
    var rename by remember { mutableStateOf<StoredDocument?>(null) }
    var title by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<StoredDocument?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Search saved titles and source") }, singleLine = true)
        val found = model.documents.filter { it.title.contains(search, true) || it.source.contains(search, true) }
        if (found.isEmpty()) Note(if (search.isBlank()) "Your library is ready" else "No matching documents", if (search.isBlank()) "Start writing, then Save to keep your first document here." else "Try a different search.")
        found.forEach { document -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(document.title, style = MaterialTheme.typography.titleMedium)
            Text(document.source.take(140).ifBlank { "Empty document" }, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
            Text("Saved ${document.updatedAt.take(16).replace('T', ' ')} UTC", style = MaterialTheme.typography.labelSmall)
            FlowActions(listOf("Open" to { if (model.dirty) open = document else model.open(document) }, "Rename" to { title = document.title; rename = document }, "Duplicate" to { model.duplicate(document) }, "Delete" to { delete = document }))
        } } }
    }
    delete?.let { document -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("Delete this saved document?") }, text = { Text(document.title) }, confirmButton = { TextButton(onClick = { model.delete(document); delete = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancel") } }) }
    rename?.let { document -> AlertDialog(onDismissRequest = { rename = null }, title = { Text("Rename document") }, text = { OutlinedTextField(title, { title = it }, label = { Text("Title") }) }, confirmButton = { TextButton(onClick = { model.rename(document, title); rename = null }, enabled = SourceGuard.validTitle(title)) { Text("Rename") } }, dismissButton = { TextButton(onClick = { rename = null }) { Text("Cancel") } }) }
    open?.let { document -> AlertDialog(onDismissRequest = { open = null }, title = { Text("Open saved document?") }, text = { Text("Your current unsaved draft will be replaced. Save or export it first if needed.") }, confirmButton = { TextButton(onClick = { model.open(document); open = null }) { Text("Discard and open") } }, dismissButton = { TextButton(onClick = { open = null }) { Text("Keep editing") } }) }
}

@Composable
private fun Editor(model: WritingModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (model.dirty) "Unsaved changes · source editor" else "Source editor", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(model.draft.title, { model.title(it) }, Modifier.fillMaxWidth(), label = { Text("Document title") }, singleLine = true, isError = model.draft.title.isNotBlank() && !SourceGuard.validTitle(model.draft.title))
        ChoiceRow(listOf("plain", "markdown"), model.draft.format, model::format)
        OutlinedTextField(model.editor, { model.edit(it) }, Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 480.dp), label = { Text("Your source") }, placeholder = { Text("Let the first thought find its way onto the page…") }, supportingText = { Text("${model.draft.source.toByteArray(Charsets.UTF_8).size} / 65,536 UTF-8 bytes · en-US") }, isError = SourceGuard.error(model.draft.source) != null)
    }
}

@Composable
private fun AnalysisCards(model: WritingModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(model.analysisState, style = MaterialTheme.typography.titleMedium)
        model.analysis?.let { result ->
            Note("Source insights", "${result.metrics.word_count} words · ${result.metrics.sentence_count} sentences · ${result.metrics.source_grapheme_count} graphemes · ${result.metrics.reading_seconds.toInt()} seconds estimated reading")
            result.metrics.scores.forEach { score -> Text("${score.kind.name.lowercase().replace('_', ' ')}: ${"%.1f".format(score.value)} (estimate)", style = MaterialTheme.typography.bodySmall) }
            if (result.metrics.warnings.isNotEmpty()) Text(result.metrics.warnings.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            result.suggestions.take(20).forEach { suggestion -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(suggestion.explanation.short, fontWeight = FontWeight.SemiBold)
                Text(suggestion.explanation.long, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { model.accept(suggestion) }, enabled = suggestion.doc_version == model.draft.version) { Text("Accept correction") }
            } } }
            if (result.suggestions.size > 20) Text("Showing 20 of ${result.suggestions.size} suggestions. Accept a correction to refresh.")
        }
    }
}

@Composable
private fun AssistPanel(model: WritingModel) {
    var task by remember { mutableStateOf("rewrite") }
    var preset by remember { mutableStateOf("clearer") }
    var provider by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("") }
    var selectionOnly by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf<TargetSnapshot?>(null) }
    val providers = model.capabilities?.get("providers")?.jsonArray?.mapNotNull { it as? JsonObject }?.filter { it["configured"]?.jsonPrimitive?.booleanOrNull == true } ?: emptyList()
    val profile = providers.firstOrNull { it["id"]?.jsonPrimitive?.content == provider }
    val models = profile?.get("models")?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
    LaunchedEffect(providers.toString()) { if (providers.none { it["id"]?.jsonPrimitive?.content == provider }) provider = providers.firstOrNull()?.get("id")?.jsonPrimitive?.content ?: "" }
    LaunchedEffect(provider, models.toString()) { if (selectedModel !in models) selectedModel = models.firstOrNull() ?: "" }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Note("Your words stay local by default", "AI sends only the source you preview, to the backend and provider you choose. Review every result; it can be wrong.")
        Text(model.aiState)
        OutlinedButton(onClick = model::discover, enabled = model.preferences.backend.isNotEmpty() && !model.busy) { Text("Discover providers") }
        if (model.capabilities != null && providers.isEmpty()) Text("No provider is configured on this backend. Ask its operator to configure one.")
        if (providers.isNotEmpty()) {
            ChoiceRow(listOf("rewrite", "summarize", "compose"), task) { task = it; preset = "clearer" }
            if (task == "rewrite") ChoiceRow(listOf("proofread", "clearer", "shorten", "expand", "formal", "friendly", "direct"), preset) { preset = it }
            Text(if (task == "compose") "Use the editor source as your composition brief." else "Choose the source to send.")
            Row { Checkbox(selectionOnly, { selectionOnly = it }); Text("Use selected source only", Modifier.padding(top = 12.dp)) }
            Text("Provider", style = MaterialTheme.typography.labelLarge)
            ChoiceRow(providers.map { it.getValue("id").jsonPrimitive.content }, provider) { provider = it }
            Text("Model", style = MaterialTheme.typography.labelLarge)
            ChoiceRow(models, selectedModel) { selectedModel = it }
            Button(onClick = { consent = model.snapshot(provider, selectedModel, task, preset, selectionOnly); if (consent == null) model.message("Choose nonempty source of at most 32 KiB, without splitting Unicode characters.") }, enabled = selectedModel.isNotEmpty() && !model.busy) { Text("Preview AI request") }
        }
        if (model.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); OutlinedButton(onClick = model::cancelAi) { Text("Cancel request") } }
        model.proposal?.let { result ->
            Note("AI proposal · ${result.getValue("provider").jsonPrimitive.content} / ${result.getValue("model").jsonPrimitive.content}", result.getValue("replacement").jsonPrimitive.content)
            result["warnings"]?.jsonArray?.forEach { Text(it.jsonPrimitive.content, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = model::applyAi, enabled = model.proposalTarget?.matches(model.draft) == true) { Text("Apply proposal") }
            OutlinedButton(onClick = model::undo, enabled = model.canUndo) { Text("Undo accepted change") }
        }
    }
    consent?.let { target -> AlertDialog(onDismissRequest = { consent = null }, title = { Text("Send this source to AI?") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Destination: ${model.preferences.backend}\nProvider: ${target.provider}\nModel: ${target.model}\nTask: ${target.task} / ${target.preset}")
        Text(target.requestText.take(1500) + if (target.requestText.length > 1500) "\n…preview truncated; ${target.requestText.toByteArray(Charsets.UTF_8).size} bytes will be sent" else "", maxLines = 12)
        Text("This request uploads the chosen source. Provider retention depends on your backend configuration.", style = MaterialTheme.typography.bodySmall)
    } }, confirmButton = { TextButton(onClick = { consent = null; model.runAi(target) }) { Text("I consent · Send") } }, dismissButton = { TextButton(onClick = { consent = null }) { Text("Cancel") } }) }
}

@Composable
private fun SettingsPanel(model: WritingModel) {
    var backend by remember(model.preferences.backend) { mutableStateOf(model.preferences.backend) }
    var token by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Note("Make it yours", "Appearance and saved documents stay on this device. Automatic saving is optional and starts off.")
        ChoiceRow(listOf("system", "light", "dark"), model.preferences.appearance) { model.settings(it, model.preferences.autosave) }
        Row { Switch(model.preferences.autosave, { model.settings(model.preferences.appearance, it) }); Text("Automatically save to this device", Modifier.padding(start = 10.dp, top = 12.dp)) }
        Text("AI backend", style = MaterialTheme.typography.titleMedium)
        Text("Leave this empty to write and check offline. Enter your self-hosted HTTPS address when ready.")
        OutlinedTextField(backend, { backend = it }, Modifier.fillMaxWidth(), label = { Text("HTTPS backend address") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("Backend access token") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, supportingText = { Text(if (model.preferences.tokenConfigured) "A token is saved securely. Enter it again to keep it when saving; a blank value clears it." else "Optional for a local personal backend; stored using Android Keystore.") })
        Button(onClick = { model.connection(backend, token); token = "" }) { Text("Save connection") }
    }
}

@Composable
private fun Note(title: String, body: String) { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(body, style = MaterialTheme.typography.bodyMedium) } } }
@Composable
private fun ChoiceRow(values: List<String>, selected: String, onSelect: (String) -> Unit) { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { values.forEach { value -> FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(value.replaceFirstChar { it.uppercase() }) }) } } }
@Composable
private fun FlowActions(actions: List<Pair<String, () -> Unit>>) { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) { actions.forEach { (title, action) -> TextButton(onClick = action) { Text(title) } } } }

internal fun readLimited(stream: InputStream, maximum: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) { val count = stream.read(buffer); if (count < 0) break; require(output.size() <= maximum - count); output.write(buffer, 0, count) }
    return output.toByteArray()
}
internal fun decodeSource(bytes: ByteArray): String = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
