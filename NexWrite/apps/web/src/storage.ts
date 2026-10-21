// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import type { InputFormat } from '@nexwrite/protocol';
import { inputError, MAX_BYTES } from './input';

export const KEYS = { draft: 'nexwrite.m1.draft', consent: 'nexwrite.m1.draft-consent', theme: 'nexwrite.m1.theme' };
export type Theme = 'dark' | 'light';
export type Draft = { version: 1; source: string; format: InputFormat };
export function initialSettings(): { source: string; format: InputFormat; persist: boolean; theme: Theme; warning: string | null } {
  const initial = { source: '', format: 'plain' as InputFormat, persist: false, theme: 'dark' as Theme, warning: null as string | null };
  try {
    if (localStorage.getItem(KEYS.theme) === 'light') initial.theme = 'light';
    if (localStorage.getItem(KEYS.consent) !== '1') return initial;
    const raw = localStorage.getItem(KEYS.draft);
    if (raw === null) return { ...initial, persist: true };
    if (raw.length > MAX_BYTES * 6 + 100) throw new Error('Oversized record');
    const parsed: unknown = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object') throw new Error('Invalid record');
    const draft = parsed as Partial<Draft>;
    if (Object.keys(draft).sort().join(',') !== 'format,source,version' || draft.version !== 1
      || typeof draft.source !== 'string' || (draft.format !== 'plain' && draft.format !== 'markdown') || inputError(draft.source)) {
      throw new Error('Invalid draft');
    }
    // A native textarea adopts LF even when a valid older draft contains CRLF/CR.
    // Adopt that exact DOM representation before analysis, export, and Undo.
    return { ...initial, source: draft.source.replace(/\r\n?/g, '\n'), format: draft.format, persist: true };
  } catch {
    return { ...initial, warning: 'A saved setting or draft could not be read safely. Your editor remains available in memory.' };
  }
}
