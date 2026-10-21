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

## Owned Android and backend intake (2026-10-21)

This release work builds the operator's NexWrite application. It does not rename
or redistribute another project's APK. The observations below are installed
source/metadata/license-text intake, not complete license clearance, an exact
runtime-linkage SBOM, or device/CI verification. Historical snapshots above remain
unchanged. The current locked metadata includes **68 Cargo registry packages**;
the current Linux pnpm installation has **83 package/version entries**.

| Source/tool reference | Observed provenance | Distribution boundary |
| --- | --- | --- |
| ClearPDF licensed source | https://github.com/Chethan616/ClearPDF at `7e00db1dd65fea94256afcf95b04ed53b3c71f30`; root MIT license, copyright 2026 Chethan616; LICENSE SHA-256 `a03f3574b14dc58163947d66e2b82719cbc89888f87936dbe69ec7a47c392f17` | E3 reference for grouped cards/navigation organization. No source, styles, strings, logos, screenshots or other assets copied into NexWrite. Its package identity, PDFs and upstream APK are not NexWrite deliverables. |
| Operator-supplied Apktool-main.zip | Archive SHA-256 `18d93873f4e4d4b220dd47e55f866b0c245340064afb613eacba6e8dd753dd2b`; 611 entries, 57,111,913 uncompressed bytes; no traversal paths/symlinks or SKILL.md observed; top-level Apache-2.0 LICENSE.md SHA-256 `c49c53fdc79e1143a1892eb7333b595eecdbe40623e72be325267db28cb72114` | External development/audit source, not an Android skill package. Reviewed Gradle source build produced actual CLI reporting `3.1.0-dirty`; tool used only against owned NexWrite artifacts. Neither its sources/dependencies nor its executable are bundled in the APK or tracked repository. Apache-2.0 at the archive root does not clear every tool dependency. |
| Gradle 9.3.0 distribution | Official distribution SHA-256 pin `0d585f69da091fc5b2beced877feab55a3064d43b8a1d46aeb07996b0915e0e0`; original bootstrap downloads and verifies into ignored build cache | Development build tool, no wrapper JAR or Gradle binary tracked/shipped in product. Android SDK/NDK/JDK remain installed external toolchains with their own terms/notices. |

### JNI dependency additions

`jni` is pinned to 0.21.1 with default features disabled. The exact installed
`LICENSE-MIT`/`LICENSE-APACHE` texts were inspected; JNI is a reviewed JSON boundary,
not a claim that UniFFI was implemented. The following 25 registry package/version
additions are relative to the prior 43-package snapshot, including platform and
build-time dependencies that need not be linked into the Android APK. Origin for
each is its locked crates.io package and repository declared in its Cargo manifest.

| Package | Locked version | Actual declared license | Installed notice files |
| --- | --- | --- | --- |
| bytes | 1.12.1 | MIT | LICENSE |
| cesu8 | 1.1.0 | Apache-2.0/MIT | COPYRIGHT-RUST.txt |
| combine | 4.6.9 | MIT | LICENSE |
| jni | 0.21.1 | MIT/Apache-2.0 | LICENSE-APACHE, LICENSE-MIT |
| jni-sys | 0.3.1 | MIT OR Apache-2.0 | LICENSE-APACHE, LICENSE-MIT |
| jni-sys | 0.4.1 | MIT OR Apache-2.0 | LICENSE-APACHE, LICENSE-MIT |
| jni-sys-macros | 0.4.1 | MIT OR Apache-2.0 | Declaration only; no complete notice text |
| log | 0.4.34 | MIT OR Apache-2.0 | LICENSE-APACHE, LICENSE-MIT |
| same-file | 1.0.6 | Unlicense/MIT | COPYING, LICENSE-MIT |
| syn | 2.0.119 | MIT OR Apache-2.0 | LICENSE-APACHE, LICENSE-MIT |
| thiserror | 1.0.69 | MIT OR Apache-2.0 | LICENSE-APACHE, LICENSE-MIT |
| thiserror-impl | 1.0.69 | MIT OR Apache-2.0 | LICENSE-APACHE, LICENSE-MIT |
| walkdir | 2.5.0 | Unlicense/MIT | COPYING, LICENSE-MIT |
| winapi-util | 0.1.11 | Unlicense OR MIT | COPYING, LICENSE-MIT |
| windows-link | 0.2.1 | MIT OR Apache-2.0 | license-apache-2.0, license-mit |
| windows-sys | 0.45.0 | MIT OR Apache-2.0 | license-apache-2.0, license-mit |
| windows-sys | 0.61.2 | MIT OR Apache-2.0 | license-apache-2.0, license-mit |
| windows-targets | 0.42.2 | MIT OR Apache-2.0 | license-apache-2.0, license-mit |
| windows_aarch64_gnullvm | 0.42.2 | MIT OR Apache-2.0 | license-apache-2.0, license-mit |
| windows_aarch64_msvc | 0.42.2 | MIT OR Apache-2.0 | license-apache-2.0, license-mit |
| windows_i686_gnu | 0.42.2 | MIT OR Apache-2.0 | license-apache-2.0, license-mit |
| windows_i686_msvc | 0.42.2 | MIT OR Apache-2.0 | license-apache-2.0, license-mit |
| windows_x86_64_gnu | 0.42.2 | MIT OR Apache-2.0 | license-apache-2.0, license-mit |
| windows_x86_64_gnullvm | 0.42.2 | MIT OR Apache-2.0 | license-apache-2.0, license-mit |
| windows_x86_64_msvc | 0.42.2 | MIT OR Apache-2.0 | license-apache-2.0, license-mit |

