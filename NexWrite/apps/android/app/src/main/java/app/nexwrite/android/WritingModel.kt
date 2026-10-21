// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
package app.nexwrite.android

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.nexwrite.android.data.Preferences
import app.nexwrite.android.data.StoredDocument
import app.nexwrite.android.model.*
import app.nexwrite.android.network.ApiTransport
import app.nexwrite.core.NativeBridge
import app.nexwrite.protocol.AnalysisResult
import app.nexwrite.protocol.Suggestion
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

class WritingModel(application: Application) : AndroidViewModel(application) {
    private val store = (application as NexWriteApplication).store
    private val history = EditHistory()
    var draft by mutableStateOf(history.draft); private set
    var editor by mutableStateOf(TextFieldValue()); private set
    var preferences by mutableStateOf(Preferences()); private set
    var documents by mutableStateOf<List<StoredDocument>>(emptyList()); private set
    var page by mutableStateOf("Library")
    var status by mutableStateOf("Drafts stay in memory until you save."); private set
    var analysis by mutableStateOf<AnalysisResult?>(null); private set
    var analysisState by mutableStateOf("Waiting for source"); private set
    var dirty by mutableStateOf(false); private set
    var pendingReceived by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf(false); private set
    var capabilities by mutableStateOf<JsonObject?>(null); private set
    var proposal by mutableStateOf<JsonObject?>(null); private set
    var proposalTarget by mutableStateOf<TargetSnapshot?>(null); private set
    var aiState by mutableStateOf("Connect a backend in Settings to use AI."); private set
    val canUndo get() = history.canUndo
    private var analysisJob: Job? = null
    private var aiJob: Job? = null
    private var autosaveJob: Job? = null
    private val analysisLock = Mutex()
    private var connectionGeneration = 0L
    private var aiGeneration = 0L
    private val storageLock = Mutex()
    private var storageGeneration = 0L
    private var autosaveGeneration = 0L
    private var automaticSavingBlocked = false

    init {
        viewModelScope.launch { store.preferences.collect { preferences = it } }
        refresh()
        scheduleAnalysis()
    }

