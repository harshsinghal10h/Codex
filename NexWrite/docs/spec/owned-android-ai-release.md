<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# NexWrite application and Android release contract

Evidence class: E4 operator instruction and E3 licensed-source reference.
Evidence references: docs/inputs/operator-brief.md; docs/MASTER_PROMPT.md;
the user's 2026-10-21 correction in this task; https://github.com/Chethan616/ClearPDF.
Reference date: 2026-10-21. Status: analyst specification, preceding builders.

## Intent, scope and authorization

The operator wants NexWrite, their full-stack all-OS AI writing assistant, with
a frontend informed by ClearPDF's visual organization, an owned Android APK,
use of the uploaded Apktool tooling, source pushed to their GitHub repository,
and a GitHub download link for that owned APK. Delivering an unchanged ClearPDF
APK does not implement this request. Product name defaults to NexWrite and the
selected existing repository remains harshsinghal10h/Codex.

This instruction authorizes implementation beyond the earlier bounded M1 demo.
It does not erase the clean-room, consent, provenance, signing or honest-evidence
requirements. The archived master prompt stays unchanged. Work remains split
into separately reviewable specification, contracts and lane implementation units.
The all-OS target remains the complete S7/S8/S14 roadmap, not a claim that an
Android preview or responsive web app completes all native integrations.

The first release must be a functioning owned vertical path: a document library,
editor, existing local analysis, explicit AI requests to an implemented backend,
proposed results with safe Apply/Undo, native Android entry points and a signed
APK. Backend configuration/credentials and unpublished platforms must be visible
capabilities or unknowns. Never substitute fabricated AI output or another
project's package for a missing implementation. A preview release is permissible
only when its exact implemented scope is disclosed; it is not the full milestone
exit or v1.0 completeness claim.

## Original visual and interaction direction

Use an original NexWrite interface with the existing obsidian/paper palettes,
green accent, self-hosted typefaces and token system. The licensed reference
informs grouped rounded cards, calm surfaces, concise action rows, restrained
translucency and mobile bottom navigation. No reference logo, PDF screens,
screenshots, app identity, illustrations or UI text become product assets.
Retain solid-surface fallbacks for blur, forced colors and reduced motion.

Small screens have four destinations: Library, Write, Assist and Settings.
Wide screens adapt them to a navigation rail and a readable content column.
Every visible destination and action performs its named behavior. Account,
billing, team and platform controls appear only when backed by real capabilities;
otherwise the download/compatibility screen states their implementation status.

- Library: saved local documents, title/search, updated time, New document,
  Rename, Duplicate, Delete with confirmation and explicit local-storage status.
- Write: editable title, plain/Markdown source, local suggestions, current-source
  metrics, goals where supported, Save, import/export and version-safe Undo.
  The minimum source editor must be labelled accurately; it is not a rich editor
  until rich-document semantics are implemented and tested.
- Assist: Rewrite, Summarize and Compose; explicit source/selection preview,
  provider/model destination, consent, Run/Cancel, proposed result and Apply.
  Disabled setup state explains configuration needed without exposing internals
  such as raw stack traces, environment variable names or credentials.
- Settings: appearance, local data/export/clear, storage/autosave choice, backend
  address/access-token configuration, cloud consent and keyboard setup on Android.

Required states include loading, empty, ready, saving/saved, dirty, offline,
analysis unavailable, invalid input, no suggestions, provider unavailable,
rate-limited, canceled, failed and permission denied. A success message requires
the corresponding current operation to have succeeded. Existing keyboard,
composition, RTL, emoji, reduced-motion and forced-colors guarantees remain.

## Document model and storage boundary

A local document contains id (UUID), title, source, input_format (plain/markdown),
locale (en-US initially), doc_version (portable nonnegative safe integer),
created_at/updated_at (UTC RFC3339), and schema_version (1). Title is 1–120 Unicode
scalars after outer-whitespace trimming; empty titles display an original default
name chosen at explicit save time. Source is never normalized by the core.
Native textarea clients adopt their actual LF representation before versioning.

