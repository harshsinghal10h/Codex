// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { useEffect, useRef, useState } from 'react';
import { backendAddress, capabilities, CONNECTION_KEY, generate, presets, type AiPreset, type AiResult, type AiTask, type Capabilities } from './api';
export type Connection = { address: string; token: string; profile: Capabilities | null; revision: number };
export type AiSnapshot = { id: string | null; version: number; source: string; start: number; end: number; original: string; requested: string };
type Props = {
  connection: Connection; id: string | null; version: number; source: string; composing: boolean;
  selection: [number, number]; onSettings: () => void; onApply: (result: AiResult, snapshot: AiSnapshot) => boolean;
};
export function BackendSettings({ connection, onChange }: { connection: Connection; onChange: (value: Connection) => void }) {
  const [address, setAddress] = useState(connection.address);
  const [token, setToken] = useState(connection.token);
  const [message, setMessage] = useState(connection.profile ? 'Connected. Provider availability comes from your backend.' : 'No AI backend connected. Local writing tools work without one.');
  const [busy, setBusy] = useState(false);
  const abort = useRef<AbortController | null>(null);
  useEffect(() => () => abort.current?.abort(), []);
  useEffect(() => { setAddress(connection.address); setToken(connection.token); }, [connection.revision]);
  async function connect() {
    abort.current?.abort();
    const controller = new AbortController();
    abort.current = controller;
    setBusy(true);
    try {
      const normalized = backendAddress(address);
      if (/[\r\n]/.test(token) || token.length > 4096) throw new Error('Enter a valid backend access token.');
      const profile = await capabilities(normalized, token.trim(), controller.signal);
      if (controller.signal.aborted) return;
      let saved = true;
      try { localStorage.setItem(CONNECTION_KEY, normalized); } catch { saved = false; }
      setAddress(normalized);
      onChange({ address: normalized, token: token.trim(), profile, revision: connection.revision + 1 });
      setMessage(`Connected to ${new URL(normalized).host}.${saved ? '' : ' The address could not be saved; this connection works for this visit.'}`);
    } catch (error) {
      if (!controller.signal.aborted) setMessage(error instanceof Error && error.name !== 'TypeError' ? error.message : 'The backend could not be reached. Check its address, access token, and allowed browser origin.');
    } finally { if (abort.current === controller) setBusy(false); }
  }
  function disconnect() {
    abort.current?.abort(); setBusy(false); setToken(''); setAddress('');
    onChange({ address: '', token: '', profile: null, revision: connection.revision + 1 });
    try { localStorage.removeItem(CONNECTION_KEY); setMessage('Disconnected. No backend address or access token is retained.'); }
    catch { setMessage('Disconnected for this visit. This browser could not remove the saved backend address.'); }
  }
  return <section className="panel settings-card" aria-labelledby="backend-heading">
    <p className="eyebrow">OPTIONAL AI CONNECTION</p><h2 id="backend-heading">Bring your own backend</h2>
    <p className="muted">Connect to a NexWrite backend you trust. Provider keys belong on that server. Your source is sent only when you approve an AI request.</p>
    <label className="stacked-field">Backend HTTPS address<input type="url" autoComplete="off" spellCheck={false} placeholder="https://writing.example.com" value={address} onChange={(event) => setAddress(event.target.value)} /></label>
    <label className="stacked-field">Backend access token <span className="muted">Optional · kept in memory for this visit</span><input type="password" autoComplete="off" value={token} onChange={(event) => setToken(event.target.value)} /></label>
    <div className="button-row"><button className="primary-button" onClick={connect} disabled={busy || !address.trim()}>{busy ? 'Connecting…' : 'Connect backend'}</button><button className="subtle-button" onClick={disconnect}>Disconnect backend</button></div>
    <p className="action-notice" role="status" data-testid="connection-status">{message}</p>
    {connection.profile && <ul className="profile-list">{connection.profile.providers.map((provider) => <li key={provider.id}><span>{provider.label}</span><span className="storage-pill">{provider.configured ? `${provider.models.length} model${provider.models.length === 1 ? '' : 's'} available` : 'Not configured on server'}</span></li>)}</ul>}
  </section>;
}
export function Assist({ connection, id, version, source, composing, selection, onSettings, onApply }: Props) {
  const [task, setTask] = useState<AiTask>('rewrite');
  const [preset, setPreset] = useState<AiPreset>('clearer');
  const available = connection.profile?.providers.filter((provider) => provider.configured && provider.models.length) ?? [];
  const [provider, setProvider] = useState(available[0]?.id ?? '');
  const selectedProvider = available.find((item) => item.id === provider) ?? available[0];
  const [model, setModel] = useState(selectedProvider?.models[0] ?? '');
  const selectedModel = selectedProvider?.models.includes(model) ? model : selectedProvider?.models[0] ?? '';
  const [scope, setScope] = useState<'document' | 'selection'>('document');
  const [prompt, setPrompt] = useState('');
  const [consent, setConsent] = useState(false);
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState('');
  const [proposal, setProposal] = useState<{ result: AiResult; snapshot: AiSnapshot } | null>(null);
  const abort = useRef<AbortController | null>(null);
  const range: [number, number] = task !== 'compose' && scope === 'selection' ? selection : [0, source.length];
  const text = task === 'compose' ? prompt : source.slice(...range);
  const requestBytes = new TextEncoder().encode(text).length;
  const allowed = connection.profile?.tasks.includes(task) && !!selectedProvider && !!selectedModel;
  const stale = proposal && (proposal.snapshot.id !== id || proposal.snapshot.version !== version || proposal.snapshot.source !== source);
  useEffect(() => { setConsent(false); }, [version, id, task, preset, scope, selection[0], selection[1], prompt, selectedProvider?.id, selectedModel, connection.revision]);
  useEffect(() => { setProposal(null); setConsent(false); setPending(false); return () => abort.current?.abort(); }, [connection.revision]);
  async function run() {
    if (!consent || !allowed || composing || !text.trim() || requestBytes > 32_768 || pending) return;
    const controller = new AbortController(); abort.current = controller;
    const snapshot: AiSnapshot = { id, version, source, start: range[0], end: range[1], original: source.slice(...range), requested: text };
    setConsent(false); setPending(true); setProposal(null); setMessage('Waiting for your backend. Your source has not changed.');
    try {
      const result = await generate(connection.address, connection.token, {
        text, doc_version: version, locale: 'en-US', task, preset: task === 'rewrite' ? preset : 'clearer',
        provider: selectedProvider!.id, model: selectedModel, cloud_consent: true,
      }, controller.signal);
      if (!controller.signal.aborted) { setProposal({ result, snapshot }); setMessage('Proposal ready. Review it before applying.'); }
    } catch (error) {
      if (!controller.signal.aborted) setMessage(error instanceof Error && error.name !== 'TypeError' ? error.message : 'The AI request could not reach your backend. Your source has not changed.');
    } finally { if (abort.current === controller) setPending(false); }
  }
  function cancel() { abort.current?.abort(); setPending(false); setConsent(false); setMessage('AI request cancelled. Your source has not changed.'); }
  if (!connection.profile) return <section className="panel setup-state"><span className="hero-symbol" aria-hidden="true">✦</span><p className="eyebrow">AI, ON YOUR TERMS</p><h2>A little help, when you choose.</h2><p>Your AI connection is not configured. Keep writing and checking locally, or connect your own NexWrite backend in Settings.</p><button className="primary-button" onClick={onSettings}>Set up AI connection <span aria-hidden="true">↗</span></button><div className="feature-chips"><span>Rewrite</span><span>Summarize</span><span>Compose</span></div></section>;
  return <div className="assist-layout"><section className="panel settings-card" aria-labelledby="assist-heading"><p className="eyebrow">A SECOND PERSPECTIVE</p><h2 id="assist-heading">Make your words work.</h2><p className="muted">Every request is optional. Review the destination and source before sending.</p>
    <div className="field-grid"><label className="stacked-field">Task<select value={task} onChange={(event) => setTask(event.target.value as AiTask)} disabled={pending}><option value="rewrite">Rewrite</option><option value="summarize">Summarize</option><option value="compose">Compose from a brief</option></select></label><label className="stacked-field">Writing direction<select value={task === 'rewrite' ? preset : 'clearer'} disabled={task !== 'rewrite' || pending} onChange={(event) => setPreset(event.target.value as AiPreset)}>{presets.map((value) => <option key={value} value={value}>{value[0].toUpperCase() + value.slice(1)}</option>)}</select></label>
    <label className="stacked-field">Provider<select value={selectedProvider?.id ?? ''} onChange={(event) => setProvider(event.target.value)} disabled={pending || !available.length}>{!available.length && <option value="">No provider configured</option>}{available.map((item) => <option value={item.id} key={item.id}>{item.label}</option>)}</select></label><label className="stacked-field">Model<select value={selectedModel} onChange={(event) => setModel(event.target.value)} disabled={pending || !selectedProvider}>{selectedProvider?.models.map((value) => <option key={value}>{value}</option>)}</select></label></div>
    {!available.length && <p className="error-message">Your backend has no configured AI provider. Add a provider on the server, then reconnect in Settings.</p>}
    {task === 'compose' && <p className="section-note">Compose proposes a replacement for this document. Review the result before applying it.</p>}{task === 'compose' ? <label className="stacked-field">Writing brief<textarea className="brief-input" value={prompt} onChange={(event) => setPrompt(event.target.value)} placeholder="What would you like to write?" disabled={pending} /></label> : <label className="stacked-field">Source to send<select value={scope} onChange={(event) => setScope(event.target.value as 'document' | 'selection')} disabled={pending}><option value="document">Whole document</option><option value="selection" disabled={selection[0] === selection[1]}>Selected passage</option></select></label>}
    <div className="consent-card"><p className="eyebrow">THIS REQUEST GOES TO</p><p className="destination">{connection.address}</p><p className="muted">{selectedProvider?.label ?? 'No provider'} · {selectedModel || 'No model'} · {requestBytes.toLocaleString()} / 32,768 bytes</p><details open><summary>{task === 'compose' ? 'Brief that will be sent' : 'Source that will be sent'}</summary><pre className="source-preview" data-testid="ai-source-preview">{text || 'No source selected.'}</pre></details><label className="checkbox-label"><input type="checkbox" checked={consent} onChange={(event) => setConsent(event.target.checked)} disabled={pending || !allowed || !text.trim() || requestBytes > 32_768} />I agree to send this text to this backend and provider for this request.</label></div>
    <div className="button-row"><button className="primary-button" onClick={run} disabled={!consent || !allowed || pending || composing || !text.trim() || requestBytes > 32_768}>{pending ? 'Request in progress…' : 'Send AI request'}</button>{pending && <button className="subtle-button" onClick={cancel}>Cancel AI request</button>}</div>
    <p className="action-notice" role="status" data-testid="ai-status">{message}</p>
  </section><section className="panel settings-card proposal-panel" aria-labelledby="proposal-heading"><p className="eyebrow">YOU HAVE THE FINAL WORD</p><h2 id="proposal-heading">Review the proposal</h2>{proposal ? <><p className="muted">{proposal.result.task} · {proposal.result.preset} · {proposal.result.model}</p><pre className="proposal-text" data-testid="ai-proposal">{proposal.result.replacement}</pre>{proposal.result.warnings.map((warning, index) => <p className="muted" key={index}>{warning}</p>)}{stale && <p className="error-message">Your source changed. This proposal cannot be applied; send a new request for the current version.</p>}<button className="primary-button" disabled={!!stale || composing} onClick={() => { if (onApply(proposal.result, proposal.snapshot)) { setProposal(null); setMessage('Proposal applied. You can undo it in Write.'); } else setMessage('The document changed. Nothing was applied.'); }}>Apply AI proposal</button></> : <div className="empty-state"><span className="empty-icon" aria-hidden="true">↗</span><h3>Room for another idea</h3><p>An actual response from your connected backend will appear here. Nothing is applied automatically.</p></div>}</section></div>;
}
