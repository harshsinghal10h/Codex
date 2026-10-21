<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# Task 0 local core threat model

Scope: CLI file input, Rust engine, JSON output/schema, generated types, Node/WASM consumer, build/tooling and optional local compose. No network analysis path, user accounts or native host integrations exist. This is an initial STRIDE assessment, not a completed security audit of future surfaces.

| Boundary / STRIDE | Risk | Current control | Remaining evidence |
| --- | --- | --- | --- |
| Files -> CLI / tampering, DoS | Invalid UTF-8, huge input, hostile paths | Read errors surfaced, typed offset conversion, negative tests | Explicit resource caps and fuzz campaigns before server exposure (UNK-BOOT-12) |
| Engine -> consumer / tampering | Wrong offsets edit unrelated text | Three half-open offset ranges, original text, version stamp, Unicode tests | Host apply/undo and stale-response tests on every future surface |
| Text -> JSON/stdout / information disclosure | Caller may publish reports | Caller-requested stdout only; no remote/telemetry path | Future logging/consent enforcement tests |
| JS/WASM boundary / elevation, DoS | Malformed host inputs, oversized documents | Narrow typed API and compiled smoke test | Worker cancellation/input caps before untrusted host integration |
| Dependency -> build / spoofing, tampering | Malicious or unlicensed package | Locked installs, upstream checksum/TLS validation, license/provenance gates | SBOM/transitive review and attestations (UNK-BOOT-06) |
| CI -> artifact / spoofing, repudiation | Misstated verification or altered build | SHA-pinned actions, separate local/CI evidence, evidence IDs | Real trusted CI run and signed release chain (UNK-BOOT-01) |
| Compose -> host / exposure | Public ports or reused demo credentials | Local development scope, bind services to localhost, env-only credentials | Runtime health/isolation tests (UNK-BOOT-07) |

No external prompt or document can authorize tool execution. Future LLM, SSRF fetcher, keyboards, native accessibility helpers, update channel and API require separate surface-specific STRIDE models before implementation. Signing, access controls and private reporting remain release gates.