`MIT/Apache-2.0` and `Apache-2.0/MIT` are the known historical dual-choice spelling;
they are preserved as observed and normalized narrowly to `MIT OR Apache-2.0` by
the engineering gate. `same-file` 1.0.6 and `walkdir` 2.5.0 similarly declare
`Unlicense/MIT`; their complete COPYING and MIT texts were inspected, including
Andrew Gallant's 2017 and 2015 copyright notices respectively. Only this exact
known spelling is normalized to `Unlicense OR MIT`; arbitrary slash expressions
remain unsupported. The installed `cesu8` attribution is COPYRIGHT-RUST.txt,
which notice collectors must preserve along with its Rust/Eric Kidd attribution.

Open notice-text gap: `jni-sys-macros` 0.4.1 declares MIT OR Apache-2.0 but its
registry archive contains no complete LICENSE/NOTICE text. It is a proc-macro
build tool, not a separately linked runtime library. The generated notice
inventory reports this gap explicitly; metadata acceptance does not mean its
source-distribution notice obligations have been closed.

### Backend npm additions

The actual `@nexwrite/api` manifest adds Fastify 5.12.7 (MIT), reuses AJV 8.20.0
(MIT), and uses the shared first-party protocol package. These 43 newly installed
Linux package/version entries are relative to the historical 40-entry snapshot.
Each license below is from its installed package.json; listed notice files were
present locally. Package repositories are recorded by those installed manifests
and the npm registry package/version identifiers. Development/test-only and
transitive packages are included conservatively; this table is not a runtime
reachability proof. These Node dependencies are backend/build components and are
not shipped inside the native APK.

