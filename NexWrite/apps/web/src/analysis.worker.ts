// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import type { AnalysisResult, InputFormat } from '@nexwrite/protocol';
import { inputError } from './input';

type EngineModule = {
  default: () => Promise<unknown>;
  analyze_json: (text: string, version: number, locale: string, format: string) => string;
};
const scope = self as unknown as {
  postMessage(message: unknown): void;
  onmessage: ((event: MessageEvent<unknown>) => void) | null;
};
let engine: EngineModule;
try {
  const engineUrl = new URL('/engine/nw_wasm.js', self.location.origin).href;
  engine = await import(/* @vite-ignore */ engineUrl) as EngineModule;
  await engine.default();
  if (typeof engine.analyze_json !== 'function') throw new Error('Missing analysis export');
  scope.postMessage({ type: 'ready' });
} catch {
  scope.postMessage({ type: 'unavailable' });
}
scope.onmessage = (event) => {
  const request = event.data;
  if (!request || typeof request !== 'object') return;
  const data = request as Record<string, unknown>;
  const version = data.doc_version;
  const format = data.input_format;
  if (data.type !== 'analyze' || typeof data.text !== 'string' || typeof version !== 'number'
    || !Number.isSafeInteger(version) || version < 0 || data.locale !== 'en-US'
    || (format !== 'plain' && format !== 'markdown') || inputError(data.text)) {
    scope.postMessage({ type: 'invalid', doc_version: version, input_format: format });
    return;
  }
  try {
    if (!engine) throw new Error('Engine unavailable');
    const result = JSON.parse(engine.analyze_json(data.text, version, 'en-US', format)) as AnalysisResult;
    if (result.doc_version !== version || result.input_format !== (format as InputFormat) || result.locale !== 'en-US') {
      throw new Error('Mismatched engine result');
    }
    scope.postMessage({ type: 'result', result });
  } catch {
    // No source text or exception payload enters diagnostics or the UI.
    scope.postMessage({ type: 'failed', doc_version: version, input_format: format });
  }
};