Android uses app-private Room storage, settings in DataStore, and protected
credentials through Android Keystore. Web uses IndexedDB for explicitly saved
documents and memory-only state before Save or an explicit autosave opt-in.
Unsupported/private/denied/quota storage must leave editing available in memory.
Do not call a failed write saved. An explicit Save authorizes that local write;
it does not authorize upload, cloud sync or training. Autosave is a separate
setting, initially off, with a visible destination. Delete removes the selected
owned record; clear-data removes owned application keys without touching others.

All edits, accepted suggestions, applied AI results, imports and restored
versions monotonically advance doc_version. Analysis/AI results retain the
snapshot version and are discarded when it differs. Duplicates receive a new id.
History is bounded and documents its bound; restoration creates a new version,
never reuses an old version stamp. Existing M1 draft import is allowed only with
its recorded consent and strict validation, and must not silently upload it.

Cloud documents remain a separate opt-in domain. When enabled, the modular API
uses authenticated user/org ownership, Postgres 16 and reviewed migrations.
Every read/write/delete/version/export is tenant-scoped. Expected-version
conflicts return 409 and preserve both source copies for user resolution. A
shared unauthenticated server directory is not cloud document sync. Keep its UI
unavailable until actual authz/storage/delete/restore tests pass.

## Local analysis contract

The existing canonical Suggestion, Span and AnalysisResult schemas remain the
source of truth and are not duplicated by clients. Inputs remain exact original
source with all three UTF-8/UTF-16/scalar coordinates. Initial capability is
en-US/plain/Markdown and the real implemented rule set; discover or declare the
actual rule count. Never advertise the planned 300 rules as implemented.

Local analysis never needs accounts, network, provider credentials or cloud
consent. Existing 1,048,576 UTF-8 byte cap, strict surrogate rejection, protected
Markdown projection and metric omission/warning gates remain. Analysis work is
cancellable; no stale response can supply a clean state or actionable correction.
The UI renders bounded cards and truthful totals, retaining the dense-result
memory unknown until output delivery is bounded across the entire pipeline.

Android consumes the same Rust core through a versioned bridge. The preferred
target is generated UniFFI; a reviewed JNI JSON adapter may provide a smaller
first bridge while retaining canonical serialization and parity fixtures. Native
editor UI is Kotlin/Compose. Any different engine host requires an explicit ADR,
matching parity/negative tests and a capability statement; a duplicated
approximation is not silently presented as the canonical engine.

## Backend and consumer API contract

The TypeScript modular monolith and stateless Rust analysis boundary follow
ADR-0009. OpenAPI 3.1 defines HTTP request/response validation before consumers
are implemented. Generate/reconcile TS/Kotlin/Swift clients; the table below is
the minimum new cross-lane contract, not an implementation or OpenAPI substitute.
Objects reject unknown fields. JSON numbers must be finite. Strings reject lone
surrogates before UTF-8 encoding. Error bodies use application/problem+json.

| Route | Request | Success behavior |
| --- | --- | --- |
| GET /health | none | 200 with status=ok and version only; never credentials or document content |
| GET /v1/capabilities | none | protocol_version=1; exact supported locales/formats/tasks; actual engine rule count; provider profiles with ids, labels, configured flag and allowed model ids; accounts/cloud_docs capability flags |
| POST /v1/analyze | text, doc_version, locale, input_format | 200 canonical AnalysisResult; invokes the real Rust engine, never a fabricated report |
| POST /v1/rewrite | text, doc_version, locale, task, preset, provider, model, cloud_consent | 200 AiResult as defined below; invokes only a configured provider after validation/consent |

Analyze request text cap is 1 MiB UTF-8. Limit HTTP JSON body separately to at
most 2 MiB and reject excess before processing. Do not interpolate source into
shell commands, filenames or logs. A subprocess/native boundary uses bounded
input/output, isolated per request and no shared document temporary filename.
Backend analysis is an explicit opt-in destination when a local core is available;
do not automatically upload text on local-engine failure.

