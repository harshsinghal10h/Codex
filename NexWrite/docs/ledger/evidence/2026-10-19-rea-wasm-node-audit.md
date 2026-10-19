<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# Own WASM bridge artifact audit

Observation, local static evidence, E5, 2026-10-19. Only our generated `target/wasm-node` directory was selected. No third-party target was analyzed. REA 6.4.0 was installed to a workspace-local prefix through npm with pinned package integrity and TLS checks retained; lifecycle scripts were disabled. Version, help, acquisition and CLI schema outputs are adjacent evidence files.

Actual command: `/workspace/.toolchains/nexwrite/rea/node_modules/.bin/rea analyze-javascript-application /workspace/Codex/NexWrite/target/wasm-node --artifact-format directory --max-heap-mb 512 --analysis-timeout-ms 60000 --format json --full-output`. Exit 0. Complete output: `2026-10-19-rea-wasm-node.json`; view: `2026-10-19-rea-wasm-node-summary.json`; exact invocation, timing and five-file SHA-256/gzip inventory: `2026-10-19-rea-wasm-node-invocation.json`. File identities were unchanged before/after the REA command.

REA parent Evidence: `ev_d98118749b6c2c558c515e9ea1ba3ad7d93243754e91f151854c56965ec867fd`. Artifact-tree SHA-256: `3579a1a426b4489b4d3fddc91825b0ad9450683104d61f8f22fa3f661b3f1123`. Generated JavaScript SHA-256: `4e41048233023957199af0e5d467d243696f68b45269a8561d9bae7bd4eca978`. WASM SHA-256: `48b4bddac19a7ef7d4b8986056b109b35cf8cab75718cfa40725cb5592d49300`.

| Obligation | Result and boundary |
| --- | --- |
| Meaningful own-artifact analysis | REA read 7,130 text bytes across 4 relevant files, parsed 3 JS/TS files, visited 1,022 AST nodes, and reported 0 parse failures. Application coverage is complete within its static application inventory. |
| Entry point | REA observed the CommonJS export `check_json` in `nw_wasm.js`; source observation confirms generated glue reads sibling `nw_wasm_bg.wasm` with Node `fs` then instantiates WebAssembly. This is the core bootstrap bridge, not a delivered browser/extension/desktop integration. |
| Declarations and permissions | No platform permission manifest is present in the five-file Node library artifact. Inference: host Node filesystem permissions govern the observed local WASM read. This does not establish browser or mobile permissions. |
| Source maps | 0 `.map` inventory entries and 0 `sourceMappingURL` directives in generated JS. WASM debug custom sections were not decoded. |
| Secrets | Actual `gitleaks 8.30.1 dir ... --redact=100` exited 0, found 0 leaks. Its exact output and JSON report are adjacent. This text scan does not prove binary WASM contains no secrets. |
| External network literals | Supplemental static scan found 0 HTTP/HTTPS/WS/WSS URL literals in generated JS. Dynamic targets and WASM network semantics remain unknown. |
| Dependency/license inventory | Locked offline Cargo metadata records the 45-package resolved `nw-wasm` closure, including build/proc-macro dependencies, in `2026-10-19-rea-wasm-node-audit.json`. License expressions were captured; this is not proof all listed crates are linked or a complete release SBOM/notice review. |
| Size budget | Exact WASM is 1,587,592 bytes; deterministic Python gzip level 9 is 525,052 bytes, below S12's 3 MiB WASM gzip budget. Node v24.19.0 default gzip is 541,253 bytes (zlib 1.3.2.1-motley-3246f1b); Python zlib 1.3.2 differs. Both are below budget. Exact compression commands/results are adjacent. This is not latency or memory evidence. |

Unknowns and limits: REA semantic coverage is `unknown`, with 199 semantic unknowns and partial call/data/object-flow families. Its static graph does not prove runtime execution, dynamic property behavior, privacy/network isolation, native/WASM semantics, platform integration, CI success or real-device behavior. WASM runtime tests belong to independently executed core smoke evidence. The machine-readable audit distinguishes observation, derivation, inference and unknown scope. The local Task 0 own-JS-artifact obligation is answered within these boundaries; final release/native-artifact audit and transitive license/SBOM obligations remain open.
