<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# NexWrite build status — 2026-10-21

## Current owned application preview

The user's corrected request is implemented as owned NexWrite source: native
Android app/keyboard/PROCESS_TEXT, original card/navigation frontend, responsive
web library/editor/Assist/Settings, and an optional stateless personal AI gateway.
The user explicitly chose to configure a backend later. Cloud AI starts inactive;
no public endpoint, live-provider pass, account system or cloud document store
is claimed. Native desktop/iOS/extensions and the complete product roadmap remain
incomplete. This preview is not an upstream PDF application.

Observed local validation before remote publication:

- 54 Rust workspace tests, strict bridge boundary/parity and an actual host JVM
  `-Xcheck:jni` smoke pass. All three Android ABI libraries build; arm64 ELF LOAD
  alignment is 16 KiB. Native cancellation discards stale delivery but cannot
  interrupt an already-running Rust call.
- All 92 Node tests and project generation/ledger/provenance/license gates pass.
  The Node count includes eight actual backend groups and five notice fixtures.
- The production browser build and all 26 real Chromium cases pass; four tested
  axe states report zero violations. AI provider responses in browser/backend
  protocol tests are labelled simulations, never a live-provider quality pass.
- A real backend Docker image built and ran as nonroot with a read-only root
  filesystem, all capabilities dropped and bounded temporary storage. Actual
  HTTP analysis matched the real Rust CLI on a Unicode/Markdown fixture; auth,
  capability discovery and unconfigured-provider states passed. Image ID:
  `sha256:13f886cd6d58aec3c8acd8ffc114fcf9c6dbbe1f5d7dcc05bf2678255c4c5d26`.
- The supplied Apktool source built with its actual version/help checks and
  173 upstream library tests passing. It decoded and rebuilt an unsigned
  inspection copy of the owned signed APK while preserving all dex/native bytes.
- Android release build, lint and seven JVM tests pass. The signed universal APK
  is package `app.nexwrite.android`, version `0.2.0-preview.1`, minSdk 26 and
  targetSdk 36, with SHA-256
  `6302b4e4ee4c8ffc317554e49bf23ca09467f4ee9be60b1daa2d3bdb398fc591`.
  APK Signature Schemes v2/v3 verify and 16 KiB ZIP alignment verifies. REA/JADX
  confirms the declarations and only `INTERNET` plus Android's generated
  signature permission. Physical Android/OEM/assistive-technology/low-RAM
  evidence remains open.

The ledger now has 149 rows: 19 in-progress, 130 planned and zero verified.
Twelve additional partial rows cover the personal AI/document/native surfaces.
Current unknowns are UNK-APP-01–05 plus the retained baseline obligations. The
source review/build units are specs `583327b`, API contract `c93d6ae`, JNI bridge
`9e3e8c5`, personal API `26793af`, web `d3d45a4` and native app `b3e97ff`.

Publication: baseline source branch was pushed successfully. The baseline CI
run https://github.com/harshsinghal10h/Codex/actions/runs/39550056484 passed four
jobs and failed browser/macOS jobs; log/artifact redirects were denied by the
managed network. Improved failure annotations and release CI are configured.
Final source branch/PR, CI run and the owned signed APK link are pending
publication. No CI or device pass is inferred from these local results.

## Historical bounded M1 baseline

The bounded M1 source-analysis slice and local browser demo are implemented and
tested on this Linux x86_64 cloud host. The app uses the real Rust/WASM engine
in a Worker for one original double-space rule, Unicode counts and labelled
readability estimates. Full M1, including 300 rules and a dictionary, and the
remaining M2–M11 product/platform roadmap are incomplete. Nothing is CI-verified.

## Scope and changes

- Eight Rust crates now include `nw-structure` and `nw-metrics`. Additive
  `Engine::analyze`, `nw analyze`, `nw schema analysis` and `analyze_json`
  preserve legacy `check` behavior and original UTF-8/UTF-16/scalar spans.
- Plain/Markdown analysis has a 1,048,576 UTF-8 byte cap. Markdown exclusions
  are conservative source fragments; protected content suppresses English
  readability formulas. Short, non-ASCII and numeric inputs have explicit gates.
  The ASCII syllable heuristic is uncalibrated and is not language detection.
- The browser textarea supports version-checked Accept, up to 20 accepted-edit
  Undo snapshots, exact adopted-LF export, IME suppression, real Worker pause
  and retry, current-source metrics, and dark/light local fonts. It renders at
  most 20 suggestion cards while showing the actual total.
- Drafts start in memory. Source persistence requires opt-in; opt-out and Clear
  remove only owned storage keys while retaining the current editor text.
- The shared AnalysisResult schema reuses canonical Suggestion/Span types;
  Rust, TypeScript, Kotlin and Swift bindings are generated. Only Rust and
  TypeScript compilation was executed. Runtime dependency notices accompany the
  built web app; exact reviewed MPL allowances apply only to development tools.
- Web builds always execute the engine helper, pass required toolchain/proxy/CA
  settings through Turbo, and build protocol prerequisites. The standalone web
  recipe passed with its generated protocol declarations initially absent.

Task 0 remains recorded at `874efaa`, with original logs and hashes under the
2026-10-19 evidence prefix. The separate M1 analyst and shared-contract commits
are `da4bfc4` and `92a8d81`; the L1 implementation is `7d66741`, followed by
the L3 editor review unit.
VLC-Glass and OpenSuite-One are preserved.

