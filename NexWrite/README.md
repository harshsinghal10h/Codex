<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# NexWrite

NexWrite is an original, local-first writing-assistant project alongside the existing projects in the Codex repository. It includes a Rust engine, CLI, compiled WASM and a small browser editor. Mobile, desktop, extension, backend, account, billing and AI-provider integrations remain roadmap work.

The bounded M1 slice adds source-preserving Unicode segmentation, conservative Markdown protection and explicitly gated readability estimates. The editor uses the same local WASM engine for one original ASCII double-space rule, safe Accept/Undo and text export. Draft storage requires opt-in. The ledger tracks all 149 requested feature/platform entries; this is part of M1, with one spacing rule rather than the planned grammar/dictionary engine. Local results and limits are recorded in BUILD_STATUS.md and docs/ledger/EVIDENCE.md. No feature is called CI-verified without a real run.

## Run locally

Use Rust 1.99.1, Node 24.19.0, pnpm 12.14.0, Python 3.12.14 and just 1.59.0; supply-chain gates also require cargo-deny 0.20.2 and gitleaks 8.30.1. Rust's wasm32-unknown-unknown target and wasm-bindgen CLI 0.2.129 are needed for WASM. The cloud environment can load them with `source /workspace/.toolchains/nexwrite/activate.sh`; elsewhere install the versions declared by the project.

```sh
cd /workspace/Codex/NexWrite
pnpm install --frozen-lockfile
just check
just test
just supply-chain
just secrets
just build core
just build wasm
printf 'One  two\n' > /tmp/nexwrite-sample.txt
cargo run -p nw-cli -- check /tmp/nexwrite-sample.txt --format json
cargo run -p nw-cli -- analyze /tmp/nexwrite-sample.txt --format json --input-format plain
cargo run -p nw-cli -- rules test
cargo run -p nw-cli -- bench
just bench-analysis
just build web
just web
```

Replace the sample path with any UTF-8 plain-text file. `nw check` intentionally prints the requested report to stdout; it sends no text to a network service. It replaces nothing by itself. The rule finds maximal runs of ASCII spaces, including indentation and leading/trailing spaces. It does not understand code, Markdown, URLs, grammar, dictionaries or semantic formatting yet. Avoid treating its suggestion as a safe structured-document formatter.

`nw analyze` accepts UTF-8 source up to 1,048,576 bytes, with `--input-format plain` (default) or `markdown`. It returns original UTF-8/UTF-16/scalar spans, counts, warnings and eligible estimates. Markdown code, links, images, HTML, references and ambiguous markup are conservatively excluded; retained fragments are analyzed separately. Any protected Markdown source suppresses English readability formulas. The ASCII syllable heuristic and default UAX #29 segmentation have the limits described in docs/spec/m1-text-analysis.md. Analysis reports data and suggestions without editing the file.

The editor opens at the address printed by Vite. It analyzes in a local Web Worker, supports pausing checks and stores no draft until consent. Native textareas adopt LF line endings; export and Undo preserve that adopted text. `just browser-test` tests the built app in Chromium; run `pnpm exec playwright install chromium` from apps/web first, or set `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` to an installed Chromium executable. Actual browser coverage and remaining limits are recorded separately from the configured CI jobs.

The canonical protocols are `packages/protocol/schema/suggestion.schema.json` and `analysis.schema.json`, with shared local references. Generate checked-in Rust/TS/Kotlin/Swift types with `node tools/codegen/generate.mjs`, or use `--check` to detect drift. A generated Kotlin/Swift declaration is not an implemented mobile app or a compiled native bridge.

## Development and evidence

Read AGENTS.md, docs/spec/m1-text-analysis.md, docs/spec/m1-editor-demo.md and docs/ledger/UNKNOWNS.md. The full supplied requirements are preserved in docs/MASTER_PROMPT.md, with upload provenance in docs/inputs/operator-brief.md. The Task 0 scope and M1 decisions are recorded in the ADRs. The remaining milestones are tracked in the ledger.

`infra/compose/compose.yaml` defines optional development services; its existence or successful configuration validation does not establish service readiness. Docker services are unnecessary for the local core/CLI/WASM slice. Do not expose development services to a public network. See SECURITY.md and docs/architecture/threat-models/bootstrap.md before adding a remote path.

## License and status

All rights reserved pending an operator decision. First-party source uses `LicenseRef-NexWrite-Proprietary`; dependency licenses remain their own. No trademark clearance, security certification, accessibility certification, store acceptance, or cross-platform runtime coverage is asserted. See docs/legal for provenance, limitations and review checklists.
