// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import type { AiResult, AiPreset, AiTask, Capabilities, RewriteRequest } from '@nexwrite/protocol/api';
import { inputError } from './input';
export type { AiResult, AiPreset, AiTask, Capabilities } from '@nexwrite/protocol/api';
export const CONNECTION_KEY = 'nexwrite.backend.v1';
const bytes = (value: string) => new TextEncoder().encode(value).length;
export const presets: AiPreset[] = ['proofread', 'clearer', 'shorten', 'expand', 'formal', 'friendly', 'direct'];
function object(value: unknown): value is Record<string, unknown> { return !!value && typeof value === 'object' && !Array.isArray(value); }
const exact = (value: Record<string, unknown>, keys: string[]) => Object.keys(value).sort().join(',') === keys.sort().join(',');
const safeString = (value: unknown, max = 1024): value is string => typeof value === 'string' && bytes(value) <= max && !inputError(value);
const scalarString = (value: unknown, max: number, min = 0): value is string => typeof value === 'string' && Array.from(value).length >= min && Array.from(value).length <= max && !inputError(value);
export function backendAddress(value: string): string {
  let url: URL;
  try { url = new URL(value.trim()); } catch { throw new Error('Enter the HTTPS address of your NexWrite backend.'); }
  const loopback = import.meta.env.DEV && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  if ((url.protocol !== 'https:' && !(loopback && url.protocol === 'http:')) || url.username || url.password || url.search || url.hash) {
    throw new Error('Use an HTTPS backend address without credentials, query parameters, or a fragment.');
  }
  return url.href.replace(/\/+$/, '');
}
export function savedAddress(): string {
  try { const value = localStorage.getItem(CONNECTION_KEY); return value ? backendAddress(value) : ''; } catch { return ''; }
}
async function json(base: string, path: string, token: string, signal: AbortSignal, body?: RewriteRequest): Promise<unknown> {
  const response = await fetch(`${backendAddress(base)}${path}`, {
    method: body ? 'POST' : 'GET', signal: AbortSignal.any([signal, AbortSignal.timeout(30_000)]), credentials: 'omit', cache: 'no-store', redirect: 'error', referrerPolicy: 'no-referrer',
    headers: { Accept: 'application/json', ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  if (!response.ok) {
    await response.body?.cancel();
    throw new Error(response.status === 401 ? 'The backend rejected the access token. Check Settings.' : response.status === 503 ? 'The backend has no available provider. Configure a provider on your server.' : response.status === 429 ? 'The backend is busy. Try again later.' : `The backend could not complete this request (HTTP ${response.status}).`);
  }
  if (!response.headers.get('content-type')?.toLowerCase().includes('application/json') || !response.body) throw new Error('The backend returned an unsupported response.');
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let length = 0;
  while (true) {
    const chunk = await reader.read();
    if (chunk.done) break;
    length += chunk.value.byteLength;
    if (length > 1_048_576) { await reader.cancel(); throw new Error('The backend response exceeded the safety limit.'); }
    chunks.push(chunk.value);
  }
  const combined = new Uint8Array(length);
  let offset = 0;
  for (const chunk of chunks) { combined.set(chunk, offset); offset += chunk.length; }
  try { return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(combined)); }
  catch { throw new Error('The backend returned unreadable JSON.'); }
}
export async function capabilities(base: string, token: string, signal: AbortSignal): Promise<Capabilities> {
  const data = await json(base, '/v1/capabilities', token, signal);
  if (!object(data) || !exact(data, ['protocol_version', 'version', 'engine', 'tasks', 'providers', 'accounts', 'cloud_docs', 'auth_required'])
    || data.protocol_version !== 1 || !scalarString(data.version, 64, 1) || data.accounts !== false || data.cloud_docs !== false || typeof data.auth_required !== 'boolean'
    || JSON.stringify(data.tasks) !== '["rewrite","summarize","compose"]'
    || !object(data.engine) || !exact(data.engine, ['available', 'rule_count', 'locales', 'input_formats']) || typeof data.engine.available !== 'boolean'
    || !Number.isSafeInteger(data.engine.rule_count) || (data.engine.rule_count as number) < 0 || (data.engine.rule_count as number) > 4_294_967_295 || JSON.stringify(data.engine.locales) !== '["en-US"]'
    || JSON.stringify(data.engine.input_formats) !== '["plain","markdown"]'
    || !Array.isArray(data.providers) || data.providers.length > 20 || !data.providers.every((provider) => object(provider)
      && exact(provider, ['id', 'label', 'configured', 'models']) && typeof provider.id === 'string' && /^[a-z][a-z0-9_-]{0,63}$/.test(provider.id) && scalarString(provider.label, 80, 1)
      && typeof provider.configured === 'boolean' && Array.isArray(provider.models) && provider.models.length <= 100
      && provider.models.every((model) => typeof model === 'string' && /^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$/.test(model)))) throw new Error('This backend uses an unsupported capability format.');
  return data as unknown as Capabilities;
}
export async function generate(base: string, token: string, request: RewriteRequest, signal: AbortSignal): Promise<AiResult> {
  if (inputError(request.text) || bytes(request.text) > 32_768) throw new Error('AI requests accept at most 32 KiB of valid UTF-8 text. Select a smaller passage.');
  const data = await json(base, '/v1/rewrite', token, signal, request);
  if (!object(data) || !exact(data, ['request_id', 'doc_version', 'provider', 'model', 'task', 'preset', 'original', 'replacement', 'warnings'])
    || !safeString(data.request_id, 36) || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(data.request_id)
    || data.doc_version !== request.doc_version || data.provider !== request.provider || data.model !== request.model || data.task !== request.task || data.preset !== request.preset
    || data.original !== request.text || !safeString(data.replacement, 65_536) || !Array.isArray(data.warnings) || data.warnings.length > 64 || !data.warnings.every((warning) => scalarString(warning, 512))) {
    throw new Error('The AI response does not match this request. Your source has not changed.');
  }
  return data as unknown as AiResult;
}
