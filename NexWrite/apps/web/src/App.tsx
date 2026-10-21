// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { useEffect, useRef, useState } from 'react';
import type { AnalysisResult, InputFormat, MetricKind, Suggestion } from '@nexwrite/protocol';
import { inputError } from './input';
import { initialSettings, KEYS, type Theme } from './storage';
import { clearDocuments, deleteDocument, listDocuments, putDocument, titleError, type SavedDocument } from './library';
import { Assist, BackendSettings, type AiSnapshot, type Connection } from './Assist';
import { CONNECTION_KEY, savedAddress, type AiResult } from './api';

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
  const [destination, setDestination] = useState<'library' | 'write' | 'assist' | 'settings'>('write');
  const [identity, setIdentity] = useState({ id: null as string | null, title: '', createdAt: '' });
  const identityRef = useRef(identity);
  identityRef.current = identity;
  const [saved, setSaved] = useState<{ id: string; title: string; text: string; format: InputFormat } | null>(null);
  const [documents, setDocuments] = useState<SavedDocument[]>([]);
  const [libraryState, setLibraryState] = useState('Loading your local library…');
  const [libraryBusy, setLibraryBusy] = useState(false);
  const [query, setQuery] = useState('');
  const [selection, setSelection] = useState<[number, number]>([0, 0]);
  const [connection, setConnection] = useState<Connection>({ address: savedAddress(), token: '', profile: null, revision: 0 });
  const [online, setOnline] = useState(navigator.onLine);
  const [aiCredit, setAiCredit] = useState('');
  const importer = useRef<HTMLInputElement>(null);
  const dirty = !saved || saved.id !== identity.id || saved.title !== identity.title || saved.text !== doc.text || saved.format !== doc.format;
  const validation = inputError(doc.text);

  useEffect(() => {
    let active = true;
    listDocuments().then((items) => { if (active) { setDocuments(items); setLibraryState('Saved only in this browser profile.'); } })
      .catch((error: Error) => { if (active) setLibraryState(error.message); });
    return () => { active = false; };
  }, []);
  useEffect(() => {
    const update = () => setOnline(navigator.onLine);
    window.addEventListener('online', update); window.addEventListener('offline', update);
    return () => { window.removeEventListener('online', update); window.removeEventListener('offline', update); };
  }, []);
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => { if (dirty && (doc.text || identity.title)) event.preventDefault(); };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty, doc.text, identity.title]);

  async function refreshLibrary() {
    const items = await listDocuments(); setDocuments(items); setLibraryState('Saved only in this browser profile.');
  }
  async function saveDocument() {
    if (libraryBusy || validation || isComposing || titleError(identity.title)) return;
    const snapshot = current.current;
    const name = identityRef.current;
    const now = new Date().toISOString();
    const item: SavedDocument = { id: name.id ?? crypto.randomUUID(), title: name.title.trim() || 'Untitled writing', source: snapshot.text, input_format: snapshot.format,
      locale: 'en-US', doc_version: snapshot.version, created_at: name.createdAt || now, updated_at: now, schema_version: 1 };
    setLibraryBusy(true);
    try {
      await putDocument(item);
      if (identityRef.current === name) {
        const next = { id: item.id, title: item.title, createdAt: item.created_at }; identityRef.current = next; setIdentity(next);
        setSaved({ id: item.id, title: item.title, text: snapshot.text, format: snapshot.format });
      }
      setNotice('Document saved in this browser.'); await refreshLibrary();
    } catch (error) { setNotice(error instanceof Error ? error.message : 'The document could not be saved. Your draft remains in memory.'); }
    finally { setLibraryBusy(false); }
  }
  function canLeave() { return !dirty || !(current.current.text || identityRef.current.title) || window.confirm('Leave this unsaved draft? Save it first to keep these changes.'); }
  function newDocument() {
    if (isComposing || !canLeave()) return;
    const next = { id: null, title: '', createdAt: '' }; identityRef.current = next; setIdentity(next); setSaved(null); setHistory([]); setAiCredit(''); setSelection([0, 0]);
    if (current.current.version >= Number.MAX_SAFE_INTEGER) {
      const blank: Document = { text: '', format: 'plain', version: 0 }; current.current = blank; setDoc(blank); setResult(null); setRetry((value) => value + 1);
    } else commit('', 'plain', true);
    setDestination('write'); requestAnimationFrame(() => editor.current?.focus());
  }
  function openDocument(item: SavedDocument) {
    if (isComposing || !canLeave()) return;
    if (Math.max(current.current.version, item.doc_version) >= Number.MAX_SAFE_INTEGER) { setLibraryState('This document’s version limit was reached. Duplicate it to continue safely.'); return; }
    const next = { id: item.id, title: item.title, createdAt: item.created_at }; identityRef.current = next; setIdentity(next);
    setSaved({ id: item.id, title: item.title, text: item.source, format: item.input_format }); setHistory([]); setAiCredit(''); setSelection([0, 0]);
    const value = { text: item.source, format: item.input_format, version: Math.max(current.current.version, item.doc_version) + 1 }; current.current = value; setDoc(value); setResult(null); setNotice(''); setDestination('write');
  }
  async function changeSaved(item: SavedDocument, action: 'rename' | 'duplicate' | 'delete') {
    if (libraryBusy) return;
    const title = action === 'rename' ? window.prompt('Document title', item.title) : action === 'duplicate' ? `${Array.from(item.title).slice(0, 100).join('')} copy` : item.title;
    if (title === null || titleError(title)) { if (title !== null) setLibraryState('Use a title of at most 120 Unicode characters.'); return; }
    if (action === 'rename' && item.doc_version >= Number.MAX_SAFE_INTEGER) { setLibraryState('This document’s version limit was reached. Duplicate it to continue safely.'); return; }
    if (action === 'delete' && !window.confirm(`Delete “${item.title}” from this browser? This cannot be undone.`)) return;
    setLibraryBusy(true);
    try {
      if (action === 'delete') { await deleteDocument(item.id); if (saved?.id === item.id) setSaved(null); }
      else {
        const copy = { ...item, id: action === 'duplicate' ? crypto.randomUUID() : item.id, title: title.trim() || 'Untitled writing', doc_version: action === 'duplicate' ? 0 : item.doc_version + 1, updated_at: new Date().toISOString(), ...(action === 'duplicate' ? { created_at: new Date().toISOString() } : {}) };
        await putDocument(copy);
        if (action === 'rename' && identityRef.current.id === item.id && identityRef.current.title === item.title) { const next = { ...identityRef.current, title: copy.title }; identityRef.current = next; setIdentity(next); setSaved((previous) => previous ? { ...previous, title: copy.title } : null); commit(current.current.text, current.current.format, true); }
      }
      await refreshLibrary();
    } catch (error) { setLibraryState(error instanceof Error ? error.message : 'The library change failed.'); }
    finally { setLibraryBusy(false); }
  }
  async function importSource(file: File | undefined) {
    if (!file || isComposing || !canLeave()) return;
    const snapshot = current.current;
    const name = identityRef.current;
    try {
      if (file.size > 1_048_576) throw new Error('Import accepts at most 1 MiB of UTF-8 text.');
      const text = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer()).replace(/\r\n?/g, '\n');
      const invalid = inputError(text); if (invalid) throw new Error(invalid);
      if (composing.current || current.current !== snapshot || identityRef.current !== name) { setNotice('The draft changed while the file was opening. Import again when you are ready.'); return; }
      setHistory([]); setAiCredit(''); commit(text, /\.md$/i.test(file.name) ? 'markdown' : 'plain', true); setDestination('write'); setNotice('Source imported into this draft. Save to keep it in the library.');
    } catch { setNotice('This file could not be imported. Use valid UTF-8 plain text or Markdown, at most 1 MiB.'); }
    finally { if (importer.current) importer.current.value = ''; }
  }
  function applyAi(value: AiResult, snapshot: AiSnapshot) {
    const latest = current.current;
    if (composing.current || identityRef.current.id !== snapshot.id || latest.version !== snapshot.version || latest.text !== snapshot.source
      || value.original !== snapshot.requested || value.doc_version !== snapshot.version || snapshot.start < 0 || snapshot.end > latest.text.length || latest.text.slice(snapshot.start, snapshot.end) !== snapshot.original) return false;
    const next = latest.text.slice(0, snapshot.start) + value.replacement + latest.text.slice(snapshot.end);
    if (inputError(next)) { setNotice('The proposed change exceeds this editor’s source limit. Export it separately or shorten the source.'); return false; }
    setHistory((previous) => [...previous, latest].slice(-20)); commit(next);
    setAiCredit(`AI-assisted edit · ${value.provider} / ${value.model} · request ${value.request_id}`); setDestination('write'); return true;
  }

  function commit(text: string, format = current.current.format, force = false) {
    if (!force && text === current.current.text && format === current.current.format) return;
    if (current.current.version >= Number.MAX_SAFE_INTEGER) { setNotice('This draft’s version limit was reached. Export it and start a new document to continue.'); return; }
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
  async function clearLocalData() {
    if (!window.confirm('Clear saved NexWrite documents and preferences from this browser? Your current draft stays in memory.')) return;
    try { await clearDocuments(); setDocuments([]); setSaved(null); localStorage.removeItem(CONNECTION_KEY); setConnection({ address: '', token: '', profile: null, revision: connection.revision + 1 }); }
    catch { setStorageError('The browser could not clear the document library. Editing remains available.'); return; }
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
    setAiCredit('');
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
    <a className="skip-link" href="#source" onClick={() => { setDestination('write'); requestAnimationFrame(() => editor.current?.focus()); }}>Skip to source editor</a>
    <header className="topbar">
      <a className="wordmark" href="./"><span className="brand-symbol" aria-hidden="true">n<span>↗</span></span>NexWrite<span className="demo-tag">PREVIEW</span></a>
      <div className="local-badge"><span className="status-dot" aria-hidden="true" />{online ? 'On-device analysis' : 'Offline · local tools ready'}</div>
    </header>
    <nav className="app-navigation" aria-label="Main navigation">{([
      ['library', '▤', 'Library'], ['write', '✎', 'Write'], ['assist', '✦', 'Assist'], ['settings', '⚙', 'Settings'],
    ] as const).map(([key, icon, label]) => <button key={key} aria-current={destination === key ? 'page' : undefined} className={destination === key ? 'nav-active' : ''} onClick={() => setDestination(key)}><span aria-hidden="true">{icon}</span>{label}</button>)}<p className="rail-note">A space for your words.<br />Local by default.</p></nav>
    <main>
      <div hidden={destination !== 'library'}>
        <div className="page-heading"><div><p className="eyebrow">YOUR WORDS, WITHIN REACH</p><h1>Your library<span>.</span></h1><p className="muted">A quiet home for what you’re working on.</p></div><button className="primary-button" onClick={newDocument} disabled={isComposing}>New document <span aria-hidden="true">＋</span></button></div>
        <section className="library-tools"><label className="search-field">Search local documents<input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Find a title or a phrase…" /></label><p className="muted" role="status" data-testid="library-status">{libraryState}</p></section>
        <div className="document-grid">{documents.filter((item) => `${item.title} ${item.source}`.toLocaleLowerCase('en-US').includes(query.toLocaleLowerCase('en-US'))).map((item) => <article className="panel document-card" key={item.id}><div className="document-card-top"><span className="document-icon" aria-hidden="true">▤</span><span className="storage-pill">{item.input_format === 'markdown' ? 'Markdown' : 'Plain text'}</span></div><button className="document-open" onClick={() => openDocument(item)}><h2>{item.title}</h2><p>{item.source.slice(0, 150) || 'An open page, ready for your next idea.'}</p></button><p className="document-date">Updated {new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date(item.updated_at))}</p><div className="document-actions"><button className="text-button" disabled={libraryBusy} onClick={() => changeSaved(item, 'rename')}>Rename<span className="sr-only"> {item.title}</span></button><button className="text-button" disabled={libraryBusy} onClick={() => changeSaved(item, 'duplicate')}>Duplicate<span className="sr-only"> {item.title}</span></button><button className="text-button" disabled={libraryBusy} onClick={() => changeSaved(item, 'delete')}>Delete<span className="sr-only"> {item.title}</span></button></div></article>)}</div>
        {!documents.length && <section className="panel library-empty"><span className="hero-symbol" aria-hidden="true">▤</span><h2>Every good idea starts somewhere.</h2><p className="muted">Create a document, or save your current draft from Write.<br />Your documents stay in this browser until you export or delete them.</p><button className="primary-button" onClick={newDocument}>Start writing <span aria-hidden="true">↗</span></button></section>}
        {!!documents.length && !documents.some((item) => `${item.title} ${item.source}`.toLocaleLowerCase('en-US').includes(query.toLocaleLowerCase('en-US'))) && <p className="empty-state">No documents match this search.</p>}
      </div>
      <div hidden={destination !== 'write'}>
      <div className="intro">
        <div><p className="eyebrow">A LITTLE SPACE TO THINK</p><h1>Make room for<br className="desktop-break" /> your next idea<span>.</span></h1></div>
        <div className="intro-note"><span className="note-line" aria-hidden="true" /><p>Your words stay here.<br />One local spacing rule.<br />Transparent text estimates.</p></div>
      </div>
      <div className="workspace">
        <section className="editor-panel panel" aria-labelledby="editor-heading">
          <div className="panel-heading"><div><p className="eyebrow">01 / WORKSPACE</p><h2 id="editor-heading">Your draft</h2></div><span className="storage-pill" data-testid="storage-state">{storageState}</span></div>
          <label className="title-field">Document title<input value={identity.title} maxLength={240} placeholder="Untitled writing" onChange={(event) => { setIdentity((previous) => ({ ...previous, title: event.target.value })); commit(current.current.text, current.current.format, true); }} disabled={isComposing} /></label>
          {titleError(identity.title) && <p className="error-message">{titleError(identity.title)}</p>}
          <div className="document-save-row"><p className="muted" data-testid="document-save-state">{identity.id && !dirty ? 'Saved in this browser' : 'Unsaved changes · in memory'}</p><button className="primary-button" onClick={saveDocument} disabled={libraryBusy || !!validation || !!titleError(identity.title) || isComposing}>{libraryBusy ? 'Saving…' : 'Save document'}</button></div>
          <div className="editor-toolbar">
            <label className="format-field">Source format<select aria-label="Source format" value={doc.format} onChange={(event) => { setHistory([]); commit(current.current.text, event.target.value as InputFormat); }} disabled={isComposing}><option value="plain">Plain text</option><option value="markdown">Markdown</option></select></label>
            <button className="text-button" onClick={() => { setHistory([]); commit(EXAMPLE, 'plain'); editor.current?.focus(); }} disabled={!!doc.text || isComposing}>Try an example <span aria-hidden="true">↗</span></button>
          </div>
          <label className="editor-label" htmlFor="source">Source editor</label>
          <textarea ref={editor} id="source" dir="auto" value={doc.text} spellCheck={false} aria-describedby={`source-hint${validation ? ' source-error' : ''}`} aria-invalid={!!validation} placeholder="Start with a thought…" onSelect={(event) => setSelection([event.currentTarget.selectionStart, event.currentTarget.selectionEnd])} onChange={(event) => edit(event.target.value)} onCompositionStart={() => { composing.current = true; setIsComposing(true); setResult(null); setHistory([]); }} onCompositionEnd={(event) => { composing.current = false; setIsComposing(false); commit(event.currentTarget.value, current.current.format, true); }} />
          <p id="source-hint" className="editor-hint">{doc.format === 'markdown' ? 'Markdown source only. Protected constructs are excluded from prose checks.' : 'Plain-text source. Nothing changes until you accept a suggestion.'} Maximum 1 MiB of UTF-8 text.</p>
          {validation && <p id="source-error" className="error-message">{validation}</p>}
          <div className="editor-footer"><div className="engine-status" role="status" aria-live="polite" data-testid="analysis-status"><span className={`status-dot ${phase === 'unavailable' || validation ? 'error-dot' : ''}`} aria-hidden="true" />{status}</div><div className="editor-actions"><input className="sr-only" ref={importer} type="file" accept=".txt,.md,text/plain,text/markdown" aria-label="Import source file" onChange={(event) => void importSource(event.target.files?.[0])} /><button className="subtle-button" onClick={() => importer.current?.click()} disabled={isComposing}>Import source</button><button className="subtle-button" title="Undo the last accepted edit; up to 20 snapshots this visit. Typing clears this history." onClick={undo} disabled={!history.length || isComposing}>Undo accept</button><button className="primary-button" onClick={exportText} disabled={!doc.text || !!validation || isComposing}>Export source <span aria-hidden="true">↓</span></button></div></div>
          {notice && <p className="action-notice" role="status">{notice}</p>}{aiCredit && <p className="action-notice" data-testid="ai-provenance">{aiCredit}</p>}
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
      </div>
      <div hidden={destination !== 'assist'}><div className="page-heading"><div><p className="eyebrow">ANOTHER WAY TO SAY IT</p><h1>Find your voice<span>.</span></h1></div></div><Assist connection={connection} id={identity.id} version={doc.version} source={doc.text} composing={isComposing} selection={selection} onSettings={() => setDestination('settings')} onApply={applyAi} /></div>
      <div hidden={destination !== 'settings'}><div className="page-heading"><div><p className="eyebrow">MAKE YOURSELF AT HOME</p><h1>Your settings<span>.</span></h1></div></div><BackendSettings connection={connection} onChange={setConnection} />
      <section className="settings-panel" aria-labelledby="settings-heading"><div className="settings-intro"><p className="eyebrow">YOUR SPACE, YOUR SETTINGS</p><h2 id="settings-heading">Kept close.</h2><p>Local analysis works without an account.<br />Cloud AI runs only after your explicit request.</p></div><div className="settings-controls"><label className="theme-field">Appearance<select aria-label="Appearance" value={theme} onChange={(event) => changeTheme(event.target.value as Theme)}><option value="dark">Obsidian</option><option value="light">Paper</option></select></label><div className="persistence-setting"><label className="checkbox-label"><input type="checkbox" checked={persist} onChange={(event) => togglePersistence(event.target.checked)} aria-describedby="persistence-hint" />Save draft in this browser</label><p id="persistence-hint">Off by default. This browser profile stores the source; it is not an encrypted vault. Others using the profile may see it.</p></div><button className="text-button clear-button" onClick={clearLocalData}>Clear local NexWrite data</button></div></section>
      <section className="panel settings-card compatibility-card"><p className="eyebrow">WHERE YOU CAN WRITE</p><h2>Platform availability</h2><dl className="metric-details"><div><dt>Browser app</dt><dd>Available here</dd></div><div><dt>Offline writing</dt><dd>After the first full visit</dd></div><div><dt>Android app + keyboard</dt><dd>Preview release</dd></div><div><dt>Native desktop / iOS</dt><dd>Not released</dd></div><div><dt>Extensions / Office / Google add-ins</dt><dd>Not released</dd></div></dl><p className="muted">This preview uses a source editor and one local spacing rule. Full grammar, rich editing, cloud document sync and other native integrations remain in development.</p></section></div>
      {storageError && <p className="error-message storage-error" role="status">{storageError}</p>}
      <footer className="page-footer"><p>Write something that feels like you.</p><div className="license-links" aria-label="Runtime licenses"><a href="/notices/inter-OFL.txt">Inter</a><a href="/notices/space-grotesk-OFL.txt">Space Grotesk</a><a href="/notices/jetbrains-mono-OFL.txt">JetBrains Mono</a><a href="/notices/react-MIT.txt">React</a><a href="/notices/react-dom-MIT.txt">React DOM</a><a href="/notices/scheduler-MIT.txt">Scheduler</a><a href="/notices/engine-third-party.txt">Engine notices</a></div><p>NexWrite preview · local documents &amp; optional connected AI.<br />English readability is an estimate. Full grammar and platform integrations are future work.</p></footer>
    </main>
  </div>;
}
