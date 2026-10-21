// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { afterEach, test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { columns, csv, expectedIds, lintLedger } from '../ledger-lint.mjs';
import { lintProvenance } from '../provenance-lint.mjs';
import { acceptable, productionNpmNames, reviewedNpmLicense, rustLicense } from '../license-check.mjs';
import { read, root } from '../lint-common.mjs';
import { initialize } from '../../infra/compose/init-env.mjs';

const temporary = [];
afterEach(() => { for (const base of temporary.splice(0)) fs.rmSync(base, { recursive: true, force: true }); });
function fixture() {
  const base = fs.mkdtempSync(path.join(os.tmpdir(), 'nw-gates-')); temporary.push(base);
  const write = (file, content) => { fs.mkdirSync(path.dirname(path.join(base, file)), { recursive: true }); fs.writeFileSync(path.join(base, file), content); };
  write('docs/MASTER_PROMPT.md', read(root, 'docs/MASTER_PROMPT.md'));
  write('docs/inputs/operator.md', 'Operator feature request.');
  write('docs/spec/core.md', 'Independent implementation specification.');
  write('docs/ledger/UNKNOWNS.md', '### UNK-TEST-01\nUnresolved runtime validation.');
  write('docs/legal/ASSETS.md', '| Path | Author | License | Source |\n');
  write('docs/legal/THIRD_PARTY.md', '| Path | Author | License | Source |\n');
  const rows = expectedIds(read(root, 'docs/MASTER_PROMPT.md')).map(id => Object.fromEntries(columns.map(key => [key, ({
    id, title: 'Original feature', surfaces: 'all', evidence_class: 'E4', evidence_ref: 'docs/inputs/operator.md',
    owner_module: 'core', required_cases: 'positive|negative|malformed', verifier_cmd: 'cargo test --workspace --locked', status: 'planned',
  })[key] ?? ''])));
  const save = () => write('docs/ledger/FEATURES.csv', [columns.join(','), ...rows.map(r => columns.map(key => `"${r[key].replaceAll('"', '""')}"`).join(','))].join('\n') + '\n');
  save(); return { base, rows, save, write };
}
function ci(f, overrides = {}) {
  const row = f.rows[0]; row.status = 'verified'; row.spec_path = 'docs/spec/core.md'; row.ci_evidence = 'docs/ledger/evidence/ci.json';
  f.write(row.ci_evidence, JSON.stringify({ kind: 'ci', feature_id: row.id, run_url: 'https://github.com/example/nexwrite/actions/runs/123', cases: { positive: 'pass', negative: 'pass', malformed: 'pass' }, ...overrides }));
  f.save();
}
test('CSV handles quoted commas, escaped quotes, CRLF and multiline text', () => {
  assert.deepEqual(csv('a,"b,b","c""d"\r\n"line\n2",x,y'), [['a', 'b,b', 'c"d'], ['line\n2', 'x', 'y']]);
  assert.throws(() => csv('a,"unterminated'), /unterminated/);
  assert.throws(() => csv('a,"closed"garbage'), /malformed/);
});
test('all 149 S7 rows remain accounted for', () => {
  const f = fixture(); assert.equal(f.rows.length, 149); assert.deepEqual(lintLedger(f.base), []);
  f.rows.pop(); f.save(); assert.match(lintLedger(f.base).join('\n'), /S7 row is missing/);
});
test('duplicate and invented IDs are rejected', () => {
  const f = fixture(); f.rows[1].id = f.rows[0].id; f.rows[2].id = 'FAKE-99'; f.save();
  assert.match(lintLedger(f.base).join('\n'), /duplicate ID/); assert.match(lintLedger(f.base).join('\n'), /ID absent from S7/);
});
test('non-planned status needs a real specification; unknowns must resolve', () => {
  const f = fixture(); Object.assign(f.rows[0], { status: 'in-progress', unknown_ids: 'UNK-NO-99' }); f.save();
  const errors = lintLedger(f.base).join('\n'); assert.match(errors, /existing docs\/spec/); assert.match(errors, /does not resolve/);
});
test('private URLs and escaping local evidence are rejected', () => {
  for (const evidence of ['https://localhost/private', 'https://127.0.0.1/a', 'https://user:password@example.com/a', 'docs/inputs/../../../secret']) {
    const f = fixture(); f.rows[0].evidence_ref = evidence; f.save(); assert.match(lintLedger(f.base).join('\n'), /evidence_ref/);
  }
});
test('invalid evidence classes and cases are rejected', () => {
  const f = fixture(); f.rows[0].evidence_class = 'E9'; f.rows[0].required_cases = 'positive|positive|anything'; f.save();
  const errors = lintLedger(f.base).join('\n'); assert.match(errors, /evidence_class/); assert.match(errors, /required_cases/);
});
test('verified requires CI provenance and every passing required case', () => {
  const f = fixture(); ci(f); assert.deepEqual(lintLedger(f.base), []);
  ci(f, { cases: { positive: 'pass', negative: 'pass' } }); assert.match(lintLedger(f.base).join('\n'), /missing passing case malformed/);
});
test('local evidence cannot be relabeled verified', () => {
  const f = fixture(); ci(f, { kind: 'local', run_url: '' }); assert.match(lintLedger(f.base).join('\n'), /needs CI kind/);
  ci(f, { feature_id: 'OTHER-01' }); assert.match(lintLedger(f.base).join('\n'), /feature_id differs/);
});
test('fail, unknown, skipped and truncated never aggregate to pass', () => {
  for (const state of ['fail', 'unknown', 'skipped', 'truncated']) {
    const f = fixture(); ci(f, { cases: { positive: 'pass', negative: state, malformed: 'pass' } });
    assert.match(lintLedger(f.base).join('\n'), /not pass/);
  }
});
test('verified cannot retain unresolved obligations or missing evidence', () => {
  const f = fixture(); ci(f); f.rows[0].unknown_ids = 'UNK-TEST-01'; f.rows[0].ci_evidence = ''; f.save();
  const errors = lintLedger(f.base).join('\n'); assert.match(errors, /retains open unknowns/); assert.match(errors, /needs ci_evidence/);
});
test('source requires an SPDX header and original identifiers', () => {
  const f = fixture(); f.write('apps/web/main.ts', 'export const title = "NexWrite";');
  assert.match(lintProvenance(f.base).join('\n'), /missing required SPDX/);
  f.write('apps/web/main.ts', '// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary\nexport const title = "NexWrite";');
  assert.deepEqual(lintProvenance(f.base), []);
  f.write('apps/web/main.ts', '// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary\nconst x = "' + 'data-' + 'gramm' + '";');
  assert.match(lintProvenance(f.base).join('\n'), /forbidden reference/);
});
test('binaries and vendored files need reviewed per-file provenance', () => {
  const f = fixture(); f.write('apps/web/logo.png', Buffer.from([0, 1, 2]));
  f.write('vendor/third.js', '// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary\n');
  let errors = lintProvenance(f.base).join('\n'); assert.match(errors, /asset lacks/); assert.match(errors, /vendored path lacks/);
  f.write('docs/legal/ASSETS.md', '| apps/web/logo.png | NexWrite authors | LicenseRef-NexWrite-Proprietary | Original |\n');
  f.write('docs/legal/THIRD_PARTY.md', '| vendor/third.js | Author | MIT | https://example.com/source |\n');
  assert.deepEqual(lintProvenance(f.base), []);
  f.write('docs/legal/ASSETS.md', '| apps/web/logo.png | unknown | MIT | TODO |\n');
  errors = lintProvenance(f.base).join('\n'); assert.match(errors, /asset lacks/);
});
test('generated browser artifacts are excluded without hiding neighboring product source', () => {
  const f = fixture();
  f.write('apps/web/public/engine/nw_wasm.js', 'Generated bindings retain upstream licensing.');
  f.write('apps/web/public/engine/nw_wasm_bg.wasm', Buffer.from([0, 1, 2]));
  f.write('apps/web/public/notices/dependency.js', 'Generated notice directory.');
  f.write('apps/web/test-results/desktop.png', Buffer.from([0, 1, 2]));
  assert.deepEqual(lintProvenance(f.base), []);
  const neighbors = ['apps/web/src/main.ts', 'apps/web/public/custom.js', 'apps/web/public/engine-source/custom.js'];
  for (const file of neighbors) f.write(file, 'const originalSource = true;');
  const errors = lintProvenance(f.base);
  assert.equal(errors.length, neighbors.length);
  for (const file of neighbors) assert.ok(errors.some(error => error.startsWith(`${file}: missing required SPDX`)));
});
test('SPDX expression policy allows a permitted alternative and rejects unreviewed obligations', () => {
  for (const expression of ['MIT', 'MIT OR Apache-2.0', '(MIT OR Apache-2.0) AND Unicode-3.0', 'GPL-3.0-only OR MIT']) assert.equal(acceptable(expression), true, expression);
  for (const expression of [null, '', 'GPL-3.0-only', 'MIT AND LGPL-3.0-only', 'Apache-2.0 WITH LLVM-exception', 'MIT; shell', '(MIT OR', 'MIT OR']) assert.equal(acceptable(expression), false, String(expression));
});
test('Cargo historical dual-license spelling preserves its OR meaning without accepting unknown licenses', () => {
  assert.equal(acceptable(rustLicense('MIT/Apache-2.0')), true);
  assert.equal(acceptable(rustLicense('Apache-2.0/MIT')), true);
  assert.equal(acceptable(rustLicense('Unlicense/MIT')), true);
  assert.equal(acceptable(rustLicense('MIT/Unlicense')), true);
  assert.equal(acceptable(rustLicense('MIT/GPL-3.0-only')), false);
});
test('reviewed npm exceptions require exact names, versions and declared licenses', () => {
  assert.equal(acceptable('MPL-2.0'), false);
  assert.equal(acceptable('OFL-1.1'), false);
  for (const name of ['@axe-core/playwright', 'axe-core']) {
    const pkg = { name, version: '4.14.0', license: 'MPL-2.0' };
    assert.equal(reviewedNpmLicense(pkg, { devOnly: true }), true);
    for (const changed of [{ name: '@unreviewed/a11y' }, { version: '4.14.1' }, { license: 'MPL-2.0 OR GPL-3.0-only' }])
      assert.equal(reviewedNpmLicense({ ...pkg, ...changed }, { devOnly: true }), false);
    assert.equal(reviewedNpmLicense(pkg), false);
    assert.equal(reviewedNpmLicense(pkg, { devOnly: false }), false);
  }
  for (const name of ['@fontsource-variable/inter', '@fontsource-variable/jetbrains-mono', '@fontsource-variable/space-grotesk']) {
    const pkg = { name, version: '5.3.0', license: 'OFL-1.1' };
    assert.equal(reviewedNpmLicense(pkg), true);
    for (const changed of [{ name: '@fontsource-variable/unreviewed' }, { version: '5.3.1' }, { license: 'MPL-2.0' }])
      assert.equal(reviewedNpmLicense({ ...pkg, ...changed }), false);
  }
  assert.equal(reviewedNpmLicense(null, { devOnly: true }), false);
});
test('accessibility tool exception cannot escape into transitive production dependencies', () => {
  const tool = { name: '@axe-core/playwright', version: '4.14.0', license: 'MPL-2.0', dependencies: { 'axe-core': '~4.14.0' } };
  const engine = { name: 'axe-core', version: '4.14.0', license: 'MPL-2.0' };
  const app = { name: '@nexwrite/web', dependencies: { react: '19.3.1' }, devDependencies: { '@axe-core/playwright': '4.14.0' } };
  let production = productionNpmNames([app], [tool, engine]);
  for (const pkg of [tool, engine]) assert.equal(reviewedNpmLicense(pkg, { devOnly: !production.has(pkg.name) }), true);
  const runtime = { name: 'runtime', dependencies: { '@axe-core/playwright': '4.14.0' } };
  production = productionNpmNames([{ ...app, dependencies: { runtime: '1' } }], [runtime, tool, engine]);
  for (const pkg of [tool, engine]) assert.equal(reviewedNpmLicense(pkg, { devOnly: !production.has(pkg.name) }), false);
  production = productionNpmNames([{ ...app, optionalDependencies: { 'axe-core': '4.14.0' } }], [tool, engine]);
  assert.equal(production.has('axe-core'), true);
  production = productionNpmNames([{ ...app, peerDependencies: { 'axe-core': '4.14.0' } }], [tool, engine]);
  assert.equal(production.has('axe-core'), true);
});
test('CSS build tool exception is limited to reviewed native variants and exact development versions', () => {
  const names = ['lightningcss', 'lightningcss-darwin-x64', 'lightningcss-darwin-arm64', 'lightningcss-win32-x64-msvc', 'lightningcss-win32-arm64-msvc', 'lightningcss-linux-x64-gnu', 'lightningcss-linux-x64-musl', 'lightningcss-linux-arm64-gnu', 'lightningcss-linux-arm64-musl', 'lightningcss-linux-arm-gnueabihf', 'lightningcss-freebsd-x64', 'lightningcss-android-arm64'];
  for (const name of names) {
    const pkg = { name, version: '1.33.0', license: 'MPL-2.0' };
    assert.equal(reviewedNpmLicense(pkg, { devOnly: true }), true);
    assert.equal(reviewedNpmLicense(pkg), false);
    for (const changed of [{ version: '1.33.1' }, { name: 'lightningcss-unreviewed' }, { license: 'GPL-3.0-only' }])
      assert.equal(reviewedNpmLicense({ ...pkg, ...changed }, { devOnly: true }), false);
  }
  const tool = { name: 'lightningcss', version: '1.33.0', license: 'MPL-2.0', optionalDependencies: { 'lightningcss-linux-x64-gnu': '1.33.0' } };
  const production = productionNpmNames([{ name: 'app', dependencies: { vite: '8.3.4' } }], [{ name: 'vite', dependencies: { lightningcss: '^1.33.0' } }, tool]);
  assert.equal(production.has('lightningcss-linux-x64-gnu'), true);
  assert.equal(reviewedNpmLicense(tool, { devOnly: !production.has(tool.name) }), false);
  assert.equal(acceptable('MPL-2.0'), false);
});
test('local credential initializer generates independent values and preserves existing files', () => {
  const f = fixture(); const destination = path.join(f.base, '.env');
  assert.match(initialize(destination), /Created/);
  const original = fs.readFileSync(destination, 'utf8');
  const values = original.split('\n').filter(line => line && !line.startsWith('#')).map(line => line.split('=')[1]);
  assert.equal(values.length, 4); assert.equal(new Set(values).size, 4);
  assert.ok(values.every(value => /^[0-9a-f]{64}$/.test(value)));
  if (process.platform !== 'win32') assert.equal(fs.statSync(destination).mode & 0o777, 0o600);
  assert.match(initialize(destination), /Preserved/);
  assert.equal(fs.readFileSync(destination, 'utf8'), original);
});
