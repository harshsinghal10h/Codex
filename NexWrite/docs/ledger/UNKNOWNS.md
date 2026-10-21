<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# Unknowns, spikes and risk register

All entries below are **open** until comparable evidence closes them. Observation, derivation, inference and unknown are distinct. Failed, skipped, simulated, truncated and unavailable checks do not roll up to pass. No real-device or CI verification is inferred from source inspection or local builds. Ledger references are semicolon-separated IDs; IDs remain stable when closed and gain an evidence/resolution record.

## Bootstrap unknowns

| ID | State | Question / present limit | Exact next evidence or remedy |
| --- | --- | --- | --- |
| UNK-BOOT-01 | open | No real NexWrite CI run URL or published PR has established feature verification | Publish the reviewed branch through an available authorized path, run the pinned CI matrix, record run URL and required-case results; keep rows in-progress/planned until then |
| UNK-BOOT-02 | open | Current public terms excerpt could not be re-verified: official robots proxy CONNECT returned HTTP403/curl56 | Permit the bounded official robots/terms host through environment settings or obtain a lawful dated operator excerpt, obey robots/rate limit, record actual retrieval and qualified review |
| UNK-BOOT-03 | open | NexWrite name/mark clearance not performed | Complete dated trademark/store/package/domain searches and qualified review; select Nex- replacement if necessary before public branding |
| UNK-BOOT-04 | open | Own-artifact REA static JS audit was executed, but semantic graph coverage remains conservative/unknown; native WASM internals/runtime privacy are outside that audit | The final audit hash now matches the safe-parser bundle; investigate the 199 semantic unknowns with targeted runtime evidence before broader release, and keep native/WASM internals and runtime privacy separate from static JS inventory |
| UNK-BOOT-05 | open | Font declarations are not rendered/audited platform typography or complete asset distribution notices | Preserve OFL/author notices from installed font packages; build self-hosted delivery and perform rendering/a11y checks before a UI release |
| UNK-BOOT-06 | open | Dependency metadata checks do not establish complete transitive license/SBOM/attestation readiness | Generate and review locked resolved dependency inventory, licenses/notices, SBOM and artifact provenance before release |
| UNK-BOOT-07 | open | Development compose configuration is not evidence of running services or tested persistence/isolation | Start only needed services with configured CA/proxy and local credentials; run health/isolation/persistence checks; never call configuration validation runtime verification |
| UNK-BOOT-08 | open | macOS/Windows/mobile/browser/device compatibility is untested for this new bootstrap | Run documented CI OS jobs and later emulator/device/assistive-tech matrix; label simulated and physical results separately |
| UNK-BOOT-09 | open | No monitored private vulnerability disclosure channel/support commitment exists | Owner enables and documents a private monitored channel before production users |
| UNK-BOOT-10 | open | Generated TypeScript compiles locally and actual Node/WASM parity passes; Kotlin/Swift declarations and native bridges remain uncompiled/unintegrated | Compile Kotlin/Swift generated types under pinned consuming toolchains and exercise native bridge fixtures before claiming those integrations |
| UNK-BOOT-11 | open | Local synthetic benchmark does not establish S12 desktop/phone p95 budgets | Run representative sizes, repeated percentiles and target-device/browser measurements with hardware/settings recorded |
| UNK-BOOT-12 | open | Double-space example is a partial COR-06 slice, with no structured-text semantics, full grammar engine, fuzz campaign, host apply/undo or resource caps for services | Complete M1 rules/tokenizer/structure scope and malformed/fuzz/property tests; add input/cancellation limits and host-safe apply/undo before broader exposure |

## S18 spikes (each must end with an ADR)

For each spike: record question, bounded method, evidence IDs, decision and revisit trigger; time-box the investigation. Static evidence cannot close a runtime question.