| Package | Installed version | Declared license | Installed notice files |
| --- | --- | --- | --- |
| @fastify/ajv-compiler | 4.0.7 | MIT | LICENSE |
| @fastify/deepmerge | 3.2.2 | MIT | LICENSE |
| @fastify/error | 4.2.1 | MIT | LICENSE |
| @fastify/fast-json-stringify-compiler | 5.1.1 | MIT | LICENSE |
| @fastify/forwarded | 3.1.1 | MIT | LICENSE |
| @fastify/merge-json-schemas | 0.2.2 | MIT | LICENSE |
| @fastify/proxy-addr | 5.1.2 | MIT | LICENSE |
| @pinojs/redact | 0.4.0 | MIT | LICENSE |
| abstract-logging | 2.0.1 | MIT | Metadata/README link only; no bundled full text |
| ajv-formats | 3.0.1 | MIT | LICENSE |
| atomic-sleep | 1.0.0 | MIT | LICENSE |
| avvio | 9.3.1 | MIT | LICENSE |
| cookie | 2.0.1 | MIT | LICENSE |
| dequal | 2.0.3 | MIT | license |
| fast-decode-uri-component | 1.0.1 | MIT | LICENSE |
| fast-json-stringify | 7.1.2 | MIT | LICENSE |
| fast-querystring | 1.1.2 | MIT | LICENSE |
| fast-uri | 4.2.3 | BSD-3-Clause | LICENSE |
| fastify | 5.12.7 | MIT | LICENSE |
| fastq | 1.20.3 | ISC | LICENSE |
| find-my-way | 9.10.0 | MIT | LICENSE |
| ipaddr.js | 2.5.0 | MIT | LICENSE |
| json-schema-ref-resolver | 3.0.1 | MIT | LICENSE |
| light-my-request | 6.8.0 | BSD-3-Clause | LICENSE |
| on-exit-leak-free | 2.1.2 | MIT | LICENSE |
| pino | 10.4.0 | MIT | LICENSE |
| pino-abstract-transport | 3.0.0 | MIT | LICENSE |
| pino-std-serializers | 7.1.0 | MIT | LICENSE |
| process-warning | 5.1.1 | MIT | LICENSE |
| quick-format-unescaped | 4.0.4 | MIT | LICENSE |
| real-require | 1.0.0 | MIT | LICENSE.md |
| ret | 0.5.1 | MIT | LICENSE |
| reusify | 1.1.0 | MIT | LICENSE |
| rfdc | 1.4.1 | MIT | LICENSE |
| safe-regex2 | 5.1.2 | MIT | LICENSE |
| safe-stable-stringify | 2.5.0 | MIT | LICENSE |
| secure-json-parse | 4.1.1 | BSD-3-Clause | LICENSE |
| semver | 7.8.5 | ISC | LICENSE |
| set-cookie-parser | 3.1.4 | MIT | LICENSE |
| sonic-boom | 4.2.1 | MIT | LICENSE |
| split2 | 4.2.0 | ISC | LICENSE |
| thread-stream | 4.2.0 | MIT | LICENSE |
| toad-cache | 3.7.4 | MIT | LICENSE |

Open backend notice-text gap: `abstract-logging` 2.0.1 declares MIT and its README
links to the author's MIT-license page, but its installed package contains no
complete license/copyright text. Preserve package notices when distributing the
backend and obtain a version-matched upstream notice before claiming complete
backend distribution notice readiness. A permissive metadata result does not
close this gap. API logging is disabled independently of this license inventory.

### Android notice packaging

`tools/android-notices.mjs` reads locked offline Cargo metadata, installed Rust
library/toolchain notices, and the actual Gradle `releaseRuntimeClasspath`
artifact inventory generated by `:app:runtimeComponentInventory`. It reads local
POM license declarations and preserves embedded LICENSE/NOTICE/COPYRIGHT texts,
including nested `classes.jar` notices, without extracting source or fetching
reference URLs. Apache-declared Android dependencies lacking embedded license
text are labelled distinctly and accompanied by the standard Apache-2.0 text
from the installed Rust documentation. Other missing notices/POMs or unsupported
inspection cases remain explicit gaps in `inventory.json`.

The generated files live under ignored `target/android-assets/notices/` and are
included through the native app's assets source directory. They include the
combined `THIRD_PARTY_NOTICES.txt`, separate Rust engine/runtime/Android texts,
file hashes and gap inventory. A successfully generated inventory is not complete
legal clearance. `--require-complete` fails when any recorded gap remains. The
combined native Settings viewer must expose the full generated text without
silently truncating it. The Rust notice list is a conservative locked-workspace
superset; toolchain license resources are also a distribution superset, not an
assertion that all components mentioned in those notices are linked into the app.

Do not infer a bundled SQLite native engine from a Room dependency alone. Any
actual SQLite-bundled artifact must be reconciled separately, including its
public-domain notice; use of Android framework SQLite does not redistribute the
OS implementation. Final Gradle artifact counts/licenses and notice sizes are
recorded only after actual dependency resolution and packaging below.

### Actual resolved Android notice inventory (2026-10-21)

Executed locally against `releaseRuntimeClasspath`: **92 Android artifacts** and
**68 Rust registry packages**. License coverage observed for the Android artifacts:
54 contain embedded notice texts, 37 use local POM Apache-2.0 declarations plus
the packaged standard Apache text, and 1 uses a pinned exact upstream license.
The remaining generated inventory gap is the build-only `jni-sys-macros` notice
text described above. This does not establish a complete runtime reachability
SBOM, legal clearance or absence of other release obligations.

