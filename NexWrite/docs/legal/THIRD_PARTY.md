<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# Third-party provenance and licenses

Date: 2026-10-19. First-party code is `LicenseRef-NexWrite-Proprietary`; third-party components retain their own licenses. This record is an inventory and review plan, not counsel approval or a complete release SBOM. Exact resolved dependency versions belong in Cargo.lock and pnpm-lock.yaml. `node tools/license-check.mjs` checks the declared bootstrap inventory; metadata checks do not replace final transitive/artifact review (UNK-BOOT-06).

## Supplied source references, not vendored dependencies

| Input | Observed license | Disposition |
| --- | --- | --- |
| rea-main.zip | Top-level LICENSE: MIT, copyright 2026 morluto; inspected 2026-10-19 | No source/binary vendored or analyzed as a third-party binary; separately installed REA may audit own artifacts |
| liquid-glass-main (1).zip | Top-level LICENSE: MIT, copyright 2026 Glass HQ; inspected 2026-10-19 | No source, CSS, assets or fonts imported; original tokens follow the operator brief |

A root archive license does not automatically clear every bundled dependency or asset. Importing any future component requires a path-level notice/license review first. Source archive upload metadata is in docs/inputs/operator-brief.md.

## Direct bootstrap components

| Component | Version / pin | Declared license | Use / source |
| --- | --- | --- | --- |
| @fontsource-variable/inter | 5.3.0 | OFL-1.1 | Self-host configuration via npm package, https://www.npmjs.com/package/@fontsource-variable/inter |
| @fontsource-variable/space-grotesk | 5.3.0 | OFL-1.1 | Self-host configuration via npm package, https://www.npmjs.com/package/@fontsource-variable/space-grotesk |
| @fontsource-variable/jetbrains-mono | 5.3.0 | OFL-1.1 | Self-host configuration via npm package, https://www.npmjs.com/package/@fontsource-variable/jetbrains-mono |
| turbo | 2.11.9 | MIT | Task orchestration, npm metadata; source https://github.com/vercel/turborepo |
| Rust standard library/toolchain | 1.99.1 | MIT OR Apache-2.0 | Build runtime, https://www.rust-lang.org/policies/licenses |
| wasm-bindgen + matching CLI | 0.2.129 | MIT OR Apache-2.0 | WASM host binding, https://github.com/wasm-bindgen/wasm-bindgen |
| just | 1.59.0 | CC0-1.0 | Development task runner, https://github.com/casey/just |
| REA / rea-agents | 6.4.0 | MIT | External development audit tool; help/version must be captured, no executable copied into repo |
| gitleaks | 8.30.1 | MIT | External secret-scanning tool, https://github.com/gitleaks/gitleaks |
| cargo-deny | 0.20.2 | MIT OR Apache-2.0 | External dependency/advisory gate; observed cargo info offline metadata, https://github.com/EmbarkStudios/cargo-deny |

Font package metadata was checked at the registry during setup. Preserve font license/author notices if distributing package font files. No fonts or other binary assets are copied into this checkout. Review reserved font names before modification; see ASSETS.md. Rust dependency rows will be reconciled to the actual manifest/lockfile before a release; no unlisted vendored code is allowed.

## Future dependency intake

Record name, version, origin, exact license/SPDX, notice location, modified files, linking/distribution model, transitive review and reviewer. LGPL candidates require a distinct distribution/linking review; proposed separate services do not themselves establish compliance. No LanguageTool, Harper, Hunspell, model, dictionary, icon set, editor framework or native platform SDK is currently bundled merely because the roadmap mentions it.

## Supply-chain gate and remediation

The initial cargo-deny pass rejected unsafe-libyaml through serde_yaml_ng 0.10.0 and unversioned internal path requirements. The builder replaced that YAML dependency with exact serde-saphyr 2.0.0, default features disabled and deserialize enabled, backed by granit-parser 2.0.0 (both MIT OR Apache-2.0), and pinned internal package requirements. No advisory ignore, checksum change or trust bypass was added. The post-migration native tests and cargo-deny advisory/bans/licenses/sources checks passed locally; final integrated execution and rebuilt artifact records are indexed separately.

## Resolved Rust inventory after remediation (2026-10-19)

Observed from the post-migration `cargo metadata --locked --format-version 1` snapshot. License expressions are package-manifest declarations, not a complete release legal audit. These 40 registry dependencies are not vendored source. js-sys is used with default features disabled, and serde-saphyr has only the deserialize feature enabled. The raw historical manifest spelling MIT/Apache-2.0 on arraydeque/core_detect is retained below; the license gate normalizes precisely that known dual-license spelling to MIT OR Apache-2.0, rather than accepting arbitrary unknown license expressions.

