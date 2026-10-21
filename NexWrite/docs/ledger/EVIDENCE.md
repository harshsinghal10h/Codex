<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# Evidence index and reporting contract

## Owned application preview (2026-10-21)

These are observed local results, separate from immutable release APK and CI
results attached to the owned GitHub release. No row is promoted to verified.

| ID | Kind | Record and bounded result |
| --- | --- | --- |
| EV-20261021-APP-01 | local | evidence/2026-10-21-owned-project-check.log: canonical/API/token generation, ledger, provenance and declared license gates pass |
| EV-20261021-APP-02 | local | evidence/2026-10-21-owned-node-tests.log: 92 tests pass, 0 fail/skip; includes 8 service and 5 notice groups |
| EV-20261021-APP-03 | local | evidence/2026-10-21-owned-rust-tests.log: 54 Rust tests pass; host JVM bridge smoke is recorded by the release coordinator separately |
| EV-20261021-APP-04 | local | evidence/2026-10-21-owned-browser.json: 26 actual production Chromium cases pass; 4 axe states have zero violations. AI fixtures are protocol simulations |
| EV-20261021-APP-05 | local | evidence/2026-10-21-owned-api-tests.log and owned-api-container.json: actual service tests/Rust parity, real restricted container/auth/unconfigured-provider states pass; no public hosting/live-provider evidence |
| EV-20261021-APP-06 | local | evidence/2026-10-21-owned-apktool-build.json: uploaded Apache-2.0 source built, version/help and 173 library tests pass; own APK inspection is separate |
| EV-20261021-APP-07 | local artifact | evidence/2026-10-21-owned-android-release-local.log: release build, lint and 7 JVM tests pass. The signed APK SHA-256 is `6302b4e4ee4c8ffc317554e49bf23ca09467f4ee9be60b1daa2d3bdb398fc591`; it verifies with APK Signature Schemes v2 and v3 and has a 16 KiB-aligned ZIP layout. No emulator or physical-device pass is asserted. |
| EV-20261021-APP-08 | local own-artifact audit | evidence/2026-10-21-owned-android-apktool-audit.json records uploaded-source Apktool decoding/rebuilding an unsigned inspection copy while preserving dex/native bytes; evidence/2026-10-21-owned-android-rea-audit.json records JADX/REA manifest inspection. Both confirm `app.nexwrite.android`, minSdk 26, targetSdk 36, and only `INTERNET` plus Android's generated signature permission. They are static inspection, not a comprehensive security, privacy, or device test. |

Baseline CI https://github.com/harshsinghal10h/Codex/actions/runs/39550056484
passed Linux, Windows, WASM and supply-chain jobs and failed browser/macOS jobs.
Managed-policy log/artifact redirect denials prevented reading their full logs.
New workflows expose bounded failure annotations and add actual Android emulator
tests. Use the actual release run's conclusions, never substitute these local
records or configured test sources for a CI/device pass.

IDs have the stable form `EV-YYYYMMDD-NNN` and are never reused. Each record states observation, derivation, inference or unknown; source kind, exact command/artifact hash, date, environment/tool versions, exit status, result and limits. Preserve meaningful outputs under evidence/. Text fixtures are synthetic/original; never store user document content or credentials. Record real failures. Truncated, skipped and simulated output remains visibly limited.

## Kinds and verification

- `local`: actual local command execution. A pass supports only that command on that machine; it is never CI evidence.
- `ci`: a trusted GitHub Actions run and required-case outcomes. A run URL is necessary but inspect its actual result; a local JSON file is only the index to that run.
- `static`: source/configuration observation; cannot close runtime obligations.
- `unknown`: unavailable or unexecuted evidence, with exact remedy.

Ledger `ci_evidence` uses semicolon-separated repository JSON paths under docs/ledger/evidence. Each CI JSON record must contain, for example:

```json
{
  "id": "EV-20261019-001",
  "kind": "ci",
  "feature_id": "COR-06",
  "run_url": "https://github.com/OWNER/REPO/actions/runs/123456789",
  "cases": {"positive": "pass", "negative": "pass", "malformed": "pass"}
}
```

This is a schema example, **not an actual run**. The record must match a genuine run, every required ledger case must be `pass`, and no unresolved unknown may remain before status `verified`. `required_cases` uses pipe separators; `unknown_ids` uses semicolons. Evidence classes E1–E5 identify provenance, while local/CI/static identify verification kind. Initial feature rows cite E4 operator requirements through docs/inputs/operator-brief.md. Platform IDs use PLT- plus the exact S7 label.

