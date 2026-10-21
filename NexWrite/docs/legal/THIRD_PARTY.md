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

## M1 dependency intake (2026-10-20)

Scope: structured-source/readability core and the local browser editor demo. The following observations come from installed, locked package manifests and their actual LICENSE/NOTICE files; package licenses were not inferred from a repository's top-level license. The current Linux snapshot contains 43 Rust registry packages and 40 installed npm packages. Existing Task 0 tables above remain historical snapshots. This is an engineering provenance review, not counsel approval or a complete release SBOM.

New Rust packages are compiled into the analysis engine, including its WASM build. No third-party source is vendored. Their installed Cargo manifests and license texts declare:

| Package | Locked version | Declared license | Source / notice files |
| --- | --- | --- | --- |
| pulldown-cmark | 0.13.4 | MIT | https://github.com/raphlinus/pulldown-cmark ; LICENSE, copyright 2015 Google Inc. |
| bitflags | 2.13.2 | MIT OR Apache-2.0 | https://github.com/bitflags/bitflags ; LICENSE-MIT and LICENSE-APACHE |
| unicase | 2.10.0 | MIT OR Apache-2.0 | https://github.com/seanmonstar/unicase ; LICENSE-MIT and LICENSE-APACHE |

pulldown-cmark is pinned with default features disabled. Its locked crate SHA-256 is `e9f068eba8e7071c5f9511831b44f32c740d5adf574e990f946ddb53db2f314e`. The inspected LICENSE text has SHA-256 `c4f10f55904bdb9f27d3fbf94c354926d6cfe8b982276e556238c258941b243b`. unicase's actual declaration is dual-licensed, even though choosing its MIT option is permitted; it is not recorded as MIT-only.

| Browser component | Exact version | Declared license | Use / upstream source / installed notices |
| --- | --- | --- | --- |
| react, react-dom | 19.3.1 each | MIT | Runtime UI; https://github.com/react/react ; each package LICENSE, copyright Meta Platforms, Inc. and affiliates |
| scheduler | 0.28.0 | MIT | React DOM runtime dependency; https://github.com/react/react ; LICENSE |
| vite | 8.3.4 | MIT | Development/build tool; https://github.com/vitejs/vite ; LICENSE.md also includes bundled-component notices |
| @vitejs/plugin-react | 6.1.2 | MIT | Development/build tool; https://github.com/vitejs/vite-plugin-react ; LICENSE |
| playwright, playwright-core | 1.64.1 each | Apache-2.0 | Development/CI browser testing; https://github.com/microsoft/playwright ; each package LICENSE and NOTICE |
| @axe-core/playwright | 4.14.0 | MPL-2.0 | Development/CI accessibility testing only; https://github.com/dequelabs/axe-core-npm ; LICENSE |
| axe-core | 4.14.0 | MPL-2.0 | Accessibility scanner used by the test wrapper; https://github.com/dequelabs/axe-core ; LICENSE and LICENSE-3RD-PARTY.txt |
| lightningcss | 1.33.0 | MPL-2.0 | Vite CSS build tool; https://github.com/parcel-bundler/lightningcss ; LICENSE |
| lightningcss-linux-x64-gnu | 1.33.0 | MPL-2.0 | Installed Linux build-tool binding; same upstream; LICENSE |
| @fontsource-variable/inter, @fontsource-variable/space-grotesk, @fontsource-variable/jetbrains-mono | 5.3.0 each | OFL-1.1 | Reused pinned self-hosted font packages; https://github.com/fontsource/font-files ; full package LICENSE/copyright texts retained as described in ASSETS.md |

### Exact development-only MPL review

The allowance covers unmodified `@axe-core/playwright` and `axe-core` at exactly 4.14.0 for automated accessibility testing, and unmodified Lightning CSS at exactly 1.33.0 for CSS transformation during development/build. The scanners run in the test browser; Lightning CSS runs in the build process. They are not product UI implementations. Production source imports and the workspace production dependency graph were inspected: these packages are not runtime imports/dependencies. This review does not authorize shipping MPL UI code or adding these tools to production dependencies.

Lightning CSS 1.33.0 declares eleven exact optional platform packages. Their npm registry manifests were individually checked on 2026-10-20 and each declares MPL-2.0: `lightningcss-darwin-x64`, `lightningcss-darwin-arm64`, `lightningcss-win32-x64-msvc`, `lightningcss-win32-arm64-msvc`, `lightningcss-linux-x64-gnu`, `lightningcss-linux-x64-musl`, `lightningcss-linux-arm64-gnu`, `lightningcss-linux-arm64-musl`, `lightningcss-linux-arm-gnueabihf`, `lightningcss-freebsd-x64`, and `lightningcss-android-arm64`. Each manifest can be retrieved at `https://registry.npmjs.org/<exact-package-name>/1.33.0`. Only the Linux x64 GNU binding was installed/inspected locally; metadata for another platform is not a runtime or binary-content audit of that platform.

