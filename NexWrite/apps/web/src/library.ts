// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import type { InputFormat } from '@nexwrite/protocol';
import { inputError } from './input';

export const LIBRARY_DB = 'nexwrite.documents.v1';
const STORE = 'documents';
export type SavedDocument = {
  id: string; title: string; source: string; input_format: InputFormat;
  locale: 'en-US'; doc_version: number; created_at: string; updated_at: string; schema_version: 1;
};
export function titleError(title: string): string | null {
  const value = title.trim();
  return value && (Array.from(value).length > 120 || inputError(value)) ? 'Use a title of at most 120 Unicode characters.' : null;
}
function valid(value: unknown): value is SavedDocument {
  if (!value || typeof value !== 'object') return false;
  const item = value as SavedDocument;
  return Object.keys(item).sort().join(',') === 'created_at,doc_version,id,input_format,locale,schema_version,source,title,updated_at'
    && typeof item.id === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(item.id)
    && typeof item.title === 'string' && item.title === item.title.trim() && !!item.title && !titleError(item.title)
    && typeof item.source === 'string' && !inputError(item.source) && !item.source.includes('\r')
    && (item.input_format === 'plain' || item.input_format === 'markdown') && item.locale === 'en-US'
    && Number.isSafeInteger(item.doc_version) && item.doc_version >= 0 && item.schema_version === 1
    && [item.created_at, item.updated_at].every((date) => typeof date === 'string' && Number.isFinite(Date.parse(date)) && new Date(date).toISOString() === date);
}
function open(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    let request: IDBOpenDBRequest;
    try { request = indexedDB.open(LIBRARY_DB, 1); }
    catch { reject(new Error('The document library is unavailable. Your current draft remains in memory.')); return; }
    request.onupgradeneeded = () => request.result.createObjectStore(STORE, { keyPath: 'id' });
    request.onerror = () => reject(new Error('The document library is unavailable. Your current draft remains in memory.'));
    request.onblocked = () => reject(new Error('Close other NexWrite tabs to update this library. Your current draft remains in memory.'));
    request.onsuccess = () => resolve(request.result);
  });
}
async function transaction<T>(mode: IDBTransactionMode, action: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await open();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, mode);
    const request = action(tx.objectStore(STORE));
    let value: T;
    request.onsuccess = () => { value = request.result; };
    tx.oncomplete = () => { db.close(); resolve(value); };
    tx.onerror = tx.onabort = () => { db.close(); reject(new Error('The browser could not complete the library change. Your current draft remains in memory.')); };
  });
}
export async function listDocuments(): Promise<SavedDocument[]> {
  const values: unknown[] = await transaction('readonly', (store) => store.getAll());
  if (!values.every(valid)) throw new Error('The library contains an unreadable document. Editing remains available; no saved documents were changed.');
  return values.sort((a, b) => b.updated_at.localeCompare(a.updated_at));
}
export async function putDocument(item: SavedDocument): Promise<void> {
  if (!valid(item)) throw new Error('This document cannot be saved safely. Check its title and source.');
  await transaction('readwrite', (store) => store.put(item));
}
export async function deleteDocument(id: string): Promise<void> { await transaction('readwrite', (store) => store.delete(id)); }
export async function clearDocuments(): Promise<void> { await transaction('readwrite', (store) => store.clear()); }
