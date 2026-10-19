<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# Evidence index and reporting contract

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