The Google Maven `datastore-preferences-external-protobuf` 1.1.7 jar declares
BSD-3-Clause but omits the full notice. Its public RuntimeVersion constants were
read as 4.28.2; the exact inspected jar SHA-256 is
`fcb3f373cf743426c85ebb0d3cc644aefaf26f39b7fa55bb90238d41b689eca7`.
The matching upstream Protobuf v28.2 LICENSE was retrieved from
https://raw.githubusercontent.com/protocolbuffers/protobuf/v28.2/LICENSE,
SHA-256 `6e5e117324afd944dcf67f36cf329843bc1a92229a8cd9bb573d7a83130fea7d`,
and preserves copyright 2008 Google Inc. and the complete BSD-3-Clause terms.
The generator accepts this supplemental notice only for that exact artifact
coordinate and checksum. `--fetch-license-inputs` optionally retrieves only this
fixed legal text into ignored build inputs, retaining TLS/proxy/CA settings,
a 64 KiB bound and the reviewed checksum. It never fetches arbitrary POM URLs.
The published same-version sources jar was also inspected but was empty apart
from its manifest; it supplied no license text and is not presented as proof of
complete source reconciliation.

`com.google.guava:listenablefuture` 1.0 inherits its Apache-2.0 declaration from
the actually cached `guava-parent` 26.0-android POM; parent metadata is resolved
locally with bounded depth and without remote entity/reference fetching.
`androidx.sqlite:sqlite-android` and `sqlite-framework-android` are both 2.5.1;
no sqlite-bundled artifact is present in this resolved release graph. The OS
SQLite engine is not redistributed by including these framework wrappers.