    fun open(document: StoredDocument) = attempt("Could not open this saved document.") {
        switchTo(store.draft(document)); dirty = false; page = "Write"; status = "Opened from this device."
    }
    fun newDocument() { switchTo(Draft()); dirty = false; page = "Write"; status = "New document · not saved" }
    fun receive(text: String) {
        val error = SourceGuard.error(text)
        if (error != null) { status = error; return }
        if (dirty) { pendingReceived = text; return }
        adoptReceived(text)
    }
    fun confirmReceived() { pendingReceived?.let { adoptReceived(it) }; pendingReceived = null }
    fun cancelReceived() { pendingReceived = null }
    private fun adoptReceived(text: String) {
        switchTo(Draft(source = text, version = 1)); dirty = true; page = "Write"; status = "Imported into memory. Save to keep it on this device."
    }
    private fun switchTo(next: Draft) {
        autosaveJob?.cancel();
        cancelAi(); history.open(next); draft = history.draft; editor = TextFieldValue(draft.source); proposal = null; proposalTarget = null
        scheduleAnalysis()
    }
    fun edit(value: TextFieldValue) {
        val changed = editor.text != value.text
        editor = value
        if (changed) { history.edit(source = value.text); changed() }
        if (value.composition != null) { analysisJob?.cancel(); analysis = null; analysisState = "Waiting for composition" }
        else if (changed || analysis == null) scheduleAnalysis()
    }
    fun title(value: String) { history.edit(title = value); changed() }
    fun format(value: String) { history.edit(format = value); changed() }
    private fun changed() {
        draft = history.draft; dirty = true; analysis = null; proposal = null; proposalTarget = null; cancelAi()
        scheduleAnalysis(); autosaveJob?.cancel()
        if (preferences.autosave && !automaticSavingBlocked) scheduleAutosave(700)
    }
    fun undo() {
        if (history.undo()) afterReplacement("Undid the accepted change.")
    }
    fun accept(suggestion: Suggestion) {
        val start = suggestion.span.utf16.getOrNull(0)?.toInt() ?: return
        val end = suggestion.span.utf16.getOrNull(1)?.toInt() ?: return
        val target = TargetSnapshot(draft.id, suggestion.doc_version, start, end, suggestion.original, suggestion.original, "", "", "", "")
        val replacement = suggestion.replacements.firstOrNull()?.text ?: return
        if (history.replace(target, replacement)) afterReplacement("Accepted one local correction.")
    }
    private fun afterReplacement(message: String) {
        cancelAi(); draft = history.draft; editor = TextFieldValue(draft.source); dirty = true; proposal = null; proposalTarget = null; status = message; scheduleAnalysis()
        if (preferences.autosave && !automaticSavingBlocked) scheduleAutosave(0)
    }
    private fun scheduleAutosave(delayMilliseconds: Long) {
        autosaveJob?.cancel()
        val generation = autosaveGeneration
        autosaveJob = viewModelScope.launch { delay(delayMilliseconds); saveNow(automaticGeneration = generation) }
    }
    private fun scheduleAnalysis() {
        analysisJob?.cancel(); analysis = null
        val snapshot = draft
        val error = SourceGuard.error(snapshot.source)
        if (error != null) { analysisState = error; return }
        analysisState = "Checking locally…"
        analysisJob = viewModelScope.launch {
            delay(300)
            try {
                val result = withContext(Dispatchers.Default) { analysisLock.withLock {
                    ApiTransport.json.decodeFromString<AnalysisResult>(NativeBridge.analyzeJson(snapshot.source, snapshot.version, "en-US", snapshot.format))
                } }
                ensureActive()
                if (draft.id == snapshot.id && draft.version == snapshot.version && editor.composition == null) {
                    analysis = result; analysisState = if (result.suggestions.isEmpty()) "No suggestions from the current local rule." else "${result.suggestions.size} local suggestions"
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Throwable) { if (draft.id == snapshot.id && draft.version == snapshot.version) analysisState = "Local analysis unavailable. Editing still works." }
        }
    }
    fun save() {
        val snapshot = draft; val generation = storageGeneration
        viewModelScope.launch { saveNow(snapshot, generation) }
    }
    private suspend fun saveNow(snapshot: Draft = draft, generation: Long = storageGeneration, automaticGeneration: Long? = null) {
        status = "Saving…"
        try {
            storageLock.withLock {
                if (generation != storageGeneration || (automaticGeneration != null && (automaticGeneration != autosaveGeneration || automaticSavingBlocked || !preferences.autosave))) return
                store.save(snapshot)
            }
            if (generation != storageGeneration) return
            if (draft.id == snapshot.id && draft.version == snapshot.version) { dirty = false; status = "Saved on this device." }
            refresh()
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { if (generation == storageGeneration) status = "Could not save. Your current source is still in memory; export a copy." }
    }
    fun duplicate(document: StoredDocument) {
        val generation = storageGeneration
        attempt("Could not duplicate this document.") {
            storageLock.withLock {
                if (generation != storageGeneration) return@withLock
                val original = store.draft(document)
                store.save(original.copy(id = UUID.randomUUID().toString(), title = (original.title.substring(0, original.title.offsetByCodePoints(0, minOf(105, original.title.codePointCount(0, original.title.length)))) + " copy"), version = 0, createdAt = java.time.Instant.now().toString()))
            }
            if (generation == storageGeneration) { refresh(); status = "Created a local copy." }
        }
    }
    fun rename(document: StoredDocument, title: String) {
        val generation = storageGeneration
        attempt("Use a title with 1–120 Unicode characters.") {
            storageLock.withLock {
                if (generation != storageGeneration) return@withLock
                require(SourceGuard.validTitle(title)); val original = store.draft(document)
                store.save(original.copy(title = title.trim(), version = original.version + 1))
                if (draft.id == original.id) { history.edit(title = title.trim()); changed() }
            }
            if (generation == storageGeneration) refresh()
        }
    }
    fun delete(document: StoredDocument) = attempt("Could not delete this document.") {
        autosaveJob?.cancel(); storageGeneration++
        storageLock.withLock { store.delete(document.id) }
        refresh(); status = "Deleted from this device."
    }
    fun clearLocalData() {
        autosaveJob?.cancel(); automaticSavingBlocked = true; storageGeneration++; connectionGeneration++; cancelAi()
        switchTo(Draft()); dirty = false; capabilities = null; documents = emptyList(); pendingReceived = null
        attempt("Could not clear local data.") {
            storageLock.withLock { store.clearDocuments(); store.clearSettings() }
            refresh(); status = "Cleared this app’s saved documents and settings."
        }
    }
    fun settings(appearance: String, autosave: Boolean) {
        automaticSavingBlocked = !autosave
        if (!autosave) { autosaveJob?.cancel(); autosaveGeneration++ }
        attempt("Could not save settings.") { store.settings(appearance, autosave) }
    }
    fun connection(address: String, token: String) = attempt("Could not save connection. Use a valid HTTPS address.") {
        cancelAi(); connectionGeneration++; capabilities = null; proposal = null; proposalTarget = null
        val validated = if (address.isBlank()) "" else ApiTransport.address(address)
        require(token.length <= 8192 && !token.contains('\r') && !token.contains('\n'))
        store.connection(validated, token); aiState = if (validated.isEmpty()) "Connect a backend in Settings to use AI." else "Connection saved. Discover providers in Assist."
        status = "Connection saved on this device."
    }
    fun discover() {
        if (preferences.backend.isEmpty()) { aiState = "Connect a backend in Settings first."; return }
        cancelAi(); val generation = aiGeneration; busy = true; aiState = "Connecting…"
        aiJob = viewModelScope.launch {
            try {
                val result = ApiTransport.capabilities(preferences.backend, store.token())
                ensureActive(); if (generation == aiGeneration) { capabilities = result; aiState = "Choose a configured provider and model." }
            } catch (_: TimeoutCancellationException) { if (generation == aiGeneration) aiState = "The backend request timed out." }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { if (generation == aiGeneration) aiState = "Could not connect. Check the backend address, token and network." }
            finally { if (generation == aiGeneration) busy = false }
        }
    }
    fun snapshot(provider: String, model: String, task: String, preset: String, selectionOnly: Boolean): TargetSnapshot? {
        val start = if (selectionOnly) editor.selection.min else 0
        val end = if (selectionOnly) editor.selection.max else draft.source.length
        if (start == end || !SourceGuard.boundary(draft.source, start) || !SourceGuard.boundary(draft.source, end)) return null
        val text = draft.source.substring(start, end)
        if (text.isBlank() || SourceGuard.error(text, SourceGuard.AI_MAX_BYTES) != null) return null
        return TargetSnapshot(draft.id, draft.version, start, end, text, text, provider, model, task, preset)
    }
    fun runAi(target: TargetSnapshot) {
        if (!target.matches(draft) || preferences.backend.isEmpty()) return
        cancelAi(); val generation = aiGeneration; busy = true; proposal = null; proposalTarget = null; aiState = "Requesting a proposal…"
        aiJob = viewModelScope.launch {
            try {
                val result = ApiTransport.rewrite(preferences.backend, store.token(), target)
                ensureActive()
                if (generation == aiGeneration && target.matches(draft)) { proposal = result; proposalTarget = target; aiState = "Review the proposal before applying it." }
            } catch (_: TimeoutCancellationException) { if (generation == aiGeneration) aiState = "The backend request timed out. Your source is unchanged." }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (generation == aiGeneration) aiState = if (error is IllegalStateException) error.message ?: "The request failed." else if (error is IllegalArgumentException) "The backend returned an invalid result. Your source is unchanged." else "Could not reach the backend. Your source is unchanged." }
            finally { if (generation == aiGeneration) busy = false }
        }
    }
    fun cancelAi() { aiGeneration++; aiJob?.cancel(); aiJob = null; busy = false; aiState = "Canceled. Source unchanged." }
    fun applyAi() {
        val result = proposal ?: return; val target = proposalTarget ?: return
        if (history.replace(target, result.getValue("replacement").jsonPrimitive.content,
            AiProvenance(result.getValue("request_id").jsonPrimitive.content, target.provider, target.model, 0))) afterReplacement("Applied an AI proposal. Undo is available.")
    }
    fun message(value: String) { status = value }
    private fun refresh() {
        val generation = storageGeneration
        attempt("Saved documents could not be loaded. You can still edit in memory.") {
            val loaded = storageLock.withLock { store.documents() }
            if (generation == storageGeneration) documents = loaded
        }
    }
    private fun attempt(message: String, action: suspend () -> Unit) { viewModelScope.launch { try { action() } catch (error: CancellationException) { throw error } catch (_: Exception) { status = message } } }
}
