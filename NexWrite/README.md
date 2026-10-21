<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# NexWrite

NexWrite is an original, local-first writing assistant alongside the existing projects in the Codex repository. The preview includes a native Android app and writing keyboard, a responsive offline-capable browser workspace, a shared Rust engine/CLI/WASM, and an optional personal AI backend with OpenAI-compatible and Gemini adapters. Accounts, cloud document sync, native desktop/iOS applications, extensions and the remaining full-product roadmap are still incomplete.

Library, Write, Assist and Settings use original NexWrite cards/navigation. Save explicitly stores a local document; automatic draft storage is a separate opt-in. The local engine currently provides one original spacing rule, Unicode counts, conservative Markdown protection and labelled readability estimates. AI starts unconfigured: enter your self-hosted HTTPS backend in Settings, discover its configured models and approve each request. Provider keys stay on the server. This is a preview rather than the planned complete grammar/dictionary engine or all-surface product. [Build status](BUILD_STATUS.md) and the [149-row ledger](docs/ledger/FEATURES.csv) record actual evidence and limits.

Android requires Android 8.0 or later and supports arm64-v8a, armeabi-v7a and x86_64. See [native build and behavior](apps/android/README.md), [web behavior](apps/web/README.md), and [backend deployment](services/api/README.md). Browser installation provides the web surface on supported operating systems; it does not establish a native app for every OS.

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