## Bootstrap observations

| Record | Kind | Finding / limits |
| --- | --- | --- |
| docs/inputs/operator-brief.md | static E4 | Full master prompt hash and three upload identifiers; establishes requested scope, not external parity |
| docs/legal/CLEAN_ROOM.md | observation/unknown | 2026-10-19 official robots proxy CONNECT returned HTTP403/curl56; no terms content retrieved; UNK-BOOT-02 remains open |
| docs/spec/bootstrap-core.md | static E4 | Original behavior spec published to the builder before rule implementation; no runtime proof |

Actual toolchain, native/CLI/WASM, lint and audit outputs are added by the task that executes them under evidence/ and referenced from the handoff. This initial index does not fabricate run IDs or duplicate local results as CI. No ledger row is currently CI-verified. Before closing Task 0, reconcile all emitted evidence files here or in the handoff's command/result index.

## Own-artifact audit checklist

Discover actual REA command syntax from installed help. Capture version/help, input hash, exact invocation, tool exit status, output/limitations and manual review. Audit only own generated artifacts. Check declarations/permissions, maps/secrets, endpoints, dependencies/licenses, size and entry points. Static inventory is not runtime network/privacy verification. No third-party target is authorized by default; see AUTHORIZATION.md.

## Stable bootstrap evidence IDs

| ID | Kind / classification | Actual record and bounded result |
| --- | --- | --- |
| EV-20261019-002 | local / observation | evidence/2026-10-19-design-tokens.txt: 46 tests passed, no failures/skips; specified token contrasts and non-color category/font configuration only, not a screen-level WCAG audit |
| EV-20261019-090 | local / observation | Documentation audit executed on 2026-10-19: CSV has 149 unique rows, exactly one in-progress row and zero verified; 10 ADRs each have context/options (>=3)/decision/consequences/revisit; AGENTS is 58 lines at the final documentation audit; archived prompt SHA256 matches the operator-input record. Exit 0. All 40 post-remediation registry dependencies match the declared inventory; superseded unsafe-libyaml/serde_yaml_ng table entries are absent. Separate node ledger-lint and provenance-lint commands also exited 0 |
| EV-20261019-091 | local / unknown | Official robots request through inherited proxy exited 56 with HTTP403 CONNECT denial (2026-10-19 10:41 UTC). No robots/terms body obtained. CLEAN_ROOM.md retains command/result/remedy; no current terms quotation is asserted |

EV-20261019-001 indexes the coordinator's final integrated local validation log/summary. Tool-native REA `ev_...` values are retained as tool identifiers and must not be confused with a CI run. Only actual completed command results establish a local pass.

## Core and own-artifact local evidence

| ID | Kind / classification | Record and bounded result |
| --- | --- | --- |
| EV-20261019-003 | local / observation | evidence/2026-10-19-core-tests.log: 14 native tests passed (CLI 3, engine 3, rules 3, schema 2, text 3); evidence/2026-10-19-core-checks.log records clean formatting/Clippy and generation checks |
| EV-20261019-004 | local / observation | evidence/2026-10-19-core-wasm.json: actual compiled WASM in Node agrees with native output for 7 cases; rejects 13 invalid versions and 7 invalid text values, rejects invalid locale, preserves maximum safe version; Node default gzip is 541,253 bytes under the 3 MiB artifact budget. No browser/device/latency claim |
| EV-20261019-005 | local / observation | evidence/2026-10-19-core-checks.log: 2 contract/generator tests passed, covering 38 malformed wire variants and 5 unsupported schema mutations; generated TypeScript compiles locally. Kotlin/Swift compilation and native bridges remain unknown |
| EV-20261019-006 | local / observation | evidence/2026-10-19-core-benchmark.json: current Linux x64 full-check p95 is 0.395080 ms for 1k words, 7.385344 ms for 5k words over 100 iterations. Single spacing rule only; excludes compilation/file I/O, measured during concurrent release compilation; no incremental/mobile/performance-parity claim |
| EV-20261019-010 | local static / observation, derivation and inference | evidence/2026-10-19-rea-wasm-node-audit.md and .json: final safe-parser own Node bridge artifact audited by REA 6.4.0; 3 JS/TS files parsed, zero parse failures, 199 semantic unknowns. Final invocation/hash inventory matches the actual core WASM. Static analysis does not prove runtime privacy, native/WASM semantics or platform integration |