## Ledger delta

COR-10, CLA-04, COR-11, INS-02, DOC-01 and DOC-06 move `spec` → `in-progress`;
COR-06 remains `in-progress`. The 149-row ledger has 7 in-progress rows,
142 planned rows and 0 verified rows. These are partial feature slices, not
completion of rich editing, complete structured-text support or platform parity.

## Executed evidence

EV-20261021-001 indexes `docs/ledger/evidence/2026-10-21-m1-validation-summary.json`:
all final command groups exited 0 after the recorded build remediation.

```sh
just check
just test
just build core
just build wasm
pnpm build
pnpm --filter @nexwrite/protocol check
PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/chromium just browser-test
just compose-check
just secrets
just supply-chain
just bench-analysis
```

The retained-tool preparation and standalone `just build web` checks also passed.
`just test` ran 70 Node tests and 48 native tests. Actual compiled WASM agreed
with native reports for 23 new analysis cases plus the unchanged 7 legacy cases;
30 new malformed-analysis inputs and legacy malformed boundaries were rejected.
Locale errors and the maximum safe document version remain covered.

EV-20261021-002 records 18 real Chromium production-build cases, including native
parity, independent formulas, stale-response rejection, IME, Accept/Undo/export,
restored CRLF/CR adoption to LF, storage consent/failures, input caps, Markdown,
dense cards, Worker failure/recovery, keyboard, 320 px, reduced motion and forced
colors. Three tested desktop/mobile theme states had zero Axe violations. This
is automated local coverage, not WCAG certification or a manual screen-reader audit.

EV-20261021-003 records 400 seeded original Unicode/Markdown cases with source
coordinate/containment checks, definition/HTML regressions and 11 bounded stress
probes. Review fixes cover multiline reference definitions, ambiguous HTML,
quadratic suffix scanning, strict schema generation and truthful browser states.
Initial failures and pre-fix reviews are retained separately, not counted as passes.

License/advisory/source gates pass and npm audit reported no known vulnerabilities
at this run. Gitleaks found no source leaks. Seven optional service configurations
passed validation; no backend container was started. CI jobs are configured but
have not run. EV-20261021-010 records REA 6.4.0 own-artifact static analysis:
Node application inventory is complete but retains 231 semantic unknowns; web
semantic coverage remains partial with 365 unknowns after exhausting both 1 GiB
and 4 GiB analyzer heaps. Zero parse failures does not establish comprehensive
coverage. Exact commands, hashes and limits are retained in the audit record.

## Artifacts and measured limits

`2026-10-21-m1-artifacts.json` inventories the current ignored Node binding and
web distribution. Both contain WASM SHA-256
`f5142a63ece63ac0605f57122d5afbd6cc634aed1d466a77604f2217d2b5eb15`.
Node default gzip measures 668,154 bytes; deterministic Python level 9 measures
647,655 bytes. Both are below the 3 MiB artifact budget; compression methods
are different and neither establishes runtime performance.

The 50-iteration current-host analysis benchmark measured p95 0.918/5.249 ms
for plain 1k/5k-word fixtures and 2.018/5.078 ms for Markdown fixtures. It covers
this pipeline and one rule, excluding compilation, file I/O and engine startup;
it is not an incremental, mobile or mature-engine result. The separate dense
1 MiB stress fixture took 2.525 seconds and about 574 MiB peak native RSS including
process/file I/O/JSON serialization. Worker result expansion remains a material
memory limitation despite the input cap and bounded visible list (UNK-M1-04).

## Unknowns, risks and publication

UNK-M1-01..06 remain open: rendered/source parity and additional formats,
uncalibrated readability, abbreviation/dictionary segmentation, dense-report
memory, richer/offline/cross-tab/accessibility/browser coverage, and incomplete
web static semantic analysis. Kotlin/Swift
compilation, other OS runners, physical devices, manual screen readers/zoom,
backend readiness and the remaining integrations are unverified. Legal/name
clearance, current public terms, release signing/SBOM and attestations also remain
open. First-party licensing remains all rights reserved.

No branch was pushed and no PR or CI run was created. The existing GitHub API
probe returned `Forbidden`; the runtime remains on configuration revision 1.
Local review branches use `feat/l1/structured-analysis` and
`feat/l3/m1-analysis-demo`. Delivery patch/bundle and built-app instructions are
prepared under `/workspace/artifacts/`; consult their manifest for exact final
commit IDs, hashes and import commands rather than assuming remote publication.

The saved environment draft preserves VLC setup and includes the tested NexWrite
commands. Its narrow network additions are still unapplied. Review and save the
draft in environment settings, then publish to retain the prepared filesystem.
Fresh-task restoration, especially local-only commits, has not been verified;
retain the delivery bundle independently.

## Next five tasks

1. Apply the saved network additions and recheck the GitHub API and bounded
   public-terms lookup with existing platform authentication.
2. Publish the separate review units and inspect real three-OS/browser CI results
   before promoting any ledger row to verified.
3. Bound or stream dense result delivery and test memory/latency on lower-memory
   browsers before increasing document limits.
4. Expand the licensed dictionary/rule set with original examples, false-positive
   evaluation and segmentation/readability calibration toward the full M1 gates.
5. Resolve legal/release obligations and separately specify rich editing/offline
   behavior and broader assistive-technology/browser/device coverage.
