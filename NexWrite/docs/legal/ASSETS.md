<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# Asset provenance

Task 0 commits original design-token source, generated text/CSS values and font self-hosting configuration. The tokens are authored from the operator's requested palette and layout specification. No logo, icon set, illustration, screenshot, copied CSS or binary font file is included. No archive asset is reused. This is an observation about the bootstrap, not authorization for future assets.

Configured font sources are Inter, Space Grotesk and JetBrains Mono through pinned @fontsource-variable packages 5.3.0 (OFL-1.1 registry metadata; THIRD_PARTY.md). Builds should self-host needed WOFF2 files from those licensed installed packages, include each package's copyright/OFL notices, and use system fallback stacks until available. No remote Google Fonts request is needed. Package dependencies are not first-party font authorship. Reserved font names and modifications need review before a renamed/subsetted font release. Full platform typography rendering remains untested.

For each new asset, record path/hash, author, source URL/retrieval date, license/notice, modifications, allowed distribution and reviewer. Binary assets without a completed row fail provenance checks. Reject copied reference marks/trade dress. Original brand mark/platform icons remain TODO(ledger:PLT-WEB); name/mark clearance remains UNK-BOOT-03.

Generated-token contrast checks cover only the specified token pairs; they do not constitute a screen or WCAG 2.2 AA audit. The bright accent is not a universal text color on light backgrounds; use an approved darker ramp when contrast requires it.

## M1 browser assets (2026-10-20)

The local demo now imports the three existing pinned font packages in `apps/web/src/main.tsx`. Vite emits local WOFF2 assets from those installed packages; no remote font provider is requested, no archive font is imported, and no font binary is added to tracked source. The package font files are used without modification. Their authorship and OFL licenses remain upstream's; they are not first-party typeface designs.

| Generated font source / output family | Author / original source | License | Local distributed notice |
| --- | --- | --- | --- |
| @fontsource-variable/inter@5.3.0/files/*.woff2 → apps/web/dist/assets/inter-*.woff2 | Copyright 2016 The Inter Project Authors; https://github.com/rsms/inter ; npm distribution https://github.com/fontsource/font-files/tree/main/fonts/variable/inter | OFL-1.1, unmodified | /notices/inter-OFL.txt |
| @fontsource-variable/space-grotesk@5.3.0/files/*.woff2 → apps/web/dist/assets/space-grotesk-*.woff2 | Copyright 2020 The Space Grotesk Project Authors; https://github.com/floriankarsten/space-grotesk ; npm distribution https://github.com/fontsource/font-files/tree/main/fonts/variable/space-grotesk | OFL-1.1, unmodified | /notices/space-grotesk-OFL.txt |
| @fontsource-variable/jetbrains-mono@5.3.0/files/*.woff2 → apps/web/dist/assets/jetbrains-mono-*.woff2 | Copyright 2020 The JetBrains Mono Project Authors; https://github.com/JetBrains/JetBrainsMono ; npm distribution https://github.com/fontsource/font-files/tree/main/fonts/variable/jetbrains-mono | OFL-1.1, unmodified | /notices/jetbrains-mono-OFL.txt |

The build helper copies each complete package LICENSE, including copyright and OFL text, into generated `apps/web/public/notices/`; the footer exposes local links and Vite copies these public files into the built distribution. Package text and generated public notice hashes were compared on 2026-10-20:

| Notice | SHA-256 of unmodified source and generated public text |
| --- | --- |
| inter-OFL.txt | 3b0a5fca3d17942cde889069889dedbbbd075e9b599968c82a95f4d944e9b345 |
| space-grotesk-OFL.txt | 18a4de52385f6b988782639d5d0cc1326e5a8c2de9a7f01d7b20d9aedcc60943 |
| jetbrains-mono-OFL.txt | 403581b69dac5cff4079205e01c6b467e56af449ecbd7247693ddb1baafa005b |

React, React DOM and Scheduler MIT notices are also copied as `react-MIT.txt`, `react-dom-MIT.txt` and `scheduler-MIT.txt`; each observed text has SHA-256 `da6d3703ed11cbe42bd212c725957c98da23cbff1998c05fa4b3d976d1a58e93`. The generated `/notices/engine-third-party.txt` carries locked Rust registry license/notice texts; see THIRD_PARTY.md for its scope and hash. Notice packaging is observed locally and does not establish a complete release SBOM or distribution review.

The generated web-target engine bindings/WASM live under `apps/web/public/engine/` and are compiled from our Rust sources plus the declared third-party dependency graph. They retain the corresponding mixed license obligations; generated bindings are not relabeled as wholly first-party proprietary code. Browser screenshots under `apps/web/test-results/` are own-artifact captures of the synthetic demo and are test outputs, not imported UI artwork. These exact generated directories and `public/notices/` are ignored build/test outputs; product-source provenance remains checked separately from built-artifact review.

No new external illustration, logo or icon package is introduced by this M1 slice. The original mark/name clearance and future modified/subsetted font review remain open as recorded above. Automated contrast and accessibility tests remain bounded evidence and are not a claim of full WCAG conformance or platform typography coverage.

| Tracked asset | Author / source | License | Review / distribution |
| --- | --- | --- | --- |
| apps/web/public/favicon.svg | Original NexWrite project mark drawn in this implementation, 2026-10-20; no external asset source | LicenseRef-NexWrite-Proprietary | First-party local demo favicon; SHA-256 `6fe4852dec73dffba2ab0e3c168e9a603de347089db0f0921ad3eb9feba1ef5a`; mark/name clearance remains UNK-BOOT-03 |
