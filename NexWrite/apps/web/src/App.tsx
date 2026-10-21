// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { useEffect, useRef, useState } from 'react';
import type { AnalysisResult, InputFormat, MetricKind, Suggestion } from '@nexwrite/protocol';
import { inputError } from './input';
import { initialSettings, KEYS, type Theme } from './storage';

type Document = { text: string; format: InputFormat; version: number };
type Phase = 'loading' | 'ready' | 'analyzing' | 'unavailable' | 'paused';
const EXAMPLE = 'Good writing gives an idea room to breathe.\n\nA clear sentence helps the reader move forward. An extra  space can interrupt that rhythm.\n\nStart with a small thought. Make it useful. Then make it yours.';
const scoreLabels: Record<MetricKind, string> = {
  type_token_ratio: 'Type / token ratio', flesch_reading_ease: 'Flesch reading ease',
  flesch_kincaid_grade: 'Flesch–Kincaid grade', gunning_fog: 'Gunning Fog', smog_grade: 'SMOG grade',
  coleman_liau_index: 'Coleman–Liau index', automated_readability_index: 'Automated readability index',
};
const warningLabels: Record<string, string> = {
  markdown_source_projection: 'Markdown counts describe retained source fragments, which can differ from rendered text.',
  markdown_content_omitted: 'Code, links, HTML, or other protected Markdown content is excluded from prose analysis.',
  markdown_prose_fragmented: 'Markdown syntax splits or withholds some prose fragments.',
  markdown_ambiguous_construct: 'An ambiguous Markdown construct is conservatively protected.',
  no_words: 'There are no analyzable words yet.',
  short_readability_sample: 'Readability formulas need at least 30 words and 3 sentences.',
  non_ascii_words: 'Some words contain non-ASCII characters; English formula coverage is limited.',
  numeric_tokens: 'Numeric tokens count as words but have no syllable estimate.',
  unsupported_readability_tokens: 'Some words are outside the ASCII English syllable heuristic; grade estimates are omitted.',
  incomplete_readability_projection: 'Readability grades are omitted because protected Markdown would make the projection incomplete.',
  smog_requires_30_sentences: 'SMOG requires at least 30 sentences and is omitted for shorter samples.',
  english_readability_estimate: 'English readability values use an uncalibrated syllable heuristic. They are estimates, not a personal assessment.',
};
const display = (value: number) => new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(value);