Rewrite required fields: text (string), doc_version (same safe-integer bound),
locale=en-US, task enum rewrite/summarize/compose, preset enum proofread/clearer/
shorten/expand/formal/friendly/direct, provider (configured profile id), model
(allowed model id), cloud_consent=true. For summarize/compose preset remains a
required field but only clearer is supported initially; reject other combinations
with 422. Text is the actual selected source, or entire source only when the user
chooses it. Rewrite/summarize need nonblank text; compose treats text as the user's
brief. Cap input at 32,768 UTF-8 bytes and output at 65,536 UTF-8 bytes. Initial
providers are OpenAI-compatible and Gemini with independent real adapters.

AiResult requires request_id (UUID), doc_version, provider, model, task, preset,
original (exact request text), replacement (plain text string), warnings (string
array). No rendered HTML, executable action, tool call or client-side script is
accepted. The service validates output shape, UTF-16 and output cap. Never turn
an unavailable provider into success or silently fall back to a different provider.
Record actual provider/model in results and consent destination.

Provider keys and upstream base URLs are server configuration, never bundled
in an APK or accepted as arbitrary per-request upstream URLs. The configured
profiles map to reviewed destinations; remote clients cannot use this API as an
SSRF proxy to metadata/private hosts. Compatible local endpoints are explicit
self-host profiles with corresponding network/consent policy. Do not infer
that HTTPS or a provider label establishes retention, legal or regional clearance.

The API defaults to loopback. Binding beyond loopback requires configured client
authentication and fails closed otherwise. A stateless personal deployment may
use a runtime operator-issued access token scoped to analyze/rewrite, with
bounded per-token/IP limits; it must not expose cloud documents/admin actions.
The public multiuser profile requires implemented auth/session/tenant isolation
from S6 before accounts/cloud_docs capabilities can become true. Tokens never
ship in client binaries. Android protects a user-entered access token with
Keystore; browser retains it in memory unless a separate secure persistence
design and explicit consent are implemented. Remote release endpoints require
HTTPS; emulator-only cleartext exceptions belong only to debug configuration.

Error status mapping: 400 invalid input, 401 missing/invalid access token,
403 cloud consent absent, 409 expected document version conflict where relevant,
413 input limit, 422 unsupported locale/format/task/preset/model, 429 rate limit,
502 invalid/upstream error, 503 engine/provider not configured or unavailable,
504 timeout. Include stable error code, short safe title, request_id and optional
retry_after_seconds; exclude input text, provider payload, tokens and stack traces.
Network cancellation aborts downstream fetch/work and never returns a partial
replacement as complete. Start with a bounded 30-second provider timeout and
at most one schema-repair retry, counted in the same request/budget.

Documents are untrusted prompt data, delimited independently from fixed task
instructions. Providers have no tools, browsing, code execution or hidden access
to other documents. Do not place credentials in prompts. Prompt injection tests
check these boundaries; they do not establish universal injection immunity.

## AI result acceptance and provenance

The client records an immutable request snapshot: document id/version, provider,
model, task/preset, selected canonical span and exact original substring. Consent
is shown per request/destination and can be withdrawn by canceling. No automatic
cloud call runs on typing, paste, focus, keyboard enablement or engine failure.

A result is initially a proposal. Apply is enabled only when document id/version,
selection span and current substring still match and the result original equals
the requested text. Apply exactly one replacement as one undoable edit, increment
doc_version and tag the inserted change as AI generated with the actual request
id/provider/model. It does not add a detector score or imply factual correctness.
Cancel leaves source unchanged; stale result state offers rerun on the new source.

## Native Android behavior and package identity

Android app identity is owned NexWrite, e.g. namespace/application id
app.nexwrite.android (coordinator freezes exact id before signing); minSdk 26.
Retain the original app name/icon/legal assets and generated token theme.
No ClearPDF application id, signing identity or downloaded APK is renamed into
NexWrite. Default release is a sideload preview, not a Play-approved build.

- Main app: native Library/Write/Assist/Settings, responsive phone/tablet layout,
  app-private documents, import/export via Storage Access Framework and native
  keyboard setup intent. No broad file-storage permission is needed.
- PROCESS_TEXT: accept the documented Android text extra and read-only flag;
  show the owned editor and return a replacement only after explicit Apply when
  writable. Read-only selection supports preview/copy/export, never a false return.