| ID | State | Spike / question | Comparable evidence / next action |
| --- | --- | --- | --- |
| UNK-SP-01 | open | Google Docs add-on versus extension overlay | Official API/canvas constraints and own prototype; ship reliable official sidebar first unless measured evidence supports overlay |
| UNK-SP-02 | open | Wayland overlay and IME path | GNOME/KDE/wlroots capability matrix; measure panel/portal and IBus/Fcitx5 prototypes |
| UNK-SP-03 | open | macOS accessibility trees in Electron/Chromium apps | Own AX harness on target versions, including AXManualAccessibility and permission behavior |
| UNK-SP-04 | open | Windows UIA coverage of Chromium, Electron, Office | UIA harness: TextPattern/TextEditPattern/ValuePattern, in-place edit/undo and elevated-window skip |
| UNK-SP-05 | open | iOS keyboard memory and Full Access capabilities | Physical-device measurements with Full Access off/on, secure-field fallback, context/memory limits |
| UNK-SP-06 | open | Google Play AccessibilityService policy | Re-read current official policy and record build separation before Play submission; Play ships keyboard/PROCESS_TEXT only |
| UNK-SP-07 | open | MV3 service-worker lifecycle versus WASM state | Own extension lifecycle/offscreen/worker test through suspend/restart with stale-state protection |
| UNK-SP-08 | open | Safari extension parity | Official API support table plus real Safari macOS/iOS build/runtime tests |
| UNK-SP-09 | open | Office range tracking without squiggles | Requirement-set matrix and own Locate/Apply/undo fixtures across Word/Outlook/PowerPoint clients |
| UNK-SP-10 | open | GEC training-data licensing | Dataset-by-dataset exact license and purpose review; prefer synthetic/public-domain/permissive data |
| UNK-SP-11 | open | On-device LLM feasibility per platform | Optional capability checks and actual latency/memory/privacy measurements; never required for local rule use |
| UNK-SP-12 | open | Plagiarism-provider terms and legality | Official API/provider terms and qualified review; explicit corpus coverage and SSRF constraints |
| UNK-SP-13 | open | AI-detector fairness on non-native writers | License-clean calibrated evaluation, fairness slices and uncertain labeled output; no accusation/proof claim |
| UNK-SP-14 | open | CSS Custom Highlight API support matrix | Current official support documentation plus own Chromium/Firefox/WebKit fallback fixtures |
| UNK-SP-15 | open | IME composition handling (CJK) | Own composition fixtures and platform/device testing; suppress analysis/edit during composition |
| UNK-SP-16 | open | RTL and bidi underline geometry | Own mixed-direction/emoji/line-wrap/zoom/scroll fixtures; compare measured geometry, not offset guesses |
| UNK-SP-17 | open | Store policy compliance for AI, keyboards and broad host permissions | Current official marketplace policy review, least-permission manifests and consent/disclosure evidence per store |
| UNK-SP-18 | open | Code signing and update-key management | Operator-supplied secure signing identities, rotation/rollback threat model and signed-update tests; no keys in repo |

## S18 risk seeds

| ID | State | Risk | Required mitigation/evidence |
| --- | --- | --- | --- |
| UNK-RISK-01 | open | Trademark and name clearance | TRADEMARK checklist and qualified review before public branding |
| UNK-RISK-02 | open | DMCA exposure from any provenance gap | Clean-room source records, original assets/specs, no unauthorized reference artifacts |
| UNK-RISK-03 | open | Dataset license contamination | License gate per corpus/dictionary/model; removal and retraining plan |
| UNK-RISK-04 | open | LLM provider terms and data retention | Provider/region/retention/consent review before enabling a real cloud route |
| UNK-RISK-05 | open | Accessibility-API fragility across OS updates | Capability tiers, regression harness and graceful fallback with honest compatibility notes |
| UNK-RISK-06 | open | Antivirus false positives on overlay apps | No injection/process hooks; signed original builds, measured distribution evidence |
| UNK-RISK-07 | open | App-store rejection | Minimal permissions, policy rechecks, disclosure and store-specific runbooks |
| UNK-RISK-08 | open | Cost of LLM calls at scale | Explicit opt-in, per-user/org budgets, bounded retries/caches and measured usage |