export function App() {
  const [initial] = useState(initialSettings);
  const [doc, setDoc] = useState<Document>({ text: initial.source, format: initial.format, version: initial.source ? 1 : 0 });
  const current = useRef(doc);
  const editor = useRef<HTMLTextAreaElement>(null);
  const worker = useRef<Worker | null>(null);
  const composing = useRef(false);
  const [isComposing, setIsComposing] = useState(false);
  const [result, setResult] = useState<AnalysisResult | null>(null);
  const [phase, setPhase] = useState<Phase>('loading');
  const [paused, setPaused] = useState(false);
  const [epoch, setEpoch] = useState(0);
  const [retry, setRetry] = useState(0);
  const [history, setHistory] = useState<Document[]>([]);
  const [theme, setTheme] = useState<Theme>(initial.theme);
  const [persist, setPersist] = useState(initial.persist);
  const [storageState, setStorageState] = useState(initial.warning ? 'Local restore unavailable' : initial.persist ? 'Saved locally' : 'Memory only');
  const [storageError, setStorageError] = useState<string | null>(initial.warning);
  const [notice, setNotice] = useState('');
  const validation = inputError(doc.text);

  function commit(text: string, format = current.current.format, force = false) {
    if (!force && text === current.current.text && format === current.current.format) return;
    const next = { text, format, version: current.current.version + 1 };
    current.current = next;
    setDoc(next);
    setResult(null);
    setNotice('');
  }
  function edit(text: string) {
    setHistory([]);
    if (composing.current) {
      const next = { ...current.current, text };
      current.current = next;
      setDoc(next);
      setResult(null);
    } else commit(text);
  }

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);

  useEffect(() => {
    if (paused) { setPhase('paused'); return; }
    setPhase('loading');
    let active = true;
    let instance: Worker;
    try {
      instance = new Worker(new URL('./analysis.worker.ts', import.meta.url), { type: 'module' });
      worker.current = instance;
      instance.onmessage = (event: MessageEvent) => {
        if (!active) return;
        const data = event.data;
        if (data.type === 'ready') { setPhase('ready'); setEpoch((value) => value + 1); return; }
        if (data.type === 'unavailable') { setPhase('unavailable'); return; }
        const latest = current.current;
        if (composing.current) return;
        if (data.type === 'result') {
          const next = data.result as AnalysisResult;
          if (next.doc_version !== latest.version || next.input_format !== latest.format) return;
          setResult(next);
          setPhase('ready');
        } else if (data.doc_version === latest.version && data.input_format === latest.format) {
          setResult(null);
          setPhase('unavailable');
        }
      };
      instance.onerror = (event) => { event.preventDefault(); if (active) { setResult(null); setPhase('unavailable'); } };
    } catch { setPhase('unavailable'); }
    return () => { active = false; instance?.terminate(); worker.current = null; };
  }, [paused, retry]);

  useEffect(() => {
    if (paused || isComposing || validation || !worker.current || phase === 'loading' || phase === 'unavailable') return;
    const snapshot = doc;
    setPhase('analyzing');
    const timer = window.setTimeout(() => {
      if (composing.current || current.current.version !== snapshot.version) return;
      worker.current?.postMessage({ type: 'analyze', text: snapshot.text, doc_version: snapshot.version, locale: 'en-US', input_format: snapshot.format });
    }, 180);
    return () => window.clearTimeout(timer);
    // phase is deliberately not a dependency: it describes this same request.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [doc, paused, isComposing, validation, epoch]);

  useEffect(() => {
    if (!persist) return;
    if (isComposing || validation) { setStorageState('Not saved: invalid or composing source'); return; }
    setStorageState('Saving locally…');
    const timer = window.setTimeout(() => {
      try {
        localStorage.setItem(KEYS.draft, JSON.stringify({ version: 1, source: doc.text, format: doc.format }));
        localStorage.setItem(KEYS.consent, '1');
        setStorageState('Saved locally');
        setStorageError(null);
      } catch {
        setStorageState('Storage unavailable');
        setStorageError('This browser could not save the local draft. Editing and analysis still work in memory.');
      }
    }, 300);
    return () => window.clearTimeout(timer);
  }, [persist, doc, isComposing, validation]);

  function togglePersistence(enabled: boolean) {
    setPersist(enabled);
    setStorageError(null);
    if (!enabled) {
      try {
        localStorage.removeItem(KEYS.draft);
        localStorage.removeItem(KEYS.consent);
        setStorageState('Memory only');
      } catch {
        setStorageState('Storage unavailable');
        setStorageError('The browser could not remove the saved draft. Clear this site’s storage in browser settings. Your current text remains in memory.');
      }
    }
  }
  function clearLocalData() {
    setPersist(false);
    setTheme('dark');
    try {
      for (const key of Object.values(KEYS)) localStorage.removeItem(key);
      setStorageState('Memory only');
      setStorageError(null);
      setNotice('Local NexWrite data cleared. Your current source is still in the editor.');
    } catch {
      setStorageState('Storage unavailable');
      setStorageError('The browser could not remove all local NexWrite data. Clear this site’s storage in browser settings.');
    }
  }
  function changeTheme(next: Theme) {
    setTheme(next);
    try { localStorage.setItem(KEYS.theme, next); }
    catch { setStorageError('The appearance preference could not be saved. The selected theme still works for this visit.'); }
  }
  function accept(suggestion: Suggestion, replacement: string) {
    const latest = current.current;
    const [start, end] = suggestion.span.utf16;
    if (composing.current || paused || !result || result.doc_version !== latest.version || result.input_format !== latest.format
      || suggestion.doc_version !== latest.version || !Number.isSafeInteger(start) || !Number.isSafeInteger(end)
      || start < 0 || end < start || end > latest.text.length || latest.text.slice(start, end) !== suggestion.original
      || !suggestion.replacements.some((item) => typeof item.text === 'string' && item.text === replacement)) {
      setResult(null);
      setNotice('That suggestion no longer matches the source. Checking the current version.');
      setEpoch((value) => value + 1);
      return;
    }
    setHistory((previous) => [...previous, latest].slice(-20));
    commit(latest.text.slice(0, start) + replacement + latest.text.slice(end));
    requestAnimationFrame(() => { editor.current?.focus(); editor.current?.setSelectionRange(start, start + replacement.length); });
  }
  function undo() {
    if (isComposing || !history.length) return;
    const previous = history[history.length - 1];
    setHistory((items) => items.slice(0, -1));
    commit(previous.text, previous.format, true);
    requestAnimationFrame(() => editor.current?.focus());
  }
  function exportText() {
    if (!doc.text || inputError(doc.text)) return;
    const url = URL.createObjectURL(new Blob([doc.text], { type: 'text/plain;charset=utf-8' }));
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `nexwrite-draft.${doc.format === 'markdown' ? 'md' : 'txt'}`;
    anchor.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    setNotice('Your source was exported as a local UTF-8 file.');
  }

  const currentResult = result?.doc_version === doc.version && result.input_format === doc.format && !isComposing && !validation && !paused ? result : null;
  const status = validation ? 'Source needs attention' : isComposing ? 'Composing — checks wait for you' : phase === 'paused' ? 'Checks paused' : phase === 'loading' ? 'Loading local engine…' : phase === 'unavailable' ? 'Local engine unavailable' : phase === 'analyzing' ? 'Checking the current source…' : !doc.text ? 'Ready when you are' : currentResult?.suggestions.length ? `${currentResult.suggestions.length} spacing suggestion${currentResult.suggestions.length === 1 ? '' : 's'}` : currentResult ? 'No spacing suggestions' : 'Checking the current source…';

  return <div className="app-shell">
    <a className="skip-link" href="#source">Skip to source editor</a>
    <header className="topbar">
      <a className="wordmark" href="./"><span className="brand-symbol" aria-hidden="true">n<span>↗</span></span>NexWrite<span className="demo-tag">LOCAL DEMO</span></a>
      <div className="local-badge"><span className="status-dot" aria-hidden="true" />On-device analysis</div>
    </header>
    <main>
      <div className="intro">
        <div><p className="eyebrow">A LITTLE SPACE TO THINK</p><h1>Make room for<br className="desktop-break" /> your next idea<span>.</span></h1></div>
        <div className="intro-note"><span className="note-line" aria-hidden="true" /><p>Your words stay here.<br />One local spacing rule.<br />Transparent text estimates.</p></div>
      </div>
      <div className="workspace">
        <section className="editor-panel panel" aria-labelledby="editor-heading">
          <div className="panel-heading"><div><p className="eyebrow">01 / WORKSPACE</p><h2 id="editor-heading">Your draft</h2></div><span className="storage-pill" data-testid="storage-state">{storageState}</span></div>
          <div className="editor-toolbar">
            <label className="format-field">Source format<select aria-label="Source format" value={doc.format} onChange={(event) => { setHistory([]); commit(current.current.text, event.target.value as InputFormat); }} disabled={isComposing}><option value="plain">Plain text</option><option value="markdown">Markdown</option></select></label>
            <button className="text-button" onClick={() => { setHistory([]); commit(EXAMPLE, 'plain'); editor.current?.focus(); }} disabled={!!doc.text || isComposing}>Try an example <span aria-hidden="true">↗</span></button>
          </div>
          <label className="editor-label" htmlFor="source">Source editor</label>
          <textarea ref={editor} id="source" dir="auto" value={doc.text} spellCheck={false} aria-describedby={`source-hint${validation ? ' source-error' : ''}`} aria-invalid={!!validation} placeholder="Start with a thought…" onChange={(event) => edit(event.target.value)} onCompositionStart={() => { composing.current = true; setIsComposing(true); setResult(null); setHistory([]); }} onCompositionEnd={(event) => { composing.current = false; setIsComposing(false); commit(event.currentTarget.value, current.current.format, true); }} />
          <p id="source-hint" className="editor-hint">{doc.format === 'markdown' ? 'Markdown source only. Protected constructs are excluded from prose checks.' : 'Plain-text source. Nothing changes until you accept a suggestion.'} Maximum 1 MiB of UTF-8 text.</p>
          {validation && <p id="source-error" className="error-message">{validation}</p>}
          <div className="editor-footer"><div className="engine-status" role="status" aria-live="polite" data-testid="analysis-status"><span className={`status-dot ${phase === 'unavailable' || validation ? 'error-dot' : ''}`} aria-hidden="true" />{status}</div><div className="editor-actions"><button className="subtle-button" title="Undo the last accepted edit; up to 20 snapshots this visit. Typing clears this history." onClick={undo} disabled={!history.length || isComposing}>Undo accept</button><button className="primary-button" onClick={exportText} disabled={!doc.text || !!validation || isComposing}>Export source <span aria-hidden="true">↓</span></button></div></div>
          {notice && <p className="action-notice" role="status">{notice}</p>}
        </section>
        <aside className="insights-column" aria-label="Writing insights">
          <section className="suggestions-panel panel" aria-labelledby="suggestions-heading">
            <div className="panel-heading"><div><p className="eyebrow">02 / A FRESH LOOK</p><h2 id="suggestions-heading">Suggestions</h2></div><span className="count-badge">{currentResult ? currentResult.suggestions.length : '—'}</span></div>
            <p className="section-note">Correctness · extra ASCII spaces<br /><code>NW-SPACE-001</code></p>
            {phase === 'unavailable' ? <div className="empty-state"><span className="empty-icon" aria-hidden="true">!</span><h3>The engine could not load</h3><p>You can keep writing. Retry loads the real local WASM engine.</p><button className="subtle-button" onClick={() => { setResult(null); setRetry((value) => value + 1); }}>Retry local engine</button></div> : currentResult?.suggestions.length ? <><p className="section-note" data-testid="suggestion-total">Showing {Math.min(20, currentResult.suggestions.length)} of {currentResult.suggestions.length} suggestions.</p><ul className="suggestion-list">{currentResult.suggestions.slice(0, 20).map((suggestion) => <li className="suggestion-card" key={suggestion.id}><span className="category-label">{suggestion.category}</span><h3>{suggestion.explanation.short}</h3><div className="replacement-preview"><del aria-label="Original">{suggestion.original.replaceAll(' ', '␠')}</del><span aria-hidden="true">→</span><ins aria-label="Replacement">{suggestion.replacements[0]?.text.replaceAll(' ', '␠') || 'empty'}</ins></div><p>{suggestion.explanation.long}</p>{suggestion.replacements.map((replacement, index) => <button className="accept-button" key={index} onClick={() => accept(suggestion, replacement.text)} disabled={isComposing || paused}>Accept suggestion <span aria-hidden="true">↵</span></button>)}</li>)}</ul></> : <div className="empty-state"><span className="empty-icon" aria-hidden="true">{currentResult && doc.text ? '✓' : '↗'}</span><h3>{paused ? 'A moment to yourself' : validation ? 'Your source is still editable' : isComposing ? 'Take your time' : phase === 'analyzing' || phase === 'loading' ? 'Looking at your source' : doc.text && currentResult ? 'A little breathing room' : 'Every draft starts somewhere'}</h3><p>{paused ? 'Resume checks when you are ready.' : validation ? 'Resolve the source limit or Unicode issue to continue checks.' : doc.text && currentResult ? 'No extra ASCII spaces found in the analyzed prose.' : 'Write a few words, or try the example, to see the local rule in action.'}</p></div>}
            <button className="pause-button" onClick={() => { setResult(null); setPaused((value) => !value); }} disabled={isComposing}>{paused ? 'Resume checks' : 'Pause checks'}</button>
          </section>
          <section className="metrics-panel panel" aria-labelledby="metrics-heading" data-testid="metrics" data-version={currentResult?.doc_version}>
            <div className="panel-heading"><div><p className="eyebrow">03 / THE SHAPE OF IT</p><h2 id="metrics-heading">At a glance</h2></div></div>
            {currentResult ? <><dl className="primary-metrics"><div><dt>Words</dt><dd data-metric="word_count" data-value={currentResult.metrics.word_count}>{display(currentResult.metrics.word_count)}</dd></div><div><dt>Sentences</dt><dd data-metric="sentence_count" data-value={currentResult.metrics.sentence_count}>{display(currentResult.metrics.sentence_count)}</dd></div></dl><dl className="metric-details">{([
              ['paragraph_count', 'Source paragraphs'], ['source_grapheme_count', 'Source graphemes'], ['source_utf8_bytes', 'Source UTF-8 bytes'], ['reading_seconds', 'Reading · seconds'], ['speaking_seconds', 'Speaking · seconds'],
            ] as const).map(([key, label]) => <div key={key}><dt>{label}</dt><dd data-metric={key} data-value={currentResult.metrics[key]}>{display(currentResult.metrics[key])}</dd></div>)}</dl><h3 className="metric-subheading">Available estimates</h3>{currentResult.metrics.scores.length ? <dl className="metric-details">{currentResult.metrics.scores.map((score) => <div key={score.kind}><dt>{scoreLabels[score.kind]}</dt><dd data-score={score.kind} data-value={score.value}>{display(score.value)}</dd></div>)}</dl> : <p className="section-note">No formula score is available for this source.</p>}<details className="metric-disclosures" open><summary>Limits &amp; omitted estimates</summary><ul>{currentResult.metrics.warnings.map((warning) => <li key={warning} data-warning={warning}>{warningLabels[warning] ?? warning}</li>)}</ul></details></> : <p className="metrics-placeholder">Current-source counts and estimates appear after a local check.</p>}
          </section>
        </aside>
      </div>
      <section className="settings-panel" aria-labelledby="settings-heading"><div className="settings-intro"><p className="eyebrow">YOUR SPACE, YOUR SETTINGS</p><h2 id="settings-heading">Kept close.</h2><p>No accounts. No document uploads.<br />Analysis happens in a local Worker.</p></div><div className="settings-controls"><label className="theme-field">Appearance<select aria-label="Appearance" value={theme} onChange={(event) => changeTheme(event.target.value as Theme)}><option value="dark">Obsidian</option><option value="light">Paper</option></select></label><div className="persistence-setting"><label className="checkbox-label"><input type="checkbox" checked={persist} onChange={(event) => togglePersistence(event.target.checked)} aria-describedby="persistence-hint" />Save draft in this browser</label><p id="persistence-hint">Off by default. This browser profile stores the source; it is not an encrypted vault. Others using the profile may see it.</p></div><button className="text-button clear-button" onClick={clearLocalData}>Clear local NexWrite data</button></div>{storageError && <p className="error-message storage-error" role="status">{storageError}</p>}</section>
      <footer className="page-footer"><p>A small, honest beginning.</p><div className="license-links" aria-label="Runtime licenses"><a href="/notices/inter-OFL.txt">Inter</a><a href="/notices/space-grotesk-OFL.txt">Space Grotesk</a><a href="/notices/jetbrains-mono-OFL.txt">JetBrains Mono</a><a href="/notices/react-MIT.txt">React</a><a href="/notices/react-dom-MIT.txt">React DOM</a><a href="/notices/scheduler-MIT.txt">Scheduler</a><a href="/notices/engine-third-party.txt">Engine notices</a></div><p>M1 local demo · one spacing rule &amp; bounded source metrics.<br />English readability is an estimate. Full grammar and platform integrations are future work.</p></footer>
    </main>
  </div>;
}