- Share target: accept supported text/plain and text/markdown within the cap;
  malformed/unsupported extras fail safely. Receiving share content alone never
  saves or uploads it; Save/AI actions are explicit.
- IME: InputMethodService with original QWERTY layout, suggestion strip,
  composition-aware insertion/backspace/space/enter, and explicit Assist action.
  Password/secure/no-suggestions/private fields suppress analysis/learning/cloud
  as required by S8.4. Editable context bounds, source version and current text
  must be rechecked before any replacement. No clipboard history by default.
- Play and sideload permissions stay separated. Accessibility reading, overlay,
  always-on capture or SYSTEM_ALERT_WINDOW is not added just for appearance.
  A sideload accessibility adapter needs its own disclosure, consent, policy
  evidence and sensitive-field fixtures before its capability is enabled.

Any unimplemented IME/bridge/platform entry point remains visibly unimplemented
in release/compatibility records; a launcher-only APK is not M5 complete.

## Tooling and owned release chain

Use the supplied Apktool source as tooling/reference and establish a runnable,
pinned executable with supported checksums/provenance before claiming tool use.
Discover its actual help/version. Run it on the owned NexWrite APK to inspect
decoded manifest/resources and verify product identity, permissions, exported
components, resource/legal notices and absence of upstream PDF assets. Archive
exact command/version/results. Inspecting the uploaded archive alone is not an
APK audit. Source compilation remains the feature implementation path.

Build the owned signed APK with a persistent app-specific key kept outside Git.
Never reuse ClearPDF's identity or claim its publisher signature. Verify APK
signature, package id/version/minSdk/ABIs, ZIP integrity and alignment; retain
public signing-certificate fingerprint and SHA-256. Do not put APKs, keys, caches
or downloaded tool binaries into source commits. Default candidate naming is
NexWrite-0.2.0-preview.1-android-universal.apk, subject to root release coordination.

Publish source to a task branch in the selected repository; do not push main,
force-push or delete branches. Attach actual PRs to the task. Publish the owned
APK/checksums/notices/provenance as GitHub Release assets, preferably using a
content-write-scoped release workflow. Return an actual reachable owned release
download URL after asset publication succeeds. A guessed URL, workflow file,
upstream ClearPDF link or local filesystem link is not a published owned APK.
If publication is blocked, retain exact command/error, complete unblocked work,
and name the precise access/network change required without requesting tokens.

## All-OS continuation and evidence gates

The responsive frontend runs in tested browsers but is not evidence of native
Windows/macOS/Linux/iOS/extension/Office integrations. Keep separate compatibility
rows for web/PWA, Tauri hosts and desktop text adapters, browser extensions,
native Swift app/keyboard/share, Office/Google add-ins and LSP/SDK. Generated
projects or configured CI jobs are unverified until executed. Store accounts,
signing identities, device labs, real providers and public hosting are external
activation requirements, not excuses to substitute fake results or block writing
all otherwise implementable source/configuration.

Required release tests: canonical local/native/WASM/backend analysis parity;
strict malformed/size/surrogate/version fixtures; local library CRUD/search and
storage failures; offline editing; explicit cloud consent and no text in logs;
two adapter contract tests plus separately labelled live-provider evidence when
credentials are configured; cancel/timeout/rate-limit/upstream-invalid handling;
stale AI Apply/Undo and exact Unicode offsets; hostile document prompt fixtures;
tenant isolation for any cloud-doc profile; native PROCESS_TEXT/share/IME and
secure-field exclusion; keyboard navigation/TalkBack/font scaling/RTL/320px;
actual APK installation/launch on emulator and separately physical-device status;
signed artifact inspection through Apktool plus required own-artifact REA checks;
reachable GitHub asset and checksum/signature verification after download.

No new feature is verified from this document. Builders update exact ledger rows,
unknowns, evidence, handoffs and changelog only from observed results. Existing
M1 limits, full 300-rule/dictionary work, provider/legal/release obligations and
unexecuted platform/device matrices remain explicit until comparable evidence
closes them. No additional product-preference question is required to start:
NexWrite identity, existing repository, current tokens, local-first behavior and
runtime-configured provider adapters are supported by the present instructions.
