<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# Independent local M1 browser review

Observed 2026-10-20, completed at approximately 05:23 UTC. Reviewer: child agent
`/root/m1_metrics`; implementation owner: `/root/m1_web`. This review made no
repository edits, Git changes, dependency changes, or changes to another
agent's processes. Test state existed only in isolated browser contexts. The
reviewer's own preview ran on port 4188 and was stopped after review.

Scope: `apps/web/**` against `docs/spec/m1-editor-demo.md`. Static review covered
consent/storage, text handling, versions/stale results, IME, Accept/Undo/export,
worker lifecycle, disclosures, and control semantics. Browser checks used the
actual production app and compiled Worker/WASM supplied by the web implementer.
An older **real** captured worker result was replayed only to exercise the
stale-response guard; no replacement analyzer or fabricated result was used.

This is local review evidence, not CI, complete M1 verification, a security
audit, or accessibility certification. No screen-reader, physical-device,
manual zoom, mobile browser, reduced-motion, or forced-colors check was run in
this review. Those remain unobserved here. The web implementer owns broader
acceptance tests. Findings below describe the build before the implementer's
subsequent fixes; this review does not claim those fixes were retested.

## Tools and launch

- Node v24.19.0; pnpm 12.14.0.
- System Chromium 154.0.8037.57, Debian GNU/Linux 13 (trixie), headless.
- Playwright 1.64.1; `@axe-core/playwright` 4.14.0.
- Desktop viewport 1280×900; narrow viewport 320×900.

Preview command, from `/workspace/Codex/NexWrite`:

```sh
source /workspace/.toolchains/nexwrite/activate.sh
pnpm --filter @nexwrite/web exec vite preview --host 127.0.0.1 --port 4188 --strictPort
```

Observed: `Local: http://127.0.0.1:4188/`. Review-owned exec session: 62681;
preview node PID 79231, parent pnpm PID 79213. No app build or install command
was run by this reviewer.

Version commands were `node --version`, `pnpm --version`, and
`/usr/bin/chromium --version` after sourcing the activation script. Package
versions were read from their installed `node_modules/*/package.json` files.
An initial `require('@axe-core/playwright/package.json')` version probe failed
because that subpath is not exported; direct file reading succeeded. This was
tool metadata lookup, not an application failure.

## Actionable findings reported to root

1. **False successful status before each new analysis.** In the reviewed
   `App.tsx` at lines 201–202, a missing `currentResult` falls through to
   `No spacing suggestions` while `phase` still equals `ready`. `commit`
   clears the result before the analysis effect sets `analyzing`. The actual
   live-region mutation sequence after edit, Accept, and Undo each contained
   `No spacing suggestions` → `Checking the current source…` → the real result.
   Gate successful no-suggestion wording on a current response; pending work
   must remain a pending status. Reported to root with exact observed sequence.
2. **Serious Axe accessible-name mismatch on the wordmark.** The desktop
   `.wordmark` displays `NexWrite` plus `LOCAL DEMO`, but its `aria-label` was
   `NexWrite home` (reviewed `App.tsx` line 207). Axe returned
   `label-content-name-mismatch`, impact `serious`, targeting `.wordmark`.
   Remove the overriding name or include all visible label text. This was
   the only Axe violation in the reviewed desktop state. Reported to root.

The root confirmed the app owner is fixing both findings. No additional
actionable defects were established by this review.

## Exact browser commands and results

Each block below ran from `/workspace/Codex/NexWrite/apps/web` after
`source /workspace/.toolchains/nexwrite/activate.sh` and exited 0.

### Source safety, local requests, 320 px, status mutations

```sh
node --input-type=module <<'NODE'
import { chromium } from 'playwright';
const browser = await chromium.launch({executablePath:'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
const context = await browser.newContext({viewport:{width:320,height:900}});
const page = await context.newPage();
const requests=[]; page.on('request',request=>requests.push({url:request.url(),method:request.method()}));
await page.goto('http://127.0.0.1:4188');
await page.getByTestId('analysis-status').filter({hasText:'Ready when you are'}).waitFor();
await page.evaluate(()=>{window.reviewStatuses=[];new MutationObserver(()=>window.reviewStatuses.push(document.querySelector('[data-testid="analysis-status"]').textContent)).observe(document.querySelector('[data-testid="analysis-status"]'),{subtree:true,characterData:true,childList:true});});
await page.getByLabel('Source editor',{exact:true}).fill('😀 One  thought.\nשלום  עולם');
await page.getByRole('button',{name:'Accept suggestion'}).first().waitFor();
const before=await page.getByLabel('Source editor',{exact:true}).inputValue();
await page.getByRole('button',{name:'Accept suggestion'}).first().click();
const accepted=await page.getByLabel('Source editor',{exact:true}).inputValue();
await page.getByRole('button',{name:'Undo accept'}).click();
const undone=await page.getByLabel('Source editor',{exact:true}).inputValue();
const result={viewportOverflow:await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),storageKeys:await page.evaluate(()=>Object.keys(localStorage)),acceptedOnlyOneSpace:accepted==='😀 One thought.\nשלום  עולם',undoExact:undone===before,statuses:await page.evaluate(()=>window.reviewStatuses),externalRequests:requests.filter(r=>!r.url.startsWith('http://127.0.0.1:4188/'))};
console.log(JSON.stringify(result));
await browser.close();
NODE
```

Actual output:

```json
{"viewportOverflow":false,"storageKeys":[],"acceptedOnlyOneSpace":true,"undoExact":true,"statuses":["No spacing suggestions","Checking the current source…","2 spacing suggestions","No spacing suggestions","Checking the current source…","1 spacing suggestion","No spacing suggestions","Checking the current source…"],"externalRequests":[]}
```

