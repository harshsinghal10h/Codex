// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
// Exercises the built app and actual WASM. No analyzer output is synthesized.
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { once } from 'node:events';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import net from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';
import AxeBuilder from '@axe-core/playwright';

const app = fileURLToPath(new URL('..', import.meta.url));
const root = path.resolve(app, '../..');
const output = path.join(app, 'test-results');
await mkdir(output, { recursive: true });
await readFile(path.join(app, 'dist/index.html')); // Build is an explicit prerequisite.
const reservation = net.createServer();
reservation.listen(0, '127.0.0.1');
await once(reservation, 'listening');
const port = reservation.address().port;
await new Promise((resolve) => reservation.close(resolve));
const vite = fileURLToPath(new URL('./bin/vite.js', import.meta.resolve('vite/package.json')));
let serverOutput = '';
const server = spawn(process.execPath, [vite, 'preview', '--host', '127.0.0.1', '--port', String(port), '--strictPort'], { cwd: app, stdio: ['ignore', 'pipe', 'pipe'] });
for (const stream of [server.stdout, server.stderr]) stream.on('data', (chunk) => { serverOutput = (serverOutput + chunk).slice(-4000); });
const origin = `http://127.0.0.1:${port}`;
const report = { scope: 'Local Chromium production-build checks; automated accessibility and synthetic composition only, not device or screen-reader certification.', started: new Date().toISOString(), versions: { node: process.version }, cases: [], timings: {}, accessibility: [] };
let browser;
const keys = { draft: 'nexwrite.m1.draft', consent: 'nexwrite.m1.draft-consent', theme: 'nexwrite.m1.theme' };
const sentence = 'The cat sat by the log and saw the dog.';
const fixture = Array(3).fill(sentence).join(' ');
const navigate = (page, name) => page.getByRole('navigation', { name: 'Main navigation' }).getByRole('button', { name, exact: true }).click();
const source = (page) => page.getByRole('textbox', { name: 'Source editor' });
const metric = async (page, key) => Number(await page.locator(`[data-metric="${key}"]`).getAttribute('data-value'));
const score = async (page, kind) => Number(await page.locator(`[data-score="${kind}"]`).getAttribute('data-value'));
async function checked(page, words) {
  await page.waitForFunction((expected) => {
    const element = document.querySelector('[data-metric="word_count"]');
    return element && (expected === undefined || Number(element.dataset.value) === expected);
  }, words, { timeout: 30_000 });
}
async function start(page) { await page.goto(origin); await checked(page, 0); }
async function noOverflow(page) { assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false, 'page overflows horizontally'); }
async function scan(page, label) {
  const result = await new AxeBuilder({ page }).analyze();
  const violations = result.violations.map(({ id, impact, nodes }) => ({ id, impact, selectors: nodes.map((node) => node.target) }));
  report.accessibility.push({ state: label, violations });
  assert.deepEqual(violations, [], `axe violations in ${label}`);
}

// Passive instrumentation records real Worker traffic. The stale-response test
// queues original MessageEvents; it never substitutes a result or analyzer.
function observeWorkers() {
  const NativeWorker = window.Worker;
  window.__nw = { requests: [], results: [], queued: [], hold: false, terminated: 0 };
  window.Worker = class extends NativeWorker {
    constructor(...args) {
      super(...args);
      this.handler = null;
      this.addEventListener('message', (event) => {
        if (event.data?.type === 'result') window.__nw.results.push(event.data.result);
        const deliver = () => this.handler?.call(this, event);
        if (window.__nw.hold && event.data?.type === 'result') window.__nw.queued.push(deliver);
        else deliver();
      });
    }
    set onmessage(handler) { this.handler = handler; }
    get onmessage() { return this.handler; }
    postMessage(message, ...rest) {
      window.__nw.requests.push({ type: message.type, version: message.doc_version, format: message.input_format });
      return super.postMessage(message, ...rest);
    }
    terminate() { window.__nw.terminated++; return super.terminate(); }
  };
}

async function test(name, run, options = {}) {
  const context = await browser.newContext({ viewport: options.viewport ?? { width: 1440, height: 1100 }, acceptDownloads: true, serviceWorkers: options.serviceWorkers ?? 'block' });
  if (options.observe) await context.addInitScript(observeWorkers);
  const page = await context.newPage();
  const pageErrors = [];
  page.on('pageerror', () => pageErrors.push('Uncaught browser error'));
  const started = performance.now();
  process.stdout.write(`RUN ${name}\n`);
  try {
    await run({ page, context });
    assert.deepEqual(pageErrors, [], 'uncaught browser error');
    report.cases.push({ name, status: 'passed', milliseconds: Math.round(performance.now() - started) });
    process.stdout.write(`PASS ${name}\n`);
  } catch (error) {
    const failure = error instanceof Error ? error.message : 'Unknown failure';
    report.cases.push({ name, status: 'failed', detail: failure, milliseconds: Math.round(performance.now() - started) });
    await page.screenshot({ path: path.join(output, `failure-${report.cases.length}.png`), fullPage: true }).catch(() => {});
    process.stdout.write(`FAIL ${name}: ${failure}\n`);
  } finally { await context.close(); }
}