| Resolved Android coordinate | Version | Local declared license | Packaged notice coverage |
| --- | --- | --- | --- |
| androidx.activity:activity-compose | 1.12.2 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.activity:activity-ktx | 1.12.2 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.activity:activity | 1.12.2 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.annotation:annotation-experimental | 1.4.1 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.annotation:annotation-jvm | 1.9.1 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.arch.core:core-common | 2.2.0 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.arch.core:core-runtime | 2.2.0 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.autofill:autofill | 1.0.0 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.collection:collection-jvm | 1.5.0 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.collection:collection-ktx | 1.5.0 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.compose.animation:animation-android | 1.10.1 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.compose.animation:animation-core-android | 1.10.1 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.compose.foundation:foundation-android | 1.10.1 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.compose.foundation:foundation-layout-android | 1.10.1 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.compose.material:material-ripple-android | 1.8.1 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.compose.material3:material3-android | 1.4.0 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.compose.runtime:runtime-android | 1.10.1 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.compose.runtime:runtime-annotation-android | 1.10.1 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.compose.runtime:runtime-retain-android | 1.10.1 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.compose.runtime:runtime-saveable-android | 1.10.1 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.compose.ui:ui-android | 1.10.1 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.compose.ui:ui-geometry-android | 1.10.1 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.compose.ui:ui-graphics-android | 1.10.1 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.compose.ui:ui-text-android | 1.10.1 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.compose.ui:ui-tooling-preview-android | 1.10.1 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.compose.ui:ui-unit-android | 1.10.1 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.compose.ui:ui-util-android | 1.10.1 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.concurrent:concurrent-futures | 1.1.0 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.core:core-ktx | 1.17.0 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.core:core-viewtree | 1.0.0 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.core:core | 1.17.0 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.core:core | 1.17.0 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.customview:customview-poolingcontainer | 1.0.0 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.datastore:datastore-android | 1.1.7 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.datastore:datastore-core-android | 1.1.7 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.datastore:datastore-core-okio-jvm | 1.1.7 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.datastore:datastore-preferences-android | 1.1.7 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.datastore:datastore-preferences-core-android | 1.1.7 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.datastore:datastore-preferences-external-protobuf | 1.1.7 | BSD-3-Clause | pinned-upstream-license-text |
| androidx.datastore:datastore-preferences-proto | 1.1.7 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.documentfile:documentfile | 1.0.0 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.dynamicanimation:dynamicanimation | 1.0.0 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.emoji2:emoji2 | 1.4.0 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.graphics:graphics-path | 1.0.1 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.interpolator:interpolator | 1.0.0 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.legacy:legacy-support-core-utils | 1.0.0 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.lifecycle:lifecycle-common-java8 | 2.9.4 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.lifecycle:lifecycle-common-jvm | 2.9.4 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.lifecycle:lifecycle-livedata-core-ktx | 2.9.4 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.lifecycle:lifecycle-livedata-core | 2.9.4 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.lifecycle:lifecycle-livedata | 2.9.4 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.lifecycle:lifecycle-process | 2.9.4 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.lifecycle:lifecycle-runtime-android | 2.9.4 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.lifecycle:lifecycle-runtime-compose-android | 2.9.4 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.lifecycle:lifecycle-runtime-ktx-android | 2.9.4 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.lifecycle:lifecycle-viewmodel-android | 2.9.4 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.lifecycle:lifecycle-viewmodel-compose-android | 2.9.4 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.lifecycle:lifecycle-viewmodel-ktx | 2.9.4 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.lifecycle:lifecycle-viewmodel-savedstate-android | 2.9.4 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.lifecycle:lifecycle-viewmodel | 2.9.4 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.loader:loader | 1.0.0 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.localbroadcastmanager:localbroadcastmanager | 1.0.0 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.navigationevent:navigationevent-android | 1.0.1 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.navigationevent:navigationevent-compose-android | 1.0.1 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.print:print | 1.0.0 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.profileinstaller:profileinstaller | 1.4.0 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.room:room-common-jvm | 2.7.2 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.room:room-runtime-android | 2.7.2 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.savedstate:savedstate-android | 1.3.2 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.savedstate:savedstate-compose-android | 1.3.2 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.savedstate:savedstate-ktx | 1.3.2 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.sqlite:sqlite-android | 2.5.1 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.sqlite:sqlite-framework-android | 2.5.1 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.startup:startup-runtime | 1.1.1 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.tracing:tracing | 1.2.0 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.transition:transition | 1.6.0 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.versionedparcelable:versionedparcelable | 1.1.1 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| androidx.window:window-core-android | 1.5.0 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| androidx.window:window | 1.5.0 | The Apache Software License, Version 2.0 | embedded-notice-texts |
| com.google.guava:listenablefuture | 1.0 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| com.squareup.okio:okio-jvm | 3.4.0 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| org.jetbrains:annotations | 23.0.0 | The Apache Software License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| org.jetbrains.kotlin:kotlin-android-extensions-runtime | 1.9.22 | The Apache License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| org.jetbrains.kotlin:kotlin-parcelize-runtime | 1.9.22 | The Apache License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| org.jetbrains.kotlin:kotlin-stdlib-jdk7 | 1.8.0 | The Apache License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| org.jetbrains.kotlin:kotlin-stdlib-jdk8 | 1.8.0 | The Apache License, Version 2.0 | pom-declaration-and-standard-Apache-text |
| org.jetbrains.kotlin:kotlin-stdlib | 2.3.0 | Apache-2.0 | pom-declaration-and-standard-Apache-text |
| org.jetbrains.kotlinx:kotlinx-coroutines-android | 1.10.2 | Apache-2.0 | pom-declaration-and-standard-Apache-text |
| org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm | 1.10.2 | Apache-2.0 | pom-declaration-and-standard-Apache-text |
| org.jetbrains.kotlinx:kotlinx-serialization-core-jvm | 1.8.1 | Apache-2.0 | pom-declaration-and-standard-Apache-text |
| org.jetbrains.kotlinx:kotlinx-serialization-json-jvm | 1.8.1 | Apache-2.0 | pom-declaration-and-standard-Apache-text |
| org.jspecify:jspecify | 1.0.0 | The Apache License, Version 2.0 | pom-declaration-and-standard-Apache-text |

The generated combined `THIRD_PARTY_NOTICES.txt` measured **2,896,041 bytes**,
SHA-256 `cb134641e1c7eb399f99def25f3cfe0832cbdff941d027e0eae135263e2354f5`,
at this execution. Actual final APK inclusion and downloaded-artifact matching
remain separate release evidence. Fixture tests cover exact copyright retention,
missing-license gap reporting, safe paths, local parent POM inheritance, nested
AAR notices, DTD/entity rejection and supplemental-license checksum failure.
