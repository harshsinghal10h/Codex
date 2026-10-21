<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# Bootstrap core behavior

evidence_ref: docs/inputs/operator-brief.md
evidence_class: E4
retrieval_date: 2026-10-19
ledger: COR-06, PLT-CLI
status: specification for the narrow Task 0 bootstrap

This original behavior specification describes the operator's requested double-space example. It uses no competitor implementation, outputs, or assets. The analyst supplies this specification before the builder implements it. Task 0 expressly requests both this bootstrap and its implementation in one deliverable; ADR-0002 records the narrow exception to the later separate-PR feature workflow.

## Input and scope

Input is a valid UTF-8 plain-text document, a document version, and an explicit supported locale/settings contract. No network or model is required. Only plain text is supported at this milestone: Markdown, HTML, LaTeX, code syntax, URLs, and semantic whitespace exclusions are not parsed. Do not claim support for structured-text safety (COR-10).

## Original double-space rule

Rule ID `NW-SPACE-001` belongs to COR-06, category correctness, subcategory spacing, severity 1. Find each maximal run of two or more consecutive ASCII spaces U+0020. Produce one suggestion per run, including leading and trailing runs. The suggested replacement is exactly one U+0020. Single spaces, tabs, newlines, carriage returns, nonbreaking spaces, and all other Unicode spaces are not themselves matched. These characters separate runs. Three spaces yield one suggestion covering all three spaces, never two overlapping suggestions. The original field is the entire matched run. Explanation copy is authored for this project and neutrally describes reducing repeated spaces.

Positive examples: `One  two` -> `One two`; `One   two` -> `One two`; `  Leading` -> ` Leading`; `Trailing  ` -> `Trailing `. Negative examples: `One two`; `One\ttwo`; `One\u00a0\u00a0two`; empty input; a single ASCII space. Newline and tab examples use actual characters in tests, not the displayed escape notation. Mixed input `a  \t  b` contains two separate suggestions.

## Offsets and output

Every span is half-open [start, end), computed in the original unchanged document. Emit UTF-8 byte, UTF-16 code-unit, and Unicode scalar ranges; clients must not calculate one representation from another. Non-BMP characters count as one scalar and two UTF-16 units. Combining sequences and ZWJ sequences retain their original bytes and scalars; offsets must not invent grapheme splitting within matched ASCII runs. Reject invalid byte boundaries and ranges in public conversion helpers. A valid UTF-8 input cannot contain a lone surrogate; invalid input at a CLI boundary must fail clearly rather than silently replacing bytes.

Each output conforms to `packages/protocol/schema/suggestion.schema.json`: stable suggestion/rule IDs, category, subcategory, severity, all offsets, original, replacement text/confidence/kind, explanation, flags, source, document version, locale, goals, provenance, and telemetry class. Severity is bounded 1–3; confidence is bounded 0–1. Source is rule, provenance is own, telemetry class is none. Repeated checks with identical input and settings produce identical output in document order. The producer copies the supplied document version, enabling callers to discard stale results. There is no claim of incremental analysis, cancellation, or locale-specific grammar yet.

## Interfaces and safety

`nw check FILE --format json` reads the named file and prints a JSON report only as explicitly requested by its caller. Invalid file/UTF-8/options fail with nonzero status and a concise diagnostic. Outputting requested analysis to stdout is distinct from background logging; no text is sent to a service, telemetry, or persistent log. `nw rules test` validates the real YAML rule examples. `nw schema` emits the contract schema. `nw bench` reports local measurements and limits without claiming a universal performance pass.

The Node WASM interface must produce the same semantic suggestions and offsets as native Rust for ASCII, non-BMP emoji, combining-mark, and RTL examples. WASM smoke tests must instantiate the compiled module; a static file-presence check is insufficient.

## Required checks

Positive and negative rule cases; malformed input/options; leading/trailing and multiple independent runs; long runs; CRLF/tab/NBSP separation; emoji, ZWJ, combining, RTL and zero-width neighbors; correct original text and replacement; deterministic output; document-version preservation; schema/type regeneration; parity between native and compiled WASM. Include offset round trips for every valid character boundary and rejection of invalid boundaries/out-of-range spans. Any unexecuted check stays unknown. Local tests never promote a ledger row to verified without CI evidence.
