// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { isMain, localPath, read, report, root } from './lint-common.mjs';

export const columns = 'id,title,surfaces,evidence_class,evidence_ref,spec_path,owner_module,required_cases,verifier_cmd,status,ci_evidence,unknown_ids,notes'.split(',');
export function csv(text) {
  const rows = []; let row = []; let cell = ''; let quoted = false; let closed = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') { cell += '"'; i++; }
      else if (c === '"') { quoted = false; closed = true; }
      else cell += c;
    } else if (c === '"' && !cell && !closed) quoted = true;
    else if (c === ',' || c === '\n') {
      row.push(cell); cell = ''; closed = false;
      if (c === '\n') { rows.push(row); row = []; }
    } else if (c === '\r' && text[i + 1] === '\n') continue;
    else if (closed || c === '"') throw new Error('malformed CSV quoting');
    else cell += c;
  }
  if (quoted) throw new Error('unterminated CSV quote');
  if (cell || row.length || closed) rows.push([...row, cell]);
  return rows;
}
export function expectedIds(prompt) {
  const s7 = prompt.split('## S7 FEATURE LEDGER SEED')[1]?.split('## S8')[0];
  if (!s7) throw new Error('MASTER_PROMPT has no S7 seed');
  const ids = [...s7.matchAll(/\b(?:COR|CLA|ENG|DEL|GOL|INS|GEN|AGT|DOC|ACC|TEAM|EDU|SYS)-\d{2}\b/g)].map(m => m[0]);
  const platforms = s7.match(/PLT rows: ([^\r\n]+)/)?.[1];
  if (!platforms) throw new Error('MASTER_PROMPT has no platform rows');
  return [...ids, ...platforms.replace(/\.$/, '').split(',').map(p => `PLT-${p.trim()}`)];
}
export function lintLedger(base) {
  const errors = []; const fail = (id, message) => errors.push(`${id}: ${message}`);
  const rows = csv(read(base, 'docs/ledger/FEATURES.csv'));
  if (JSON.stringify(rows.shift()) !== JSON.stringify(columns)) return ['FEATURES.csv: header does not match required schema'];
  const expected = expectedIds(read(base, 'docs/MASTER_PROMPT.md'));
  const unknowns = new Set([...read(base, 'docs/ledger/UNKNOWNS.md').matchAll(/^(?:#{1,6}\s+|\|\s*)(UNK-[A-Z0-9-]+)\b/gm)].map(m => m[1]));
  const seen = new Set();
  for (const cells of rows) {
    if (cells.length !== columns.length) { fail('CSV', 'wrong number of columns'); continue; }
    const r = Object.fromEntries(columns.map((key, i) => [key, cells[i]]));
    if (seen.has(r.id)) fail(r.id, 'duplicate ID');
    seen.add(r.id);
    if (!expected.includes(r.id)) fail(r.id, 'ID absent from S7');
    for (const field of ['title', 'surfaces', 'owner_module', 'required_cases']) if (!r[field]) fail(r.id, `${field} is empty`);
    if (r.status !== 'planned' && !r.verifier_cmd) fail(r.id, 'non-planned row needs verifier_cmd');
    if (!r.surfaces.split('|').every(s => /^(?:all|W|X|D|A|I|O|G|L|S)$/.test(s))) fail(r.id, 'invalid surface');
    if (!/^E[1-5]$/.test(r.evidence_class)) fail(r.id, 'invalid evidence_class');
    const evidence = r.evidence_ref;
    let publicUrl = false;
    try {
      const u = new URL(evidence);
      publicUrl = ['https:', 'http:'].includes(u.protocol) && !u.username && !u.password
        && !/^(?:localhost|127\.|0\.|10\.|192\.168\.|169\.254\.|\[|.*\.local$)/i.test(u.hostname)
        && !/\/(?:private|authenticated|decompile|unpack|trace)(?:\/|$)/i.test(u.pathname);
    } catch { /* An operator input may be a local document. */ }
    if (!(publicUrl || (evidence.startsWith('docs/inputs/') && localPath(base, evidence)))) fail(r.id, 'evidence_ref must be a public URL or existing docs/inputs path');
    if (!['planned', 'spec', 'in-progress', 'verified', 'blocked', 'out-of-scope'].includes(r.status)) fail(r.id, 'invalid status');
    if (r.status !== 'planned' && !(r.spec_path.startsWith('docs/spec/') && localPath(base, r.spec_path))) fail(r.id, 'non-planned row needs an existing docs/spec path');
    const cases = r.required_cases.split('|');
    if (!cases.every(c => ['positive', 'negative', 'malformed', 'a11y', 'perf', 'privacy', 'i18n'].includes(c)) || new Set(cases).size !== cases.length) fail(r.id, 'invalid or duplicate required_cases');
    for (const unknown of r.unknown_ids.split(';').filter(Boolean)) if (!unknowns.has(unknown)) fail(r.id, `unknown ID does not resolve: ${unknown}`);
    if (r.status === 'verified') {
      if (r.unknown_ids) fail(r.id, 'verified row retains open unknowns');
      if (!r.ci_evidence) fail(r.id, 'verified row needs ci_evidence');
      const passed = new Set();
      for (const file of r.ci_evidence.split(';').filter(Boolean)) {
        if (!(file.startsWith('docs/ledger/evidence/') && localPath(base, file) && file.endsWith('.json'))) { fail(r.id, 'invalid CI evidence path'); continue; }
        try {
          const record = JSON.parse(read(base, file));
          if (record.kind !== 'ci' || !/^https:\/\/github\.com\/[^/]+\/[^/]+\/actions\/runs\/\d+(?:\/attempts\/\d+)?$/.test(record.run_url ?? '')) fail(r.id, 'evidence needs CI kind and GitHub Actions run URL');
          if (record.feature_id !== r.id) fail(r.id, 'evidence feature_id differs from row');
          for (const [key, value] of Object.entries(record.cases ?? {})) {
            if (value !== 'pass') fail(r.id, `CI evidence case ${key} is ${String(value)}, not pass`);
            else passed.add(key);
          }
        } catch { fail(r.id, 'CI evidence is not valid JSON'); }
      }
      for (const name of cases) if (!passed.has(name)) fail(r.id, `CI evidence missing passing case ${name}`);
    }
  }
  for (const id of expected) if (!seen.has(id)) fail(id, 'S7 row is missing');
  return errors;
}
if (isMain(import.meta.url)) {
  try { report('ledger-lint', lintLedger(root)); } catch (error) { report('ledger-lint', [error.message]); }
}