try {
  for (let attempt = 0; attempt < 100; attempt++) {
    if (server.exitCode !== null) throw new Error(`Preview failed: ${serverOutput}`);
    if (await fetch(origin).then((response) => response.ok).catch(() => false)) break;
    if (attempt === 99) throw new Error('Preview did not start');
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  browser = await chromium.launch({ headless: true, ...(process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH } : {}) });
  report.versions.chromium = browser.version();
  for (const dependency of ['playwright', '@axe-core/playwright', 'vite', 'react']) {
    report.versions[dependency] = JSON.parse(await readFile(path.join(app, 'node_modules', dependency, 'package.json'), 'utf8')).version;
  }

  await test('fresh memory-only, local assets/CSP, real WASM and independent/native metrics', async ({ page }) => {
    const requests = [];
    page.on('request', (request) => requests.push({ url: request.url(), method: request.method(), body: request.postData() }));
    const response = await page.goto(origin);
    const policy = response.headers()['content-security-policy'];
    assert.ok(policy.includes("'wasm-unsafe-eval'"));
    assert.ok(!policy.includes("'unsafe-eval'"));
    await checked(page, 0);
    assert.equal(await source(page).inputValue(), '');
    await navigate(page, 'Settings');
    assert.equal(await page.getByRole('checkbox', { name: 'Save draft in this browser' }).isChecked(), false);
    await navigate(page, 'Write');
    assert.equal(await page.evaluate((owned) => localStorage.getItem(owned.draft), keys), null);
    const started = performance.now();
    await source(page).fill(fixture);
    await checked(page, 30);
    report.timings.fixtureDebounceAndAnalysisMs = Math.round(performance.now() - started);
    assert.equal(await metric(page, 'sentence_count'), 3);
    assert.ok(Math.abs(await score(page, 'flesch_reading_ease') - 112.085) < 0.000001);
    assert.ok(Math.abs(await score(page, 'flesch_kincaid_grade') - 0.11) < 0.000001);
    assert.ok(Math.abs(await score(page, 'gunning_fog') - 4) < 0.000001);
    assert.ok(Math.abs(await score(page, 'coleman_liau_index') + 1.708) < 0.000001);
    assert.ok(Math.abs(await score(page, 'automated_readability_index') + 2.771) < 0.000001);
    assert.equal(await page.locator('[data-score="smog_grade"]').count(), 0);
    assert.ok(Math.abs(await score(page, 'type_token_ratio') - 8 / 30) < 0.000001);
    assert.ok(Math.abs(await metric(page, 'reading_seconds') - 1800 / 238) < 0.000001);
    assert.equal(await metric(page, 'speaking_seconds'), 12);
    const fixturePath = path.join(output, 'native-fixture.txt');
    await writeFile(fixturePath, fixture);
    const nativeRun = spawnSync('cargo', ['run', '-q', '-p', 'nw-cli', '--locked', '--', 'analyze', fixturePath, '--format', 'json', '--input-format', 'plain', '--doc-version', '1', '--locale', 'en-US'], { cwd: root, encoding: 'utf8', timeout: 120_000 });
    assert.equal(nativeRun.status, 0, 'native fixture CLI failed');
    const actual = await page.evaluate(() => window.__nw.results.at(-1));
    assert.deepEqual(actual, JSON.parse(nativeRun.stdout));
    await page.waitForTimeout(400);
    assert.equal(await page.evaluate((owned) => localStorage.getItem(owned.draft), keys), null);
    assert.ok(requests.some((request) => request.url.endsWith('.wasm')));
    assert.ok(requests.some((request) => request.url.endsWith('.woff2')));
    assert.ok(requests.every((request) => new URL(request.url).origin === origin && new URL(request.url).search === '' && request.method === 'GET' && request.body === null));
    for (const notice of ['inter-OFL.txt', 'space-grotesk-OFL.txt', 'jetbrains-mono-OFL.txt', 'react-MIT.txt', 'react-dom-MIT.txt', 'scheduler-MIT.txt', 'engine-third-party.txt']) {
      const noticeResponse = await page.request.get(`${origin}/notices/${notice}`);
      assert.ok(noticeResponse.ok());
      const contents = await noticeResponse.text();
      assert.ok(contents.length > 100 && !contents.includes('<!doctype html>'));
    }
    await scan(page, 'desktop dark real metrics');
    await noOverflow(page);
    await page.screenshot({ path: path.join(output, 'desktop.png'), fullPage: true });
  }, { observe: true });

  await test('Unicode accept/Undo preserves adopted source; typing clears accept history; exact export', async ({ page }) => {
    await start(page);
    const original = '😀 Hi  café.\n👨‍👩‍👧‍👦 e\u0301  שלום.\n<script>alert("literal")</script>';
    await source(page).fill(original);
    await page.getByRole('button', { name: 'Accept suggestion' }).first().waitFor();
    await page.getByRole('button', { name: 'Accept suggestion' }).first().click();
    assert.equal(await source(page).inputValue(), original.replace('Hi  café', 'Hi café'));
    assert.equal(await source(page).evaluate((element) => element === document.activeElement), true);
    await page.getByRole('button', { name: 'Undo accept' }).click();
    assert.equal(await source(page).inputValue(), original);
    await page.getByRole('button', { name: 'Accept suggestion' }).first().waitFor();
    await page.getByRole('button', { name: 'Accept suggestion' }).first().click();
    await source(page).press('End');
    await source(page).pressSequentially('x');
    assert.equal(await page.getByRole('button', { name: 'Undo accept' }).isDisabled(), true);
    await page.getByRole('combobox', { name: 'Source format' }).selectOption('markdown');
    const adopted = await source(page).inputValue();
    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Export source' }).click();
    const download = await downloadPromise;
    assert.equal(download.suggestedFilename(), 'nexwrite-draft.md');
    assert.deepEqual(await readFile(await download.path()), Buffer.from(adopted, 'utf8'));
    assert.equal(await page.locator('textarea script').count(), 0);
  });

  await test('late actual Worker responses are ignored after edits', async ({ page }) => {
    await start(page);
    await page.evaluate(() => { window.__nw.hold = true; });
    await source(page).fill('Old  source.');
    await page.waitForFunction(() => window.__nw.queued.length === 1);
    await source(page).fill('Current source has five words.');
    await page.waitForFunction(() => window.__nw.queued.length === 2);
    await page.evaluate(() => window.__nw.queued.shift()());
    assert.equal(await page.getByRole('button', { name: 'Accept suggestion' }).count(), 0);
    assert.equal(await page.locator('[data-metric="word_count"]').count(), 0);
    await page.evaluate(() => { window.__nw.hold = false; window.__nw.queued.shift()(); });
    await checked(page, 5);
    assert.equal(await page.getByRole('button', { name: 'Accept suggestion' }).count(), 0);
    const version = Number(await page.getByTestId('metrics').getAttribute('data-version'));
    assert.equal(version, await page.evaluate(() => window.__nw.requests.at(-1).version));
  }, { observe: true });

  await test('composition suppresses checks and actions until committed; pause terminates Worker', async ({ page }) => {
    await start(page);
    await source(page).fill('Before  composition.');
    await page.getByRole('button', { name: 'Accept suggestion' }).waitFor();
    const before = await page.evaluate(() => window.__nw.requests.length);
    await source(page).dispatchEvent('compositionstart', { data: '' });
    await source(page).fill('日本語  committed.');
    await page.waitForTimeout(450);
    assert.equal(await page.evaluate(() => window.__nw.requests.length), before);
    assert.equal(await page.getByRole('button', { name: 'Accept suggestion' }).count(), 0);
    assert.equal(await page.getByRole('button', { name: 'Undo accept' }).isDisabled(), true);
    await source(page).dispatchEvent('compositionend', { data: '日本語' });
    await page.getByRole('button', { name: 'Accept suggestion' }).waitFor();
    assert.equal(await page.evaluate(() => window.__nw.requests.length), before + 1);
    await page.getByRole('button', { name: 'Pause checks', exact: true }).click();
    await page.waitForFunction(() => window.__nw.terminated === 1);
    await source(page).fill('Checks pause while writing.');
    const paused = await page.evaluate(() => window.__nw.requests.length);
    await page.waitForTimeout(350);
    assert.equal(await page.evaluate(() => window.__nw.requests.length), paused);
    await page.getByRole('button', { name: 'Resume checks' }).click();
    await checked(page, 4);
  }, { observe: true });

  await test('opt-in draft restore, opt-out removal, and scoped clear preserve current/unrelated data', async ({ page }) => {
    await start(page);
    await page.evaluate(() => localStorage.setItem('unrelated.application', 'keep'));
    await source(page).fill('Local-only draft 😀.');
    await page.waitForTimeout(450);
    assert.equal(await page.evaluate((owned) => localStorage.getItem(owned.draft), keys), null);
    await navigate(page, 'Settings');
    await page.getByRole('checkbox', { name: 'Save draft in this browser' }).check();
    await navigate(page, 'Write');
    await page.waitForFunction((owned) => localStorage.getItem(owned.consent) === '1' && JSON.parse(localStorage.getItem(owned.draft)).source.includes('😀'), keys);
    await page.reload();
    await checked(page);
    assert.equal(await source(page).inputValue(), 'Local-only draft 😀.');
    await navigate(page, 'Settings');
    await page.getByRole('checkbox', { name: 'Save draft in this browser' }).uncheck();
    await navigate(page, 'Write');
    assert.equal(await source(page).inputValue(), 'Local-only draft 😀.');
    assert.equal(await page.evaluate((owned) => localStorage.getItem(owned.draft), keys), null);
    await page.reload();
    await checked(page, 0);
    assert.equal(await source(page).inputValue(), '');
    await source(page).fill('Keep this current source.');
    await navigate(page, 'Settings');
    await page.getByRole('checkbox', { name: 'Save draft in this browser' }).check();
    await navigate(page, 'Write');
    await navigate(page, 'Settings');
    await page.getByRole('combobox', { name: 'Appearance' }).selectOption('light');
    await navigate(page, 'Write');
    await page.waitForTimeout(400);
    await navigate(page, 'Settings');
    page.once('dialog', dialog => dialog.accept());
    await page.getByRole('button', { name: 'Clear local NexWrite data' }).click();
    await page.waitForFunction((owned) => Object.values(owned).every(key => localStorage.getItem(key) === null), keys);
    await navigate(page, 'Write');
    assert.equal(await source(page).inputValue(), 'Keep this current source.');
    assert.deepEqual(await page.evaluate((owned) => Object.values(owned).map((key) => localStorage.getItem(key)), keys), [null, null, null]);
    assert.equal(await page.evaluate(() => localStorage.getItem('unrelated.application')), 'keep');
    await page.reload();
    await checked(page, 0);
  });

  await test('restored CRLF/CR adopts LF consistently for DOM, analysis, accept/Undo and export', async ({ page, context }) => {
    const raw = 'First  line.\r\nSecond line.\rThird line. 😀';
    const adopted = raw.replace(/\r\n?/g, '\n');
    await context.addInitScript(({ owned, saved }) => {
      localStorage.setItem(owned.consent, '1');
      localStorage.setItem(owned.draft, JSON.stringify({ version: 1, source: saved, format: 'plain' }));
    }, { owned: keys, saved: raw });
    await page.goto(origin);
    await checked(page);
    assert.equal(await source(page).inputValue(), adopted);
    assert.equal(await metric(page, 'source_utf8_bytes'), Buffer.byteLength(adopted));
    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Export source' }).click();
    assert.deepEqual(await readFile(await (await downloadPromise).path()), Buffer.from(adopted));
    await page.getByRole('button', { name: 'Accept suggestion' }).click();
    assert.equal(await source(page).inputValue(), adopted.replace('First  line', 'First line'));
    await page.getByRole('button', { name: 'Undo accept' }).click();
    assert.equal(await source(page).inputValue(), adopted);
    await checked(page);
    assert.equal(await metric(page, 'source_utf8_bytes'), Buffer.byteLength(adopted));
  });

  await test('storage denial/quota and malformed saved records remain editable', async ({ page, context }) => {
    await context.addInitScript(() => { Storage.prototype.setItem = function () { throw new DOMException('Blocked', 'QuotaExceededError'); }; });
    await start(page);
    await source(page).fill('Keep  editing.');
    await navigate(page, 'Settings');
    await page.getByRole('checkbox', { name: 'Save draft in this browser' }).check();
    await navigate(page, 'Write');
    await page.getByTestId('storage-state').filter({ hasText: 'Storage unavailable' }).waitFor();
    await page.getByRole('button', { name: 'Accept suggestion' }).click();
    assert.equal(await source(page).inputValue(), 'Keep editing.');
  });
  for (const [label, record] of [
    ['malformed', '{'], ['unknown-version', JSON.stringify({ version: 9, source: 'Do not import', format: 'plain' })],
    ['invalid-format', JSON.stringify({ version: 1, source: 'Do not import', format: 'html' })],
    ['oversized', JSON.stringify({ version: 1, source: 'a'.repeat(1_048_577), format: 'plain' })],
    ['malformed-Unicode', JSON.stringify({ version: 1, source: '\ud800', format: 'plain' })],
  ]) {
    await test(`${label} draft is not imported`, async ({ page, context }) => {
      await context.addInitScript(({ owned, saved }) => { localStorage.setItem(owned.consent, '1'); localStorage.setItem(owned.draft, saved); }, { owned: keys, saved: record });
      await start(page);
      assert.equal(await source(page).inputValue(), '');
      await navigate(page, 'Settings');
    assert.equal(await page.getByRole('checkbox', { name: 'Save draft in this browser' }).isChecked(), false);
    await navigate(page, 'Write');
      await page.getByText('A saved setting or draft could not be read safely.', { exact: false }).waitFor();
      await source(page).fill('Still  editable.');
      await page.getByRole('button', { name: 'Accept suggestion' }).waitFor();
    });
  }
  await test('storage read denial is bounded and analysis stays available', async ({ page, context }) => {
    await context.addInitScript(() => { Storage.prototype.getItem = function () { throw new DOMException('Blocked', 'SecurityError'); }; });
    await start(page);
    await page.getByTestId('storage-state').filter({ hasText: 'Local restore unavailable' }).waitFor();
    await source(page).fill('Working  in memory.');
    await page.getByRole('button', { name: 'Accept suggestion' }).waitFor();
  });

  await test('Markdown source protects code/link/HTML and omits incomplete grade estimates', async ({ page }) => {
    await start(page);
    const markdown = '# Heading\n\nVisible  prose.\n\n`code  stays` [label  stays](https://example.invalid)\n\n<script>not  executed</script>\n\n' + fixture;
    await page.getByRole('combobox', { name: 'Source format' }).selectOption('markdown');
    await source(page).fill(markdown);
    await page.getByRole('button', { name: 'Accept suggestion' }).waitFor();
    assert.equal(await page.getByRole('button', { name: 'Accept suggestion' }).count(), 1);
    assert.equal(await page.locator('[data-score="flesch_reading_ease"]').count(), 0);
    await page.locator('[data-warning="incomplete_readability_projection"]').waitFor();
    await page.getByRole('button', { name: 'Accept suggestion' }).click();
    assert.equal(await source(page).inputValue(), markdown.replace('Visible  prose', 'Visible prose'));
    assert.equal(await page.locator('script[src^="https:"]').count(), 0);
  });

  await test('UTF-8 input cap and malformed UTF-16 do not produce success or repaired export', async ({ page }) => {
    await start(page);
    await source(page).fill('x'.repeat(1_048_576));
    await checked(page, 1);
    assert.equal(await metric(page, 'source_utf8_bytes'), 1_048_576);
    await source(page).fill('x'.repeat(1_048_577));
    await page.getByText('The source exceeds the 1 MiB UTF-8 limit.', { exact: false }).waitFor();
    assert.equal(await page.locator('[data-metric="word_count"]').count(), 0);
    assert.equal(await page.getByRole('button', { name: 'Export source' }).isDisabled(), true);
    await source(page).fill('é'.repeat(524_289));
    await page.getByText('The source exceeds the 1 MiB UTF-8 limit.', { exact: false }).waitFor();
    // Native browser insertion may repair this string. Inject a malformed DOM
    // value with its native setter to exercise the app's strict rejection path.
    assert.equal(await source(page).evaluate((element) => {
      Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set.call(element, String.fromCharCode(0xd800));
      element.dispatchEvent(new Event('input', { bubbles: true }));
      return element.value.charCodeAt(0);
    }), 0xd800);
    await page.getByText('The source contains an unpaired Unicode surrogate.', { exact: false }).waitFor();
    assert.equal(await page.getByRole('button', { name: 'Export source' }).isDisabled(), true);
    await source(page).fill('Valid source again.');
    await checked(page, 3);
  });

  await test('dense real results render bounded cards with truthful total', async ({ page }) => {
    await start(page);
    await source(page).fill('a  b. '.repeat(1000));
    await page.getByTestId('suggestion-total').waitFor();
    assert.equal(await page.getByTestId('suggestion-total').innerText(), 'Showing 20 of 1000 suggestions.');
    assert.equal(await page.getByRole('button', { name: 'Accept suggestion' }).count(), 20);
    await page.getByRole('button', { name: 'Accept suggestion' }).first().click();
    await page.getByTestId('suggestion-total').filter({ hasText: 'Showing 20 of 999' }).waitFor();
  });

  await test('controlled Worker asset failure has truthful error and real retry recovery', async ({ page, context }) => {
    const block = (route) => route.abort('blockedbyclient');
    await context.route('**/engine/nw_wasm.js', block);
    await page.goto(origin);
    await page.getByRole('button', { name: 'Retry local engine' }).waitFor();
    await source(page).fill('Keep  this source.');
    assert.equal(await page.locator('[data-metric="word_count"]').count(), 0);
    await context.unroute('**/engine/nw_wasm.js', block);
    await page.getByRole('button', { name: 'Retry local engine' }).click();
    await page.getByRole('button', { name: 'Accept suggestion' }).waitFor();
    assert.equal(await source(page).inputValue(), 'Keep  this source.');
  });

  await test('local library save/search/rename/duplicate/delete and reload preserve exact source', async ({ page }) => {
    await start(page);
    const text = 'A saved idea 😀.\n\nKeep  this exact source.';
    await source(page).fill(text);
    await page.getByRole('textbox', { name: 'Document title' }).fill('A thoughtful note');
    await page.getByRole('button', { name: 'Save document', exact: true }).click();
    await page.getByTestId('document-save-state').filter({ hasText: 'Saved in this browser' }).waitFor();
    await navigate(page, 'Library');
    await page.getByRole('heading', { name: 'A thoughtful note', exact: true }).waitFor();
    await page.screenshot({ path: path.join(output, 'library.png'), fullPage: true });
    await page.getByRole('searchbox', { name: 'Search local documents' }).fill('not present');
    await page.getByText('No documents match this search.').waitFor();
    await page.getByRole('searchbox').fill('exact source');
    await page.getByRole('heading', { name: 'A thoughtful note', exact: true }).waitFor();
    await page.getByRole('button', { name: 'Duplicate A thoughtful note', exact: true }).click();
    await page.getByRole('heading', { name: 'A thoughtful note copy', exact: true }).waitFor();
    page.once('dialog', dialog => dialog.accept('Renamed note'));
    await page.getByRole('button', { name: 'Rename A thoughtful note copy', exact: true }).click();
    await page.getByRole('heading', { name: 'Renamed note', exact: true }).waitFor();
    page.once('dialog', dialog => dialog.dismiss());
    await page.getByRole('button', { name: 'Delete Renamed note', exact: true }).click();
    assert.equal(await page.getByRole('heading', { name: 'Renamed note' }).count(), 1);
    page.once('dialog', dialog => dialog.accept());
    await page.getByRole('button', { name: 'Delete Renamed note', exact: true }).click();
    await page.getByRole('heading', { name: 'Renamed note' }).waitFor({ state: 'detached' });
    await page.reload(); await checked(page, 0);
    await navigate(page, 'Library');
    await page.getByRole('heading', { name: 'A thoughtful note', exact: true }).click();
    assert.equal(await source(page).inputValue(), text);
    assert.equal(await page.getByRole('textbox', { name: 'Document title' }).inputValue(), 'A thoughtful note');
    await source(page).fill('Unsaved source stays.');
    await navigate(page, 'Library');
    page.once('dialog', dialog => dialog.dismiss());
    await page.getByRole('button', { name: 'New document', exact: true }).click();
    await navigate(page, 'Write');
    assert.equal(await source(page).inputValue(), 'Unsaved source stays.');
  });

  await test('denied IndexedDB Save stays unsaved while local checking works', async ({ page, context }) => {
    await context.addInitScript(() => { IDBFactory.prototype.open = function () { throw new DOMException('Denied', 'SecurityError'); }; });
    await start(page);
    await source(page).fill('Still  writing.');
    await page.getByRole('button', { name: 'Save document', exact: true }).click();
    await page.locator('.action-notice').filter({ hasText: 'The document library is unavailable. Your current draft remains in memory.' }).filter({ visible: true }).waitFor();
    assert.match(await page.getByTestId('document-save-state').innerText(), /Unsaved/);
    await page.getByRole('button', { name: 'Accept suggestion' }).click();
    assert.equal(await source(page).inputValue(), 'Still writing.');
  });

  await test('SAF-style local file import adopts LF and rejects invalid UTF8 without replacing source', async ({ page }) => {
    await start(page);
    const raw = 'A  file.\r\n😀 Next line.\rLast.';
    await page.getByLabel('Import source file').setInputFiles({ name: 'note.md', mimeType: 'text/markdown', buffer: Buffer.from(raw) });
    await page.getByText('Source imported into this draft.', { exact: false }).waitFor();
    assert.equal(await source(page).inputValue(), raw.replace(/\r\n?/g, '\n'));
    assert.equal(await page.getByRole('combobox', { name: 'Source format' }).inputValue(), 'markdown');
    page.once('dialog', dialog => dialog.accept());
    await page.getByLabel('Import source file').setInputFiles({ name: 'bad.txt', mimeType: 'text/plain', buffer: Buffer.from([0xc3, 0x28]) });
    await page.getByText('This file could not be imported.', { exact: false }).waitFor();
    assert.equal(await source(page).inputValue(), raw.replace(/\r\n?/g, '\n'));
  });

  const mockBase = 'https://backend.nexwrite.test';
  const fixtureHeaders = { 'access-control-allow-origin': origin, 'access-control-allow-headers': 'authorization,content-type', 'access-control-allow-methods': 'GET,POST,OPTIONS' };
  const mockProfile = { protocol_version: 1, version: 'contract-fixture', engine: { available: true, rule_count: 1, locales: ['en-US'], input_formats: ['plain', 'markdown'] }, tasks: ['rewrite', 'summarize', 'compose'], providers: [{ id: 'openai', label: 'Contract test provider', configured: true, models: ['contract-model'] }], accounts: false, cloud_docs: false, auth_required: true };
  async function connectFixture(page) {
    await page.route(`${mockBase}/v1/capabilities`, route => route.fulfill({ headers: fixtureHeaders, ...(route.request().method() === 'OPTIONS' ? { status: 204, body: '' } : { json: mockProfile }) }));
    await navigate(page, 'Settings');
    await page.getByRole('textbox', { name: 'Backend HTTPS address' }).fill(mockBase);
    await page.getByLabel('Backend access token').fill('browser-contract-token');
    await page.getByRole('button', { name: 'Connect backend', exact: true }).click();
    await page.getByTestId('connection-status').filter({ hasText: 'Connected to backend.nexwrite.test' }).waitFor();
    await navigate(page, 'Assist');
  }
  const fixtureResponse = (request, replacement = 'A proposed change 😀.') => ({ request_id: '860ff713-a57c-4ab6-85be-a54b1079582d', doc_version: request.doc_version, provider: request.provider, model: request.model, task: request.task, preset: request.preset, original: request.text, replacement, warnings: ['Local protocol fixture; no live provider was called.'] });

  await test('optional AI starts unconfigured and no text request occurs without explicit consent', async ({ page }) => {
    const sent = [];
    page.on('request', request => { if (request.method() === 'POST') sent.push(request.url()); });
    await start(page); await source(page).fill('Private writing.');
    await navigate(page, 'Assist');
    await page.getByRole('button', { name: 'Set up AI connection' }).waitFor();
    assert.deepEqual(sent, []);
    await connectFixture(page);
    assert.equal(await page.getByRole('button', { name: 'Send AI request' }).isDisabled(), true);
    assert.equal(await page.getByTestId('ai-source-preview').innerText(), 'Private writing.');
    assert.deepEqual(sent, []);
    assert.equal(await page.evaluate(() => Object.values(localStorage).some(value => value.includes('browser-contract-token'))), false);
    await scan(page, 'AI consent with configured protocol fixture');
  });

  await test('AI protocol fixture Apply/Undo preserves Unicode, literal text, consent and provenance', async ({ page }) => {
    let submitted;
    await start(page);
    const original = 'My source 😀.';
    const replacement = '<script>literal, never executed</script> 😀';
    await source(page).fill(original);
    await connectFixture(page);
    await page.route(`${mockBase}/v1/rewrite`, async route => { if (route.request().method() === 'OPTIONS') { await route.fulfill({ status: 204, headers: fixtureHeaders, body: '' }); return; } submitted = route.request().postDataJSON(); await route.fulfill({ headers: fixtureHeaders, json: fixtureResponse(submitted, replacement) }); });
    await page.getByRole('checkbox', { name: 'I agree to send this text' }).check();
    await page.getByRole('button', { name: 'Send AI request' }).click();
    await page.getByTestId('ai-proposal').waitFor();
    await page.screenshot({ path: path.join(output, 'assist.png'), fullPage: true });
    assert.equal(submitted.text, original); assert.equal(submitted.cloud_consent, true);
    assert.equal(await page.getByRole('checkbox', { name: 'I agree to send this text' }).isChecked(), false);
    await page.getByRole('button', { name: 'Apply AI proposal', exact: true }).click();
    assert.equal(await source(page).inputValue(), replacement);
    assert.equal(await page.locator('main script').count(), 0);
    await page.getByTestId('ai-provenance').filter({ hasText: 'openai / contract-model' }).waitFor();
    await page.getByRole('button', { name: 'Undo accept' }).click();
    assert.equal(await source(page).inputValue(), original);
  });

  await test('AI protocol fixture rejects stale Apply and cancellation leaves the source unchanged', async ({ page }) => {
    let waiting;
    await start(page); await source(page).fill('Original draft.'); await connectFixture(page);
    await page.route(`${mockBase}/v1/rewrite`, async route => { if (route.request().method() === 'OPTIONS') { await route.fulfill({ status: 204, headers: fixtureHeaders, body: '' }); return; } const request = route.request().postDataJSON(); await new Promise(resolve => { waiting = () => resolve(route.fulfill({ headers: fixtureHeaders, json: fixtureResponse(request) }).catch(() => {})); }); });
    await page.getByRole('checkbox', { name: 'I agree to send this text' }).check();
    await page.getByRole('button', { name: 'Send AI request' }).click();
    await page.waitForFunction(() => document.querySelector('[data-testid="ai-status"]').textContent.includes('Waiting'));
    while (!waiting) await page.waitForTimeout(20);
    await navigate(page, 'Write'); await source(page).fill('A newer draft.');
    await waiting(); await navigate(page, 'Assist');
    await page.getByTestId('ai-proposal').waitFor();
    assert.equal(await page.getByRole('button', { name: 'Apply AI proposal' }).isDisabled(), true);
    waiting = null;
    await page.getByRole('checkbox', { name: 'I agree to send this text' }).check();
    await page.getByRole('button', { name: 'Send AI request' }).click();
    while (!waiting) await page.waitForTimeout(20);
    await page.getByRole('button', { name: 'Cancel AI request' }).click();
    await waiting();
    await page.getByTestId('ai-status').filter({ hasText: 'cancelled' }).waitFor();
    assert.equal(await page.getByTestId('ai-proposal').count(), 0);
    await navigate(page, 'Write'); assert.equal(await source(page).inputValue(), 'A newer draft.');
  });

  await test('AI rate-limit and mismatched-response protocol fixtures never change the source', async ({ page }) => {
    await start(page); await source(page).fill('A protected draft.'); await connectFixture(page);
    let attempt = 0;
    await page.route(`${mockBase}/v1/rewrite`, async route => {
      if (route.request().method() === 'OPTIONS') { await route.fulfill({ status: 204, headers: fixtureHeaders, body: '' }); return; }
      const request = route.request().postDataJSON();
      if (attempt++ === 0) await route.fulfill({ status: 429, headers: fixtureHeaders, json: { title: 'Rate limited' } });
      else await route.fulfill({ headers: fixtureHeaders, json: { ...fixtureResponse(request), doc_version: request.doc_version + 1 } });
    });
    await page.getByRole('checkbox', { name: 'I agree to send this text' }).check();
    await page.getByRole('button', { name: 'Send AI request' }).click();
    await page.getByTestId('ai-status').filter({ hasText: 'The backend is busy' }).waitFor();
    await page.getByRole('checkbox', { name: 'I agree to send this text' }).check();
    await page.getByRole('button', { name: 'Send AI request' }).click();
    await page.getByTestId('ai-status').filter({ hasText: 'does not match this request' }).waitFor();
    assert.equal(await page.getByTestId('ai-proposal').count(), 0);
    await navigate(page, 'Write'); assert.equal(await source(page).inputValue(), 'A protected draft.');
  });

  await test('offline static shell restores only explicitly saved local documents and never caches API data', async ({ page, context }) => {
    await start(page);
    await source(page).fill('An offline note 😀.');
    await page.getByRole('textbox', { name: 'Document title' }).fill('Offline note');
    await page.getByRole('button', { name: 'Save document', exact: true }).click();
    await page.getByTestId('document-save-state').filter({ hasText: 'Saved in this browser' }).waitFor();
    await page.evaluate(async () => { await navigator.serviceWorker.ready; if (!navigator.serviceWorker.controller) await new Promise(resolve => navigator.serviceWorker.addEventListener('controllerchange', resolve, { once: true })); });
    const cached = await page.evaluate(async () => (await Promise.all((await caches.keys()).filter(key => key.startsWith('nexwrite-shell-')).map(async key => (await (await caches.open(key)).keys()).map(request => new URL(request.url).pathname)))).flat());
    assert.ok(cached.some(url => url.endsWith('.wasm')));
    assert.ok(cached.includes('/index.html'));
    assert.ok(cached.every(url => !url.includes('/v1/') && !url.includes('document') && !url.includes('token')));
    await context.setOffline(true); await page.reload(); await checked(page, 0);
    await navigate(page, 'Library'); await page.getByRole('heading', { name: 'Offline note', exact: true }).click();
    await checked(page, 3);
    assert.equal(await source(page).inputValue(), 'An offline note 😀.');
    await page.getByText('Offline · local tools ready').waitFor();
  }, { serviceWorkers: 'allow' });

  await test('320px keyboard, light/dark, reduced motion and forced colors remain operable', async ({ page }) => {
    await start(page);
    await noOverflow(page);
    await page.keyboard.press('Tab');
    assert.equal(await page.getByRole('link', { name: 'Skip to source editor' }).evaluate((element) => element === document.activeElement), true);
    await page.keyboard.press('Enter');
    assert.equal(await source(page).evaluate((element) => element === document.activeElement), true);
    await source(page).fill('A  little space.');
    await page.getByRole('button', { name: 'Accept suggestion' }).waitFor();
    await scan(page, '320px dark suggestion');
    await navigate(page, 'Settings');
    await page.getByRole('combobox', { name: 'Appearance' }).selectOption('light');
    await navigate(page, 'Write');
    await scan(page, '320px light suggestion');
    await noOverflow(page);
    await page.screenshot({ path: path.join(output, 'mobile.png'), fullPage: true });
    await page.emulateMedia({ reducedMotion: 'reduce', forcedColors: 'active' });
    await noOverflow(page);
    await page.getByRole('button', { name: 'Accept suggestion' }).focus();
    await page.keyboard.press('Enter');
    assert.equal(await source(page).inputValue(), 'A little space.');
    assert.equal(await source(page).evaluate((element) => getComputedStyle(element).outlineStyle), 'solid');
    assert.equal(await page.evaluate(() => matchMedia('(prefers-reduced-motion: reduce)').matches), true);
  }, { viewport: { width: 320, height: 800 } });
} catch (error) {
  report.cases.push({ name: 'runner', status: 'failed', detail: error instanceof Error ? error.message : 'Unknown failure' });
} finally {
  await browser?.close();
  server.kill('SIGTERM');
  await Promise.race([once(server, 'exit'), new Promise((resolve) => setTimeout(resolve, 2000))]);
  if (server.exitCode === null) server.kill('SIGKILL');
  report.completed = new Date().toISOString();
  report.status = report.cases.some((entry) => entry.status === 'failed') ? 'failed' : 'passed';
  await writeFile(path.join(output, 'e2e-report.json'), JSON.stringify(report, null, 2) + '\n');
  process.stdout.write(`${report.status.toUpperCase()}: ${report.cases.length} local browser cases; report test-results/e2e-report.json\n`);
  if (report.status === 'failed') process.exitCode = 1;
}