| Package | Locked version | Declared license |
| --- | --- | --- |
| aho-corasick | 1.1.5 | Unlicense OR MIT |
| annotate-snippets | 0.12.16 | MIT OR Apache-2.0 |
| anstyle | 1.0.14 | MIT OR Apache-2.0 |
| arraydeque | 0.5.1 | MIT/Apache-2.0 |
| autocfg | 1.5.1 | Apache-2.0 OR MIT |
| bumpalo | 3.20.3 | MIT OR Apache-2.0 |
| cfg-if | 1.0.5 | MIT OR Apache-2.0 |
| core_detect | 1.0.0 | MIT/Apache-2.0 |
| encoding_rs | 0.8.42 | (Apache-2.0 OR MIT) AND BSD-3-Clause |
| encoding_rs_io | 0.1.8 | MIT OR Apache-2.0 |
| granit-parser | 2.0.0 | MIT OR Apache-2.0 |
| itoa | 1.0.18 | MIT OR Apache-2.0 |
| js-sys | 0.3.106 | MIT OR Apache-2.0 |
| memchr | 2.8.3 | Unlicense OR MIT |
| multiversion_no_op | 1.0.0 | Apache-2.0 OR MIT |
| num-traits | 0.2.19 | MIT OR Apache-2.0 |
| once_cell | 1.21.4 | MIT OR Apache-2.0 |
| proc-macro2 | 1.0.107 | MIT OR Apache-2.0 |
| quote | 1.0.47 | MIT OR Apache-2.0 |
| regex | 1.13.1 | MIT OR Apache-2.0 |
| regex-automata | 0.4.18 | MIT OR Apache-2.0 |
| regex-syntax | 0.8.11 | MIT OR Apache-2.0 |
| rustversion | 1.0.23 | MIT OR Apache-2.0 |
| scopeguard | 1.2.0 | MIT OR Apache-2.0 |
| serde | 1.0.229 | MIT OR Apache-2.0 |
| serde-saphyr | 2.0.0 | MIT OR Apache-2.0 |
| serde_core | 1.0.229 | MIT OR Apache-2.0 |
| serde_derive | 1.0.229 | MIT OR Apache-2.0 |
| serde_json | 1.0.154 | MIT OR Apache-2.0 |
| simdutf8 | 0.1.5 | MIT OR Apache-2.0 |
| smallvec | 1.16.3 | MIT OR Apache-2.0 |
| syn | 3.0.9 | MIT OR Apache-2.0 |
| unicode-ident | 1.0.27 | (MIT OR Apache-2.0) AND Unicode-3.0 |
| unicode-segmentation | 1.13.3 | MIT OR Apache-2.0 |
| unicode-width | 0.2.2 | MIT OR Apache-2.0 |
| wasm-bindgen | 0.2.129 | MIT OR Apache-2.0 |
| wasm-bindgen-macro | 0.2.129 | MIT OR Apache-2.0 |
| wasm-bindgen-macro-support | 0.2.129 | MIT OR Apache-2.0 |
| wasm-bindgen-shared | 0.2.129 | MIT OR Apache-2.0 |
| zmij | 1.0.23 | MIT |

Transitive review still needs distribution notices, SBOM and release-artifact reconciliation; local metadata/advisory gates do not close the full release obligation in UNK-BOOT-06.

## Installed npm inventory (2026-10-19, Linux)

Observed package metadata after frozen-lockfile installation. OS-specific optional packages in the lockfile still require review on their target runner; this table describes the current Linux installation. Installed package metadata is not a complete binary/asset notice audit.

| Package | Locked version | Declared license |
| --- | --- | --- |
| @fontsource-variable/inter | 5.3.0 | OFL-1.1 |
| @fontsource-variable/jetbrains-mono | 5.3.0 | OFL-1.1 |
| @fontsource-variable/space-grotesk | 5.3.0 | OFL-1.1 |
| @turbo/linux-64 | 2.11.9 | MIT |
| @typescript/typescript-linux-x64 | 7.0.2 | Apache-2.0 |
| ajv | 8.20.0 | MIT |
| fast-deep-equal | 3.1.3 | MIT |
| fast-uri | 3.1.8 | BSD-3-Clause |
| json-schema-traverse | 1.0.0 | MIT |
| require-from-string | 2.0.2 | MIT |
| turbo | 2.11.9 | MIT |
| typescript | 7.0.2 | Apache-2.0 |
