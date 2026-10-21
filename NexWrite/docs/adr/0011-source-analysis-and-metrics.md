<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# ADR-0011: additive source analysis and conservative metric estimates

Status: accepted for a bounded M1 slice; implementation is a separate review unit.
Date: 2026-10-20. Evidence: docs/inputs/operator-brief.md (E4), original analyst specifications.

## Context

The user asked to continue after Task 0. The next M1 slice needs Unicode segmentation, a safe partial structured-text path and readable metric data, while preserving the bootstrap check interfaces. A small real editor demo can consume the same contract after the L1 implementation; it does not authorize the entire M2 product. Specification authors must not implement their feature in the same task. Current GitHub access constraints require separate local branches/commits and reviewable fallback artifacts when separate PR publication remains unavailable.

## Options

1. Replace the existing check result with an expanded report and analyze Markdown as plain text: short implementation, breaks the documented API and can damage code/hard breaks.
2. Build a full rendered Markdown/HTML/LaTeX source-map and multilingual linguistic engine now: broader semantics, exceeds this slice and adds unvalidated offset/data assumptions.
3. Add an AnalysisResult contract, retain original coordinates, use a maintained source-offset Markdown parser and conservative exclusions, and expose explicitly gated English estimates.

## Decision

Choose option 3. docs/spec/m1-text-analysis.md defines the exact additive contract and original fixtures; docs/spec/m1-editor-demo.md separately defines a minimal M1 consumer. The analyst unit contains the cross-lane behavior/contract specifications before any new core/UI implementation. L1 and then L3 implementation use distinct branches/review units, keeping the S2 role wall and S3 contract-before-consumer ordering. Only COR-10, CLA-04, INS-02, DOC-01, DOC-06 and COR-11 may be advanced in these bounded slices; other rows remain planned, and local success never becomes CI verification.

Use the existing pinned unicode-segmentation library for reproducible UAX #29 boundaries. Select pulldown-cmark 0.13.4 with default features disabled, subject to actual E3 license/advisory/lockfile review before installation. The registry checksum identified during the coordinator's read-only lookup is e9f068eba8e7071c5f9511831b44f32c740d5adf574e990f946ddb53db2f314e; installation must still use Cargo's authoritative registry integrity verification. Never change checksums or suppress advisories to install it.

Limit the new analyze path to 1 MiB of canonical UTF-8. Preserve legacy check/schema behavior. Required report arrays reuse Span/Suggestion; unavailable formula scores are absent from scores and explained in warnings, avoiding nullable codegen expansion. Markdown is source-prose fragments with whole links/images/code/HTML and syntax exclusions; no joining across gaps. Any protected Markdown content gates off English formulas. Empty/unsupported/short prose retains defined counts and finite timing data without invented readability scores. English formulas are original, unrounded, unclamped estimates using a disclosed heuristic; no language detection, calibrated grading or full structured-text safety is claimed.

## Consequences

The bounded pipeline is useful and testable without a dictionary, ML model, server or account. Contract generation needs an explicit two-schema registry with checked cross-schema references. Conservative Markdown omissions can reduce or fragment counts; warnings communicate this rather than pretending rendered-editor parity. Unicode/default abbreviation behavior and English syllables remain limited. INS-02 requires an actual panel, and DOC-01/06/COR-11 require actual UI behavior, before their implementation statuses advance. No future platform or entire milestone is completed by the demo.

The maintained Markdown parser is preferable to an ad hoc complete grammar, but source offsets and malformed recovery still require original fixture tests. A separate 1 MiB input cap contains this report path; it does not silently change the old check API or establish hostile-service resource safety. The editor's text remains local; real worker execution, stale versions, cancellation, safe apply/undo, storage disclosure and keyboard/IME checks need runtime evidence in its own implementation unit.

## Revisit trigger

Revisit when rendered-source mappings, full HTML/LaTeX/code-comment support, a new locale, abbreviation tailoring, calibrated syllable data, larger documents/incremental analysis, or a parser advisory requires changed assumptions. Record new source/evaluation evidence and update the versioned contract/spec before consumer changes. Current legal/trademark/CI/platform unknowns remain open and are not waived by this continuation.
