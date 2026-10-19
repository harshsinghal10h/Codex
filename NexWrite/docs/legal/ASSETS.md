<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# Asset provenance

Task 0 commits original design-token source, generated text/CSS values and font self-hosting configuration. The tokens are authored from the operator's requested palette and layout specification. No logo, icon set, illustration, screenshot, copied CSS or binary font file is included. No archive asset is reused. This is an observation about the bootstrap, not authorization for future assets.

Configured font sources are Inter, Space Grotesk and JetBrains Mono through pinned @fontsource-variable packages 5.3.0 (OFL-1.1 registry metadata; THIRD_PARTY.md). Builds should self-host needed WOFF2 files from those licensed installed packages, include each package's copyright/OFL notices, and use system fallback stacks until available. No remote Google Fonts request is needed. Package dependencies are not first-party font authorship. Reserved font names and modifications need review before a renamed/subsetted font release. Full platform typography rendering remains untested.

For each new asset, record path/hash, author, source URL/retrieval date, license/notice, modifications, allowed distribution and reviewer. Binary assets without a completed row fail provenance checks. Reject copied reference marks/trade dress. Original brand mark/platform icons remain TODO(ledger:PLT-WEB); name/mark clearance remains UNK-BOOT-03.

Generated-token contrast checks cover only the specified token pairs; they do not constitute a screen or WCAG 2.2 AA audit. The bright accent is not a universal text color on light backgrounds; use an approved darker ramp when contrast requires it.
