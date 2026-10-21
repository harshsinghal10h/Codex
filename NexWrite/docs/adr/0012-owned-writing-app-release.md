<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# ADR-0012: Owned writing application and Android release path

Status: accepted analyst direction; implementation/runtime evidence pending.
Date: 2026-10-21. Evidence: E4 user correction in this task,
docs/inputs/operator-brief.md and docs/MASTER_PROMPT.md; E3 public ClearPDF source
as a licensed visual-organization reference. Behavior specification:
docs/spec/owned-android-ai-release.md.

## Context

The earlier delivered ClearPDF APK belongs to that project's PDF application.
The user's requested product is NexWrite, their AI writing assistant with an
interface informed by that project's card/navigation organization, own Android
package, source in their GitHub repository and an owned APK release link. The
existing NexWrite implementation is an original Rust analysis core/CLI/WASM
with a local web editor. No live backend, native APK or complete all-OS product
is established by that state. The latest user instruction authorizes continuing
beyond the prior bounded M1 scope while keeping honest feature/evidence gates.

## Options

1. Redistribute or rename the upstream PDF APK: fast download but wrong product,
   unchanged PDF features and no owned writing architecture.
2. Replace the native roadmap with a launcher containing only a web page:
   shortest initial packaging path, but it cannot substantiate the requested
   native keyboard, PROCESS_TEXT, sensitive-field or all-OS capabilities.
3. Build owned native Android UI and entry points over shared engine/contracts,
   alongside responsive web and a real opt-in modular backend; use Apktool for
   inspection of owned artifacts and publish separate reviewed source/release units.

## Decision

Choose option 3. Keep NexWrite branding, namespaces, tokens and original UI copy.
Use the reference's broad interaction patterns as an input to original design;
do not import its PDF app identity, signing key, binary or product screenshots.
Native Kotlin/Compose targets the S8.4 module/IME/PROCESS_TEXT/share behaviors.
Rust remains the local engine source of truth and canonical AnalysisResult is
unchanged. Prefer UniFFI; a small reviewed JNI JSON bridge is a permissible
initial adapter with parity/strict-boundary tests and an explicit migration path.
Any other engine host requires evidence and a separate recorded decision.

The backend follows ADR-0009 modular boundaries. OpenAPI 3.1 contracts land before
consumers. Local document Save is explicit and does not grant cloud consent.
The minimum opt-in gateway implements OpenAI-compatible and Gemini adapters;
provider keys/base URLs are runtime server configuration and clients never select
arbitrary upstream URLs. No configured provider means an honest unavailable
state. A personal stateless gateway is not a complete managed multiuser backend;
account/cloud-doc capabilities require real auth, tenant isolation and persistence.

Compile features from owned source. Use a pinned runnable Apktool on the owned
APK for manifest/resource/permission/legal-asset inspection, retaining commands
and limits. Keep persistent signing material outside Git; deliver public cert
fingerprint, checksum, notices and exact provenance with the signed release.
Publish source branches and GitHub Release assets using the user's authorized
repository. Never replace a missing publication result with a guessed URL or an
upstream project's APK. Publishing remains subject to actual access, not to an
inferred token value or a configured-but-unapplied network draft.

## Consequences

This makes the Android download a real NexWrite implementation and establishes
shared behavior for other platforms. It requires more implementation and testing
than renaming a binary. Source availability/compilation is independent from live
AI provider activation, public backend hosting, release write access, store
signing and physical-device evidence. Complete every unblocked source/build task,
record each actual external blocker and preserve independently recoverable work.
An Android preview is labelled by its real capabilities; it does not close full
M1, M2, M5 or the remaining all-OS roadmap by itself. No CI verification or legal,
accessibility, device or production-readiness claim arises from this ADR.

## Revisit trigger

Revisit bridge/hosting/package choices if measured memory/latency, SDK access,
actual platform APIs, provider data policy, signing/release operations or UI
accessibility evidence contradict them. A fallback must retain version/offset,
consent and truthful capability behavior and be described in the compatibility
matrix before it changes user-visible guarantees.