`tools/license-check.mjs` retains a false result for generic MPL-2.0 acceptance. Its reviewed exception requires the exact package name, version, declared MPL-2.0 license and development-only context. A conservative dependency traversal rejects the exception if a package is reachable through workspace production, optional or peer dependencies. Neighboring versions, arbitrary native-package names and unrelated MPL packages still fail. This dependency check does not replace inspection of application imports and built artifacts. Generated CSS is compiled from our original styles and licensed font declarations; distributing the build tool itself, modifying covered files, or copying any tool code into the product requires a separate distribution review, including upstream source and notice obligations. Preserve the tools' LICENSE/NOTICE/third-party notices if redistributing them.

### Browser notice packaging observed

`apps/web/tools/build-engine.mjs` copies the three font OFL texts and React/React DOM/Scheduler MIT texts unchanged into generated `apps/web/public/notices/`. The browser footer links to the local `/notices/` files; Vite copies public files into `apps/web/dist/notices/` when building. The generated public files were inspected and match the installed license text hashes recorded in ASSETS.md.

The helper also writes `/notices/engine-third-party.txt` from actual LICENSE/COPYING/NOTICE texts of every locked Rust registry dependency. The observed generated file contains all 43 locked registry package/version headings and is 443,919 bytes, SHA-256 `294cc5b61f30442d9145d917956c4666e71388631b8063400ad5538ac8f1e52d`. It intentionally includes a conservative workspace/build-time superset. It is not a reachability proof or complete release SBOM; toolchain/runtime and release-artifact reconciliation remain part of UNK-BOOT-06. Generated notice files are packaging outputs, not vendored third-party source.

### Installed npm inventory (2026-10-20, Linux)

Observed from actual installed package.json files after the M1 workspace installation. The 40 entries below include runtime and development packages; OS-specific optional bindings absent on Linux are not claimed to have been executed or inspected as binaries.

| Package | Installed version | Declared license |
| --- | --- | --- |
| @axe-core/playwright | 4.14.0 | MPL-2.0 |
| @fontsource-variable/inter | 5.3.0 | OFL-1.1 |
| @fontsource-variable/jetbrains-mono | 5.3.0 | OFL-1.1 |
| @fontsource-variable/space-grotesk | 5.3.0 | OFL-1.1 |
| @oxc-project/types | 0.154.0 | MIT |
| @rolldown/binding-linux-x64-gnu | 1.2.14 | MIT |
| @rolldown/pluginutils | 1.0.1 | MIT |
| @turbo/linux-64 | 2.11.9 | MIT |
| @types/node | 24.19.2 | MIT |
| @types/react | 19.3.0 | MIT |
| @types/react-dom | 19.3.0 | MIT |
| @typescript/typescript-linux-x64 | 7.0.2 | Apache-2.0 |
| @vitejs/plugin-react | 6.1.2 | MIT |
| ajv | 8.20.0 | MIT |
| axe-core | 4.14.0 | MPL-2.0 |
| csstype | 3.2.3 | MIT |
| detect-libc | 2.1.2 | Apache-2.0 |
| fast-deep-equal | 3.1.3 | MIT |
| fast-uri | 3.1.8 | BSD-3-Clause |
| fdir | 6.5.0 | MIT |
| json-schema-traverse | 1.0.0 | MIT |
| lightningcss | 1.33.0 | MPL-2.0 |
| lightningcss-linux-x64-gnu | 1.33.0 | MPL-2.0 |
| nanoid | 3.3.20 | MIT |
| picocolors | 1.1.1 | ISC |
| picomatch | 4.0.7 | MIT |
| playwright | 1.64.1 | Apache-2.0 |
| playwright-core | 1.64.1 | Apache-2.0 |
| postcss | 8.5.31 | MIT |
| react | 19.3.1 | MIT |
| react-dom | 19.3.1 | MIT |
| require-from-string | 2.0.2 | MIT |
| rolldown | 1.2.14 | MIT |
| scheduler | 0.28.0 | MIT |
| source-map-js | 1.2.2 | BSD-3-Clause |
| tinyglobby | 0.2.17 | MIT |
| turbo | 2.11.9 | MIT |
| typescript | 7.0.2 | Apache-2.0 |
| undici-types | 7.24.6 | MIT |
| vite | 8.3.4 | MIT |
