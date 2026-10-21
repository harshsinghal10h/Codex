<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# NexWrite agent instructions

This directory is the independent NexWrite project inside the existing Codex checkout; Task 0 is complete. Preserve sibling projects and existing changes. Cloud tasks already have an isolated checkout: use it; do not create a worktree unless the user asks.

## Start and finish

Read this file, docs/MASTER_PROMPT.md (S1–S3, S14 and the current lane), docs/ledger/FEATURES.csv, docs/ledger/UNKNOWNS.md, BUILD_STATUS.md and docs/handoff/<lane>.md before work. The user's 2026-10-20/21 continuations cover the bounded M1 source-analysis/readability (L1) and minimal local editor (L3) slices, from prior analyst specifications and separate implementation review units. Both now have local implementation evidence; full M1 and later milestones remain incomplete. Finish by updating affected ledger rows, unknowns, evidence index, handoff and CHANGELOG.md. Keep the complete master prompt unchanged.

## Commands (run from this directory)

- `source /workspace/.toolchains/nexwrite/activate.sh` loads the local cloud toolchain when that file exists; other machines install the pinned tools normally.
- `pnpm install --frozen-lockfile` installs the locked JS workspace.
- `just check` runs project checks and Rust lint; `just test` runs meaningful tooling/Rust tests.
- `just build core` builds the native engine; `just build wasm` builds compiled WASM and parity/malformed-input smoke tests.
- `just build web` builds protocol prerequisites and the actual web-target WASM/app; `just web` starts its local development process. Turbo web build caching is disabled so Rust changes cannot leave stale WASM.
- `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/chromium just browser-test` runs actual built-app Chromium checks in this cloud environment; install Playwright Chromium or select its executable elsewhere. Build first. No CI or certification claim follows from a local pass.
- `pnpm check` checks ledger, provenance, license metadata, code generation and design-token outputs.
- `just supply-chain` runs metadata/license, cargo-deny and pnpm advisory gates; `just secrets` runs the actual gitleaks gate. Do not suppress an advisory to make setup pass.
- `node --test tools/tests/*.test.mjs` runs the tooling's regression tests.
- `cargo test --workspace --locked` runs Rust tests; `cargo fmt --all -- --check` and `cargo clippy --workspace --all-targets --locked -- -D warnings` lint Rust.
- `cargo run -p nw-cli -- check FILE --format json` checks a UTF-8 plain-text file; `cargo run -p nw-cli -- rules test` checks rule examples.
- `cargo run -p nw-cli -- analyze FILE --format json --input-format plain` runs additive source analysis; use `markdown` for the conservative source protector. Inputs are limited to 1,048,576 UTF-8 bytes and en-US.
- `cargo run -p nw-cli -- schema` exports Suggestion; `cargo run -p nw-cli -- schema analysis` exports AnalysisResult. `node tools/codegen/generate.mjs --check` checks both schemas' generated bindings.
- `cargo run -p nw-cli -- bench` reports legacy single-rule measurements; `just bench-analysis` measures the bounded analysis pipeline.
- `just compose-check` validates all seven optional service configurations with temporary generated values; containers are not started. `just dev-env` creates ignored local credentials before `just services` starts the backend profile. Record runtime results separately.

`just build <surface>` is parameterized because recipe names cannot contain colons. The implemented targets are core, wasm and the local web demo; other surfaces remain ledger work. Keep draft storage opt-in and source text out of logs. Browser text adopts native textarea LF line endings; core spans continue to reference exact input source.

## Repository map and lane ownership

L0: .github, tools, infra, docs/adr, docs/legal (tooling/contracts). L1: core, rules, models (engine). L2: services (backend). L3: apps/web, apps/site, apps/admin, packages/ui-kit, packages/editor. L4: apps/extension, packages/adapters. L5: apps/desktop. L6: apps/android. L7: apps/ios. L8: apps/office, apps/gdocs. L9: packages/design-tokens, packages/i18n, docs/design. L10: tests, docs/runbooks and cross-cutting CI. L11: apps/lsp, apps/cli, packages/sdk. Empty roadmap directories are not functioning integrations. Protocol changes land before consumers. Root coordination files are edited by the coordinating task.

## Clean-room summary (10 rules)

1. Use only public documentation, official platform documentation, licensed OSS, operator notes, or own-artifact evidence.
2. Read bounded public sources without login/paywalls, respecting robots and at most one request per second.
3. Never unpack, trace or audit reference-product binaries, private endpoints, traffic or bundles.
4. Never copy reference code, strings, styles, identifiers, prompts, dictionaries, models or assets.
5. Never use reference outputs as tests, training or distillation data.
6. Analysts write original behavioral specs; builders use those specs, official docs and licensed OSS.
7. Future feature specifications and implementations use separate tasks/PRs; Task 0's explicit bootstrap exception is recorded in ADR-0002.
8. Audit only own artifacts unless docs/legal/AUTHORIZATION.md contains a matching operator-signed third-party entry.
9. Record evidence class, source/date, original asset provenance and every third-party license.
10. Keep the verbatim master prompt archival; product-source provenance checks enforce the brand restrictions.

## Definition of done and evidence

Spec -> positive/negative/malformed tests and required a11y/perf/privacy/i18n cases -> implementation -> telemetry off -> docs -> evidence -> handoff. A successful local command is local evidence, never CI evidence. `verified` requires a real CI run URL, passing results for every required case and no open unknowns. Fail, skipped, simulated or unknown never means pass. Follow docs/ledger/EVIDENCE.md. Every stub must carry `TODO(ledger:ID)` with an actual ledger ID. No stub is advertised as a working integration.

## Privacy, security and never-do list

Local-only default. No text in telemetry or background logs, no training on user data, cloud/LLM features require explicit opt-in. Never put secrets, tokens, credentials, model weights, third-party binaries or vendored code in the repo. Do not disable TLS/signatures/checksums. Do not claim legal clearance, certified accessibility, real-device coverage or verified integration without matching evidence. Preserve Unicode text and all three offset representations. Avoid real-person impersonation. Detectors/graders must state uncertainty.

## Changes and delivery

Use `feat/<lane>/<slug>` branches and Conventional Commit titles. Never force-push, delete branches, or push to main. Aim at one lane and at most 12 feature rows per later PR; Task 0 is the explicit cross-lane bootstrap. Probe push tools before publication and retain exact errors. Prefer one bootstrap PR titled `chore: bootstrap monorepo, ledger, legal, CI`; if unavailable, provide a patch/bundle and exact commands without claiming publication. Do not commit secrets, generated build binaries, node_modules or target directories. Do not alter protected sibling projects. Keep first-party SPDX headers `LicenseRef-NexWrite-Proprietary` unless the operator changes the license.

## Review checklist

Check scope/clean-room/provenance; exact ledger coverage; claims against CI evidence; privacy and least privilege; contract/type regeneration; Unicode/negative cases; original licensed assets; no secrets/binaries; actual performance/a11y limits; unknowns with remedies; handoff and changelog. End with S17: Scope | Changes | Ledger delta | Evidence | Unknowns/blocked | Risks | Push status | Next 5 tasks.