## Bounded M1 analysis unknowns (2026-10-20)

| ID | State | Question / present limit | Exact next evidence or remedy |
| --- | --- | --- | --- |
| UNK-M1-01 | open | Conservative Markdown source fragments do not establish rendered parity or full HTML/LaTeX/comment/URL/email/handle/hashtag awareness | Implement original protection fixtures with unchanged original offsets; keep excluded/fragmented warnings and score gates; add separately scoped source-map/platform evidence before broadening COR-10 |
| UNK-M1-02 | open | ASCII English syllable/readability heuristics are not calibrated pronunciation, language detection or a certified grade measure | Use independent formula fixtures and conservative short/non-ASCII/numeric gates now; acquire license-clean dialect/genre/fairness evaluation before changing claims |
| UNK-M1-03 | open | Default UAX #29 sentence/word rules have no English abbreviation tailoring or dictionary segmentation for other scripts | Pin dependency behavior and Unicode fixtures; implement and evaluate an original licensed abbreviation/dictionary layer in a separate slice |
| UNK-M1-04 | open | The 1 MiB input cap permits substantial output expansion for dense suggestions; current-host stress checks do not establish low-memory/mobile budgets | Retain measured dense-result limits, show at most 20 editor cards and keep real worker termination; separately design and test bounded/streamed report delivery before raising input limits or claiming mobile performance |
| UNK-M1-05 | open | The local textarea demo does not implement a rich editor, offline application shell, cross-tab conflicts, screen-reader certification or browser/device parity | Keep exact adopted-source and opt-in-storage tests, execute configured CI and broader assistive-technology/browser tests, then scope rich editing/offline synchronization separately |
| UNK-M1-06 | open | REA 6.4.0 retained partial web-bundle semantic coverage after its analyzer exhausted both 1 GiB and 4 GiB heap limits; Node bridge also retains semantic unknowns | Preserve raw own-artifact reports and exact limits, use runtime/source evidence for observed behavior, and rerun improved or separately scoped analysis before claiming comprehensive artifact coverage |

## Owned application release (2026-10-21)

| ID | State | Question / present limit | Exact next evidence or remedy |
| --- | --- | --- | --- |
| UNK-APP-01 | open | No physical Android/OEM, TalkBack, Switch Access, low-RAM or keyboard timing evidence exists; the local host has no KVM | Execute the committed emulator suite in supported CI and the documented physical-device matrix before broadening native compatibility/accessibility/performance claims |
| UNK-APP-02 | open | User chose to configure a backend later; no public HTTPS gateway, live provider keys, provider retention review or live-provider pass is established | Deploy the documented authenticated personal gateway, configure reviewed provider profiles and explicitly consent to a real request; keep cloud actions unavailable until then |
| UNK-APP-03 | open | Personal stateless gateway is not multiuser auth, cloud document storage, collaboration, billing or teams; iOS/native desktop/extensions are still roadmap | Implement separate specified persistence/auth/tenant and host slices with actual platform evidence; discovery must continue reporting accounts/cloud_docs false |
| UNK-APP-04 | open | Native JNI work already running cannot be interrupted; dense output expansion and mobile resource budgets remain unmeasured | Keep serialized 64 KiB editor/16 KiB IME bounds and stale-result guards; measure low-memory devices and design bounded report delivery before increasing limits |
| UNK-APP-05 | open | First actual baseline CI run failed browser and macOS tests; log/artifact redirects were denied by managed network policy | Preserve run 39550056484 outcomes; inspect bounded failure annotations from the improved workflow and obtain passing results for the release commit. APFS filename correction is source-based diagnosis until comparable CI runs |
