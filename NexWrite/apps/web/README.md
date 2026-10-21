<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# NexWrite browser preview

The responsive app offers Library, Write, Assist and Settings. Its original
rounded document cards and navigation follow the application specification in
`../../docs/spec/owned-android-ai-release.md`; no reference branding or assets
are included. The source editor remains plain text / Markdown source, with the
real Rust WASM engine in a dedicated Worker and one implemented spacing rule.
Readability values retain their coverage and uncertainty disclosures.

Save writes a document to IndexedDB in this browser profile. A new draft stays
in memory until Save, or the separate optional draft-persistence setting is
chosen. Saved records support search, rename, duplicate and confirmed deletion.
Storage failures keep memory editing available and never report a failed write
as saved. Import accepts strict UTF-8 up to 1 MiB and adopts browser LF endings;
export preserves that source. The saved library is not an encrypted vault.
Cross-tab document conflict resolution is not implemented in this preview.

AI starts disconnected. Settings accepts a backend HTTPS URL and an optional
access token; only the address is saved, while the token stays in memory. Connect
fetches capabilities, without sending document text. Each AI request requires
its own source/destination preview and consent. Requests use the implemented
backend protocol, a 30-second client timeout, and cancellation. Results are plain
text proposals, guarded by document id, version, exact source/span and requested
text before Apply. Apply is one undoable edit with visible request/provider/model
provenance. The 20-snapshot Accept/AI Undo history is visit-local; typing clears
it. Provider configuration belongs on the server. No provider key is bundled.

The production build generates a service worker from the exact build inventory.
Only same-origin immutable application files, fonts, WASM and notices are
precached. It never intercepts API routes, non-GET requests, authenticated
requests, query-bearing requests or other origins. Offline reload is available
after the first successful full visit, while local documents remain in IndexedDB.
No background sync, upload, telemetry or automatic AI request is implemented.
Serve the build at the origin root; subpath hosting is not configured.

From the monorepo root, with the pinned toolchains installed:

```sh
pnpm --filter @nexwrite/protocol build
pnpm --filter @nexwrite/web build
PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/chromium pnpm --filter @nexwrite/web test:e2e
```

The browser suite checks the actual production build and WASM. AI browser cases
use explicitly labelled local protocol fixtures, not a live provider; backend
adapter tests and any live-provider evidence are separate. Offline caching has
its own real service-worker case. Automated Axe checks and synthetic composition
are not screen-reader, physical-device or cross-browser certification. Native
desktop, iOS, extensions and office integrations remain separate unreleased work.