The final safe-parser build's JavaScript SHA-256 is `4e41048233023957199af0e5d467d243696f68b45269a8561d9bae7bd4eca978`; WASM SHA-256 is `48b4bddac19a7ef7d4b8986056b109b35cf8cab75718cfa40725cb5592d49300`. Final REA parent ID is `ev_d98118749b6c2c558c515e9ea1ba3ad7d93243754e91f151854c56965ec867fd`; artifact-tree SHA-256 is `3579a1a426b4489b4d3fddc91825b0ad9450683104d61f8f22fa3f661b3f1123`. Node default gzip measures 541,253 bytes; deterministic Python level 9 measures 525,052 bytes on the same WASM hash. Different installed zlib implementations explain the method-specific sizes; both are below the 3 MiB artifact budget. Neither measurement establishes latency, memory or target-platform behavior.

### Supply-chain remediation and artifact succession

The initial cargo-deny gate rejected unsafe-libyaml through serde_yaml_ng and internal wildcard path versions. The builder migrated to serde-saphyr 2.0.0 with deserialize-only features and granit-parser 2.0.0, and pinned internal requirements. Fourteen native tests, formatting, Clippy and cargo-deny then passed locally. No advisory suppressions or trust bypasses were added. The rebuilt Node WASM gzip is 541,253 bytes; the earlier 383,558-byte artifact is superseded. The final benchmark and REA invocation now reference the safe-parser build. Historical failures are retained separately below instead of aggregating them into a pass.

## Integrated checks and retained failures

| ID | Kind / classification | Record and interpretation |
| --- | --- | --- |
| EV-20261019-001 | local runtime / observation | evidence/2026-10-19-bootstrap-validation.txt and evidence/2026-10-19-bootstrap-validation-summary.json contain the final integrated command invocations, exit codes and durations. Read the actual summary for final pass/failure counts. These are local Linux results, never CI or three-OS verification |
| EV-20261019-007 | local runtime / failure, superseded | evidence/2026-10-19-bootstrap-initial-validation-summary.json retains the first integration pass: 8 commands passed, pnpm build failed with Exec format error while Turbo spawned pnpm. The coordinator repaired the local package-manager launcher and reran the installation/build workflow; superseded_by points to EV001 |
| EV-20261019-008 | local runtime / failure | evidence/2026-10-19-supply-chain-initial-failure.txt retains the actual unsafe-libyaml/serde_yaml_ng and internal path-version gate failures. It is not passing evidence |
| EV-20261019-009 | local runtime / observation | evidence/2026-10-19-supply-chain-final.txt records post-remediation cargo-deny: advisories ok, bans ok, licenses ok, sources ok. Unused license-allowance warnings remain visible. No advisory ignore or verification bypass was used |

Runtime containers, Xcode/mobile/native host integrations, physical devices, CI runs, legal clearance and full release SBOM/signing remain separate unclosed obligations. A final local green summary does not close them.

## Bounded M1 continuation — final local evidence, 2026-10-21

These records concern original synthetic fixtures and own source/builds on the
current Linux x86_64 host. They do not complete M1 or establish CI, other-OS,
physical-device, legal or accessibility certification. The separate analyst and
contract commits precede the L1 implementation; see the lane handoffs and
BUILD_STATUS.md for scope and review-unit branches.

