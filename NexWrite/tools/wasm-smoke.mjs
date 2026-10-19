// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { readFileSync, mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { gzipSync } from 'node:zlib';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
// wasm-bindgen's nodejs target emits CommonJS inside this ESM workspace.
writeFileSync(new URL('../target/wasm-node/package.json', import.meta.url), '{"type":"commonjs"}\n');
const wasm = require('../target/wasm-node/nw_wasm.js');
const directory = mkdtempSync(join(tmpdir(), 'nw-wasm-'));
const executable = process.platform === 'win32' ? 'nw.exe' : 'nw';
let cases = 0;
let malformedVersions = 0;
let malformedTexts = 0;
try {
  for (const text of ['One  two', '👩‍💻  e\u0301', 'שלום  عالم', 'a\r\n  b\t  c', '\u00a0\u00a0', '', ' '.repeat(2048)]) {
    const file = join(directory, 'input.txt');
    writeFileSync(file, text);
    const native = spawnSync(fileURLToPath(new URL(`../target/debug/${executable}`, import.meta.url)), ['check', file, '--format', 'json', '--doc-version', '7'], { encoding: 'utf8' });
    assert.equal(native.status, 0, native.stderr);
    assert.deepEqual(JSON.parse(wasm.check_json(text, 7, 'en-US')), JSON.parse(native.stdout));
    cases++;
  }
  assert.throws(() => wasm.check_json('One  two', 0, 'fr'));
  for (const version of [-1, 0.5, Infinity, NaN, 9007199254740992, null, true, false, '7', '', [2], {}, undefined]) {
    assert.throws(() => wasm.check_json('One  two', version, 'en-US'));
    malformedVersions++;
  }
  for (const text of ['\ud800', '\udfff', 'a\ud800 b', null, 1, true, {}]) {
    assert.throws(() => wasm.check_json(text, 0, 'en-US'));
    malformedTexts++;
  }
  assert.throws(() => wasm.check_json('One  two', 0, null));
  assert.equal(JSON.parse(wasm.check_json('One  two', Number.MAX_SAFE_INTEGER, 'en-US'))[0].doc_version, Number.MAX_SAFE_INTEGER);
  const gzipBytes = gzipSync(readFileSync(new URL('../target/wasm-node/nw_wasm_bg.wasm', import.meta.url))).length;
  assert.ok(gzipBytes <= 3 * 1024 * 1024, `WASM gzip budget exceeded: ${gzipBytes}`);
  console.log(JSON.stringify({ runtime: 'actual compiled WASM in Node', native_parity_cases: cases, malformed_versions_rejected: malformedVersions, malformed_texts_rejected: malformedTexts, invalid_locale_rejected: true, max_safe_version_preserved: true, gzip_bytes: gzipBytes, gzip_budget_bytes: 3 * 1024 * 1024 }));
} finally {
  rmSync(directory, { recursive: true, force: true });
}
