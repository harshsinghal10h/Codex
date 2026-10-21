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
let analysisCases = 0;
let malformedAnalysisInputs = 0;
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
  const sentence = 'The cat sat by the log and saw the dog.';
  for (const [text, format] of [
    ['😀 Hi  café.', 'plain'], ['👩‍💻\tHi\u00a0there\u200b\r\n\r\nשלום  عالم.', 'plain'],
    ['', 'plain'], [' \r\n\t', 'plain'], ['😀 !!!', 'plain'], ['42 3.14', 'plain'], ['שלום عالم', 'plain'],
    [Array(3).fill(sentence).join(' '), 'plain'], [Array(30).fill(sentence).join(' '), 'plain'],
    ['One  two.\n\n```text\ncode  words\n```\n\nMore  words.', 'markdown'],
    ['`code  words` and [link  label](https://example.test) ![image  label](image.png)', 'markdown'],
    ['before <em>inline  html</em> after\n\nText  here.', 'markdown'],
    ['Text &amp; entity and \\*escape.\n\n[id]: /reference\n', 'markdown'],
    ['  leading  words.  \nnext  words.', 'markdown'], ['hel*lo* world.', 'markdown'],
    ['😀 `excluded 👩‍💻` Hi  café.', 'markdown'], ['open ` ambiguous  tail', 'markdown'],
    ['[unclosed  label', 'markdown'], ['<broken  html\nnext  line', 'markdown'],
    ['```\nonly  code', 'markdown'], ['plain words', 'markdown'],
    ['.'.repeat(1048576), 'plain'], ['😀'.repeat(262144), 'plain'],
  ]) {
    const file = join(directory, 'analysis.txt');
    writeFileSync(file, text);
    const native = spawnSync(fileURLToPath(new URL(`../target/debug/${executable}`, import.meta.url)), ['analyze', file, '--format', 'json', '--doc-version', '23', '--input-format', format], { encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
    assert.equal(native.status, 0, native.stderr || native.error?.message);
    const report = JSON.parse(wasm.analyze_json(text, 23, 'en-US', format));
    assert.deepEqual(report, JSON.parse(native.stdout));
    assert.equal(report.doc_version, 23);
    assert.equal(report.metrics.source_utf8_bytes, Buffer.byteLength(text));
    assert.ok(report.metrics.scores.every(score => Number.isFinite(score.value)));
    if (text === Array(3).fill(sentence).join(' ')) {
      const score = report.metrics.scores.find(score => score.kind === 'flesch_reading_ease');
      assert.ok(Math.abs(score.value - 112.085) < 0.000001);
      assert.ok(!report.metrics.scores.some(score => score.kind === 'smog_grade'));
    }
    analysisCases++;
  }
  for (const version of [-1, 0.5, Infinity, NaN, 9007199254740992, null, true, false, '7', '', [2], {}, undefined]) {
    assert.throws(() => wasm.analyze_json('One  two', version, 'en-US', 'plain'));
    malformedAnalysisInputs++;
  }
  for (const text of ['\ud800', '\udfff', 'a\ud800 b', null, 1, true, {}, '.'.repeat(1048577), `${'😀'.repeat(262144)}x`]) {
    assert.throws(() => wasm.analyze_json(text, 0, 'en-US', 'plain'));
    malformedAnalysisInputs++;
  }
  for (const format of ['html', 'latex', 'Markdown', '', null, 1, {}, undefined]) {
    assert.throws(() => wasm.analyze_json('One  two', 0, 'en-US', format));
    malformedAnalysisInputs++;
  }
  assert.throws(() => wasm.analyze_json('One  two', 0, 'fr', 'plain'));
  assert.throws(() => wasm.analyze_json('One  two', 0, null, 'plain'));
  assert.equal(JSON.parse(wasm.analyze_json('', Number.MAX_SAFE_INTEGER, 'en-US', 'plain')).doc_version, Number.MAX_SAFE_INTEGER);
  const gzipBytes = gzipSync(readFileSync(new URL('../target/wasm-node/nw_wasm_bg.wasm', import.meta.url))).length;
  assert.ok(gzipBytes <= 3 * 1024 * 1024, `WASM gzip budget exceeded: ${gzipBytes}`);
  console.log(JSON.stringify({ runtime: 'actual compiled WASM in Node', native_parity_cases: cases, malformed_versions_rejected: malformedVersions, malformed_texts_rejected: malformedTexts, analysis_native_parity_cases: analysisCases, malformed_analysis_inputs_rejected: malformedAnalysisInputs, invalid_locale_rejected: true, max_safe_version_preserved: true, gzip_bytes: gzipBytes, gzip_budget_bytes: 3 * 1024 * 1024 }));
} finally {
  rmSync(directory, { recursive: true, force: true });
}
