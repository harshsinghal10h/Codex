<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# NexWrite

NexWrite is an original, local-first writing-assistant project. This directory contains its **Task 0 bootstrap**, alongside existing projects in the Codex repository. It does not yet provide the roadmap's web, mobile, desktop, extension, backend, account, billing or AI-provider integrations.

The initial working slice is a Rust plain-text engine and `nw` CLI, a Suggestion JSON Schema with generated client types, Unicode offset conversion, one original ASCII double-space rule, and a compiled WASM interface. The ledger tracks all 149 requested feature/platform entries; a double-space example implements only part of COR-06. Local results and limitations are recorded in docs/ledger/EVIDENCE.md and docs/handoff/L0.md. No feature is called CI-verified without a real run.

## Run the bootstrap

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
cargo run -p nw-cli -- rules test
cargo run -p nw-cli -- bench
```

Replace the sample path with any UTF-8 plain-text file. `nw check` intentionally prints the requested report to stdout; it sends no text to a network service. It replaces nothing by itself. The rule finds maximal runs of ASCII spaces, including indentation and leading/trailing spaces. It does not understand code, Markdown, URLs, grammar, dictionaries or semantic formatting yet. Avoid treating its suggestion as a safe structured-document formatter.

The canonical protocol is `packages/protocol/schema/suggestion.schema.json`; generate checked-in Rust/TS/Kotlin/Swift types with `node tools/codegen/generate.mjs`, or use `--check` to detect drift. A generated Kotlin/Swift declaration is not an implemented mobile app or a compiled native bridge.

## Development and evidence

Read AGENTS.md, docs/spec/bootstrap-core.md and docs/ledger/UNKNOWNS.md. The full supplied requirements are preserved in docs/MASTER_PROMPT.md, with upload provenance in docs/inputs/operator-brief.md. The Task 0 scope and exceptions are recorded in the ADRs. The remaining milestones are tracked, not silently replaced with stubs.

`infra/compose/compose.yaml` defines optional development services; its existence or successful configuration validation does not establish service readiness. Docker services are unnecessary for the local core/CLI/WASM slice. Do not expose development services to a public network. See SECURITY.md and docs/architecture/threat-models/bootstrap.md before adding a remote path.

## License and status

All rights reserved pending an operator decision. First-party source uses `LicenseRef-NexWrite-Proprietary`; dependency licenses remain their own. No trademark clearance, security certification, accessibility certification, store acceptance, or cross-platform runtime coverage is asserted. See docs/legal for provenance, limitations and review checklists.