| ID | Kind / classification | Actual record and bounded result |
| --- | --- | --- |
| EV-20261021-001 | local runtime / observation | evidence/2026-10-21-m1-validation-summary.json indexes 12 final command groups, exact invocations/exit codes and retained-tool preparation. Final check/test/native/WASM/web/protocol/browser/compose/secrets/supply-chain/benchmark commands exited 0 after the recorded web-build remediation. Its followup check records standalone `just build web` passing with generated protocol declarations initially absent. Per-command logs use the same date/prefix. `just test` ran 70 Node and 48 native tests; WASM smoke checked 23 new native-analysis parity cases plus 7 unchanged legacy cases and rejected 30 new malformed inputs plus legacy boundaries. No CI run is asserted |
| EV-20261021-002 | local browser runtime / observation | evidence/2026-10-21-m1-browser.json and -browser.log: 18 actual production-build Chromium cases passed using the compiled Worker/WASM. Coverage includes native/independent metrics, stale responses, Unicode Accept/Undo/export, restored CRLF/CR adoption to LF, IME/termination, consent/storage failures, Markdown/input limits, dense cards, Worker retry and keyboard/320 px/themes/reduced-motion/forced-colors cases. Three tested Axe states have no violations. Screenshot/report outputs are generated under ignored apps/web/test-results and copied to coordinator delivery artifacts; no manual screen-reader, zoom, physical-device or cross-browser certification follows |
| EV-20261021-003 | local runtime / observation | evidence/2026-10-21-m1-core-review.py and -core-review.json: the retained independent runner passes 400 seeded original Markdown/Unicode coordinate and containment cases, three duplicate-definition regressions, malformed HTML warning and 11 bounded release-CLI stress cases. The dense 1 MiB fixture measured 2.525 s and 587,492 KiB peak RSS including process startup, file I/O and JSON serialization. These single current-host measurements do not close UNK-M1-04 or establish browser/mobile budgets |
| EV-20261021-004 | local artifact inventory and measurement / observation, derivation | evidence/2026-10-21-m1-artifacts.json inventories current ignored Node/web files and hashes. Both WASM copies have SHA-256 f5142a63ece63ac0605f57122d5afbd6cc634aed1d466a77604f2217d2b5eb15; Node default gzip is 668,154 bytes and deterministic Python level 9 is 647,655 bytes. evidence/2026-10-21-m1-analysis-benchmark.log records 50-iteration p95 plain 1k/5k-word results 0.918/5.249 ms and Markdown results 2.018/5.078 ms on the declared Xeon host. This bounded one-rule pipeline excludes compilation, file I/O and initialization; no incremental/full-engine/mobile claim |
| EV-20261021-005 | local review and failure / retained, superseded where retested | evidence/2026-10-21-m1-validation-initial-summary.json and -web-build-initial.log retain the Turbo child-toolchain failure; final build/check/browser logs supersede it. evidence/2026-10-21-m1-ui-review-initial.md and -core-review-initial.json preserve pre-fix findings, including truthful status, accessible name, Markdown/projection and schema-generator cases; final tests/review records above establish only the retested outcomes. The 2026-10-20-m1-license-initial.log, -generated-boundary-initial.log and -browser-initial.json remain actual failures. The other 2026-10-20-m1 core/benchmark/artifact records are intermediate builder observations superseded by the October 21 integrated artifact records, never CI evidence |
| EV-20261021-010 | local own-artifact static / observation, derivation and unknown | evidence/2026-10-21-m1-rea-summary.json retains REA 6.4.0 invocations, limits, raw report hashes and manual artifact observations. Node application inventory coverage is complete with 231 semantic unknowns. The web bundle parsed without syntax failures but semantic coverage is partial/truncated with 365 unknowns after both 1 GiB and 4 GiB analyzer heaps were exhausted. Tool exit 0 and zero parse failures do not mean comprehensive coverage. Raw reports remain in the coordinator's /workspace/artifacts delivery archive. evidence/2026-10-21-m1-rea-secrets.log records the actual raw-report secret scan; UNK-M1-06 remains open |
| EV-20261021-011 | local package runtime / observation | evidence/2026-10-21-m1-package.json records the CRC-checked web ZIP, extracted distribution served through a plain local Python HTTP server, and a real packaged Worker/WASM Accept action preserving the exact expected source. One synthetic package smoke supplements the 18 built-app cases; no additional platform or production-deployment claim |
| EV-20261021-012 | static environment configuration / observation | evidence/2026-10-21-m1-environment.json records confirmed draft revision 4 and runtime revision 1. Setup/startup changes preserve VLC and include tested NexWrite commands; saved network additions remain unapplied. Review/save and publish are still required; local-only commit restoration remains unverified |

The final supply-chain log retains unused license-allowance warnings while
reporting advisory/bans/licenses/sources success; npm audit reports no known
vulnerabilities at that execution. License exceptions remain exact, reviewed
development-package/version scopes. Secret scans and configuration validation
are scoped to their actual inputs. Seven optional service configurations were
validated, but no backend service, devcontainer image or native platform client
was executed by this continuation.

The browser runtime observed only same-origin GET requests without request bodies
for its synthetic fixture. Static REA endpoint/import candidates cannot replace
that runtime evidence, and neither observation proves all future privacy paths.
Incomplete static semantic analysis remains explicitly unknown. Before any
ledger promotion, run and inspect real CI plus each feature's remaining required
cases and unknown remedies; all 149 rows still have zero CI-verified entries.