### IME, stale real worker response, actual Axe scan

```sh
node --input-type=module <<'NODE'
import { chromium } from 'playwright';
import AxeBuilder from '@axe-core/playwright';
const browser=await chromium.launch({executablePath:'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
const context=await browser.newContext({viewport:{width:1280,height:900}});
await context.addInitScript(()=>{window.reviewRequests=[];window.reviewResponses=[];window.reviewWorkers=[];const RealWorker=Worker;window.Worker=class extends RealWorker{constructor(...args){super(...args);window.reviewWorkers.push(this);this.addEventListener('message',event=>window.reviewResponses.push(event.data));}postMessage(request,...rest){window.reviewRequests.push({version:request.doc_version,format:request.input_format});return super.postMessage(request,...rest);}};});
const page=await context.newPage();await page.goto('http://127.0.0.1:4188');await page.getByTestId('analysis-status').filter({hasText:'Ready when you are'}).waitFor();
await page.waitForTimeout(300);
const before=await page.evaluate(()=>window.reviewRequests.length);
await page.getByLabel('Source editor',{exact:true}).dispatchEvent('compositionstart');
await page.getByLabel('Source editor',{exact:true}).fill('日本語  draft');
await page.waitForTimeout(400);
const during=await page.evaluate(()=>window.reviewRequests.length);
await page.getByLabel('Source editor',{exact:true}).dispatchEvent('compositionend');
await page.getByRole('button',{name:'Accept suggestion'}).waitFor();
const after=await page.evaluate(()=>window.reviewRequests.length);
const version=await page.getByTestId('metrics').getAttribute('data-version');
await page.getByLabel('Source editor',{exact:true}).fill('New current source without a gap.');
await page.evaluate(()=>{const old=window.reviewResponses.findLast(message=>message.type==='result');window.reviewWorkers.at(-1).dispatchEvent(new MessageEvent('message',{data:old}));});
const staleActionCount=await page.getByRole('button',{name:'Accept suggestion'}).count();
await page.waitForFunction(()=>document.querySelector('[data-testid="metrics"]').dataset.version && document.querySelector('[data-metric="word_count"]').dataset.value==='6');
const newVersion=await page.getByTestId('metrics').getAttribute('data-version');
const accessibility=await new AxeBuilder({page}).analyze();
console.log(JSON.stringify({compositionRequests:{before,during,after,once:after===before+1},staleActionCount,oldVersion:version,newVersion,axeViolations:accessibility.violations.map(v=>({id:v.id,impact:v.impact,nodes:v.nodes.map(n=>n.target)}))}));
await browser.close();
NODE
```

Actual output:

```json
{"compositionRequests":{"before":1,"during":1,"after":2,"once":true},"staleActionCount":0,"oldVersion":"1","newVersion":"2","axeViolations":[{"id":"label-content-name-mismatch","impact":"serious","nodes":[[".wordmark"]]}]}
```

### Consent, reload/opt-out, clear-owned-only, export bytes, denied reads

```sh
node --input-type=module <<'NODE'
import { chromium } from 'playwright';
import { readFile } from 'node:fs/promises';
const browser=await chromium.launch({executablePath:'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
const context=await browser.newContext({acceptDownloads:true});const page=await context.newPage();
await page.goto('http://127.0.0.1:4188');await page.getByTestId('analysis-status').filter({hasText:'Ready when you are'}).waitFor();
const source='😀 private  draft\nשלום עולם';await page.getByLabel('Source editor',{exact:true}).fill(source);await page.waitForTimeout(400);
const before=await page.evaluate(()=>Object.keys(localStorage));await page.getByLabel('Save draft in this browser').check();await page.getByTestId('storage-state').filter({hasText:'Saved locally'}).waitFor();await page.reload();await page.getByLabel('Source editor',{exact:true}).filter({hasText:''}).waitFor();const restored=await page.getByLabel('Source editor',{exact:true}).inputValue();
await page.getByLabel('Save draft in this browser').uncheck();await page.reload();const optedOut=await page.getByLabel('Source editor',{exact:true}).inputValue();
await page.getByLabel('Source editor',{exact:true}).fill(source);await page.evaluate(()=>localStorage.setItem('review.unrelated','keep'));await page.getByLabel('Save draft in this browser').check();await page.getByTestId('storage-state').filter({hasText:'Saved locally'}).waitFor();await page.getByRole('button',{name:'Clear local NexWrite data'}).click();const clearState=await page.evaluate(()=>({keys:Object.keys(localStorage),text:document.querySelector('#source').value}));
const downloadPromise=page.waitForEvent('download');await page.getByRole('button',{name:'Export source'}).click();const download=await downloadPromise;const bytes=await readFile(await download.path());
const deniedContext=await browser.newContext();await deniedContext.addInitScript(()=>{Storage.prototype.getItem=function(){throw new DOMException('Denied','SecurityError')};});const denied=await deniedContext.newPage();await denied.goto('http://127.0.0.1:4188');const deniedState=await denied.getByTestId('storage-state').textContent();
console.log(JSON.stringify({freshStorageEmpty:before.length===0,restoreExact:restored===source,optOutReloadEmpty:optedOut==='',clearPreservesSource:clearState.text===source,clearLeavesOnlyUnrelated:clearState.keys.join(',')==='review.unrelated',exportExact:bytes.equals(Buffer.from(source)),exportFilename:download.suggestedFilename(),deniedState}));await browser.close();
NODE
```

Actual output:

```json
{"freshStorageEmpty":true,"restoreExact":true,"optOutReloadEmpty":true,"clearPreservesSource":true,"clearLeavesOnlyUnrelated":true,"exportExact":true,"exportFilename":"nexwrite-draft.txt","deniedState":"Local restore unavailable"}
```
