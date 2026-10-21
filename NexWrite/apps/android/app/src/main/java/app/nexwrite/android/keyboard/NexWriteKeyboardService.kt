// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
package app.nexwrite.android.keyboard

import android.content.Intent
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import app.nexwrite.android.MainActivity
import app.nexwrite.android.R
import app.nexwrite.android.model.SensitiveField
import app.nexwrite.android.model.SourceGuard
import app.nexwrite.core.NativeBridge
import app.nexwrite.protocol.AnalysisResult
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** No clipboard history, learning, network calls or background text collection. */
class NexWriteKeyboardService : InputMethodService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val analysisLock = Mutex()
    private var checkJob: Job? = null
    private var excluded = true
    private var composing = ""
    private var shift = false
    private var symbols = false
    private var stamp = 0L
    private var composingOutside = false
    private var selectionStart = -1
    private var selectionEnd = -1
    private var root: LinearLayout? = null
    private var status: TextView? = null
    private var accept: Button? = null
    private var check: Button? = null
    private var assist: Button? = null
    private data class Proposal(val text: String, val replacement: String, val stamp: Long, val start: Int, val end: Int)
    private var proposal: Proposal? = null

    override fun onCreateInputView(): View {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(6), dp(4), dp(4)); setBackgroundColor(Color.rgb(22, 30, 21)) }
        root = layout; render(); return layout
    }
    private fun render() {
        val layout = root ?: return; layout.removeAllViews()
        status = TextView(this).apply { text = getString(if (excluded) R.string.keyboard_private else R.string.keyboard_ready); setTextColor(Color.rgb(210, 231, 191)); setPadding(dp(8), dp(4), dp(8), dp(4)); textSize = 13f }
        layout.addView(status)
        val tools = LinearLayout(this)
        check = key(getString(R.string.keyboard_check)) { inspectSelection() }.also { tools.addView(it, weight()) }
        accept = key(getString(R.string.keyboard_accept)) { applyProposal() }.also { it.visibility = View.GONE; tools.addView(it, weight()) }
        assist = key(getString(R.string.keyboard_assist)) { openAssistant() }.also { tools.addView(it, weight()) }
        layout.addView(tools)
        val rows = if (symbols) listOf("1234567890", "@#£_&-+()/", "*\"':;!?.,") else listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        rows.forEach { letters ->
            val row = LinearLayout(this)
            letters.forEach { char -> val shown = if (shift && !symbols) char.uppercase() else char.toString(); row.addView(key(shown) { type(shown) }, weight()) }
            layout.addView(row)
        }
        val bottom = LinearLayout(this)
        bottom.addView(key(if (symbols) "ABC" else "123") { finishComposition(); symbols = !symbols; render() }, weight())
        bottom.addView(key("⇧", getString(R.string.keyboard_shift)) { shift = !shift; render() }, weight())
        bottom.addView(key("Space", getString(R.string.keyboard_space)) { finishComposition(); currentInputConnection?.commitText(" ", 1); invalidate() }, LinearLayout.LayoutParams(0, dp(50), 2f))
        bottom.addView(key("⌫", getString(R.string.keyboard_backspace)) { backspace() }.apply { setOnLongClickListener { if (!deleteSelection()) { finishComposition(); currentInputConnection?.deleteSurroundingTextInCodePoints(5, 0); invalidate() }; true } }, weight())
        bottom.addView(key("↵", getString(R.string.keyboard_enter)) { enter() }, weight())
        layout.addView(bottom); updateAvailability()
    }
    private fun key(label: String, description: String = label, action: () -> Unit): Button = Button(this).apply {
        text = label; contentDescription = description; textSize = if (label.length > 3) 11f else 18f
        setTextColor(Color.rgb(236, 244, 228)); setBackgroundColor(Color.rgb(45, 59, 40)); minWidth = 0; minimumWidth = 0
        setPadding(dp(1), 0, dp(1), 0); isAllCaps = false; setOnClickListener { action() }
    }
    private fun weight() = LinearLayout.LayoutParams(0, dp(50), 1f).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        excluded = attribute == null || SensitiveField.excluded(attribute.inputType, attribute.imeOptions) || attribute.privateImeOptions?.contains("incognito", true) == true
        composing = ""; composingOutside = false; selectionStart = attribute?.initialSelStart ?: -1; selectionEnd = attribute?.initialSelEnd ?: -1; invalidate(); render()
    }
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        composingOutside = candidatesStart >= 0 && candidatesEnd > candidatesStart
        selectionStart = newSelStart; selectionEnd = newSelEnd
        if (newSelStart != oldSelStart || newSelEnd != oldSelEnd) invalidate()
        if (composing.isNotEmpty() && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) { composing = ""; currentInputConnection?.finishComposingText() }
    }
    override fun onFinishInput() { checkJob?.cancel(); composing = ""; proposal = null; excluded = true; stamp++; super.onFinishInput() }
    override fun onFinishInputView(finishingInput: Boolean) { checkJob?.cancel(); proposal = null; finishComposition(); stamp++; super.onFinishInputView(finishingInput) }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    private fun type(value: String) {
        invalidate()
        if (excluded || symbols) { finishComposition(); currentInputConnection?.commitText(value, 1) }
        else { composing += value; currentInputConnection?.setComposingText(composing, 1) }
    }
    private fun deleteSelection(): Boolean {
        if (selectionStart >= 0 && selectionEnd >= 0 && selectionEnd != selectionStart) {
            finishComposition(); currentInputConnection?.commitText("", 1); invalidate(); return true
        }
        return false
    }
    private fun backspace() {
        if (deleteSelection()) return
        invalidate()
        if (composing.isNotEmpty()) {
            composing = composing.substring(0, composing.offsetByCodePoints(composing.length, -1))
            currentInputConnection?.setComposingText(composing, 1)
            if (composing.isEmpty()) currentInputConnection?.finishComposingText()
        } else currentInputConnection?.deleteSurroundingTextInCodePoints(1, 0)
    }
    private fun enter() {
        finishComposition(); invalidate()
        val options = currentInputEditorInfo?.imeOptions ?: 0
        val action = options and EditorInfo.IME_MASK_ACTION
        if ((options and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0 && action !in setOf(EditorInfo.IME_ACTION_NONE, EditorInfo.IME_ACTION_UNSPECIFIED)) currentInputConnection?.performEditorAction(action)
        else currentInputConnection?.commitText("\n", 1)
    }
    private fun finishComposition() { composing = ""; currentInputConnection?.finishComposingText(); composingOutside = false }
    private fun invalidate() { checkJob?.cancel(); proposal = null; stamp++; accept?.visibility = View.GONE; updateAvailability() }
    private fun updateAvailability() { check?.isEnabled = !excluded; assist?.isEnabled = !excluded; status?.text = getString(if (excluded) R.string.keyboard_private else R.string.keyboard_ready) }
    private fun selected(): String? {
        if (excluded || composingOutside || composing.isNotEmpty() || selectionStart < 0 || selectionEnd < 0 || selectionEnd == selectionStart) return null
        val selected = currentInputConnection?.getSelectedText(0)?.toString() ?: return null
        return selected.takeIf { SourceGuard.error(it, NativeBridge.IME_MAX_UTF8_BYTES) == null }
    }
    private fun inspectSelection() {
        if (excluded) return
        finishComposition(); invalidate()
        val text = selected()
        if (text == null) { status?.text = "Select up to 16 KiB of source to check."; return }
        val version = stamp; val start = selectionStart; val end = selectionEnd
        status?.text = "Checking selection locally…"
        checkJob = scope.launch {
            try {
                val report = withContext(Dispatchers.Default) { analysisLock.withLock { Json.decodeFromString<AnalysisResult>(NativeBridge.analyzeJson(text, version)) } }
                ensureActive()
                if (excluded || stamp != version || selected() != text) return@launch
                val correction = report.suggestions.firstOrNull()
                if (correction == null) status?.text = "No suggestions from the local spacing rule."
                else {
                    val a = correction.span.utf16[0].toInt(); val b = correction.span.utf16[1].toInt()
                    if (!SourceGuard.boundary(text, a) || !SourceGuard.boundary(text, b) || a > b || text.substring(a, b) != correction.original) return@launch
                    proposal = Proposal(text, text.replaceRange(a, b, correction.replacements.first().text), version, start, end)
                    status?.text = correction.explanation.short; accept?.visibility = View.VISIBLE
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Throwable) { if (stamp == version) status?.text = "Local analysis unavailable." }
        }
    }
    private fun applyProposal() {
        val result = proposal ?: return
        if (!excluded && result.stamp == stamp && result.start == selectionStart && result.end == selectionEnd && selected() == result.text) {
            currentInputConnection?.beginBatchEdit(); currentInputConnection?.commitText(result.replacement, 1); currentInputConnection?.endBatchEdit()
        }
        invalidate()
    }
    private fun openAssistant() {
        if (excluded) return
        finishComposition()
        val source = selected()
        if (source == null) { status?.text = "Select source first. Nothing was sent."; return }
        startActivity(Intent(this, MainActivity::class.java).apply { action = Intent.ACTION_SEND; type = "text/plain"; putExtra(Intent.EXTRA_TEXT, source); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}
