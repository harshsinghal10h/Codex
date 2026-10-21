<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# M1 local editor demonstration

| Field | Value |
| --- | --- |
| evidence_ref | docs/inputs/operator-brief.md |
| evidence_class | E4 |
| retrieval_date | 2026-10-20 |
| analyst scope | Original behavior specification, before application implementation |
| feature slices | COR-11, INS-02, DOC-01, DOC-06 |
| dependency | docs/spec/m1-text-analysis.md and its additive analysis contract |

This is the M1 browser demonstration of the local engine, not the M2 web
product. Its working surface is one editable plain-text source document with a
plain/Markdown format choice, spacing suggestions, and text metrics. A native
textarea is sufficient for this slice. Rich-text editing, accounts, cloud
analysis, collaboration, full grammar/spelling, and service integrations remain
outside scope. Each listed feature remains partial after implementation and
cannot become `verified` without matching CI evidence and required-case results.

## Visible experience

The initial screen identifies NexWrite and says that analysis runs on this
device. It identifies the currently available spacing rule and calls readability
values estimates. The screen contains a labelled source editor, labelled format
selector, suggestion list, metrics region, Undo, local text export, and a small
settings area for theme and local draft persistence. It has no inactive product
navigation or simulated account, payment, agent, or cloud controls.

The document starts empty unless the user previously opted into a valid saved
draft. An optional original example is inserted only by an explicit action;
it never replaces typed text automatically. Format choices are exactly the
formats accepted by the analysis contract. Markdown is source editing, not
an HTML renderer. Pasted markup and suggestions are displayed as text.

The interface uses the existing original dark/light design tokens and local
Fontsource assets with system fallbacks. Dark is the initial appearance; theme
selection is available without transmitting data. Use solid readable surfaces
as the baseline, restrained optional glass, linear gradients only, and no orbs.
Suggestion identity includes a textual category and an explanation; color
alone never conveys meaning.

## Analysis lifecycle and stale results

1. Load the actual compiled WASM module in a dedicated module Worker. No
   JavaScript replacement analyzer, mock response, network model, or server
   analysis fallback is permitted in this demonstration.
2. Each committed edit, format change, accepted replacement, Undo, and draft
   restore receives a new monotonically increasing document version. Analysis
   requests carry the exact source snapshot, format, version, and supported
   locale required by the additive contract.
3. Debounce committed edits briefly; a new change invalidates any pending timer
   and makes old results non-actionable immediately. A late response is applied
   only when its version and format match the latest requested snapshot.
   A mismatched response cannot replace suggestions, metrics, or status.
4. During IME composition, preserve the editor value and selection without
   analyzing or applying replacements. Composition completion schedules the
   committed value. Ordinary input handling and composition completion must
   not create two conflicting snapshots.
5. Validate the canonical input byte limit from the analysis specification and
   reject malformed UTF-16 before silently converting it through TextEncoder.
   The worker's own validation remains authoritative. The source stays editable
   when analysis is unavailable or input is invalid.

The UI must remain responsive while analysis runs. A busy state does not cover
the editor or move focus. DOM text operations use UTF-16 offsets from the
contract, never byte offsets or scalar offsets. The application does not
normalize Unicode, spaces, or Markdown delimiters in the adopted source. Native
textarea entry exposes LF line endings; this demo adopts that DOM value and
does not claim byte-preserving CRLF file import. Export and Undo preserve the
adopted source exactly. Raw CRLF preservation remains a separate CLI/WASM test.

## Suggestion actions and Undo

A suggestion card presents the affected text as escaped text, replacement,
category, and original explanation. Accept is enabled only for a current
response. Before replacing, check all of the following:

- The suggestion's document version matches the current source version.
- Its UTF-16 range is ordered and within the current string.
- The current substring equals the suggestion's `original` value exactly.
- The replacement belongs to that suggestion and is text, not executable markup.

If a precondition fails, leave the document unchanged, discard the stale action,
and request current analysis. A successful accept replaces only that range,
records the previous complete source/format for Undo, advances the version, and
reanalyzes. Adjacent text, line breaks, emoji sequences, combining marks, and bidi text
must survive unchanged. There is no automatic replacement while typing.

Undo restores the preceding accepted edit, with its exact source and format,
then advances the version and requests fresh analysis. Undo is disabled when no
accepted edit is available. If arbitrary intervening typing makes an accept-only
Undo history ambiguous, clear that history and disable the action; do not silently
discard subsequent typed work. Native text editing shortcuts remain usable.
After an action, retain a useful focus position in the editor or next suggestion.

An optional dismiss action affects only the current analysis snapshot and
clears when the source or format changes. Persistent ignore rules, custom
dictionaries, batch accept, and a complete history are future slices.

## Metrics and scope disclosures

Display actual data from the current analysis result: source/prose word and
sentence measures as defined by the contract, reading/speaking estimates, and
available readability scores. Show every omission or limitation affecting the
displayed scores, including short text, unsupported language/script, and
conservative Markdown exclusions. Do not substitute zero, a placeholder score,
or a synthesized successful result for an unavailable metric.

Keep raw metric values available to tests; visual rounding is a display choice.
Grade/formula results are estimates rather than grades assigned to a person.
Explain that one spacing rule and bounded metrics do not establish full M1 rule
coverage or mature writing assistance. No all-platform, legal-clearance, or
accessibility-certification claim is implied by this demonstration.

## Privacy and draft storage

The initial setting is memory-only: no document text is written to browser
storage until the user explicitly enables local draft saving. The setting says
that the draft is stored in this browser profile, is not an encrypted vault,
and may be visible to other users of the same profile. A persistent indicator
distinguishes memory-only, saving, saved locally, and storage-unavailable states.

When enabled, save only the current source, selected format, and a small versioned
record needed to restore them. Coalesce writes after committed edits. No document
text enters application logs, diagnostics, URLs, analytics, remote storage, or
other network requests. Theme preference may be stored separately without text.

Turning draft saving off removes the saved draft and persistence consent record;
the current editor text stays in memory. Clear local data removes the owned
draft, consent, and theme keys while preserving the current editor content and
leaving unrelated origin storage untouched. A reload after opting out must not
restore document text. A malformed, oversized, unknown-version, or invalid-format
stored record is not automatically imported; show a bounded explanation without
printing its contents. Storage denial or quota errors keep editing/analysis
working in memory and show the failed persistence state truthfully.

Text export is an explicit local download of the current valid source as UTF-8
text with an appropriate `.txt` or `.md` filename. It preserves the source and
does not transmit it. Revoke generated object URLs after use. Empty export may
be disabled with a clear reason. Export must not silently repair malformed
Unicode that the editor or engine has rejected.

## States and accessibility

Required actual states are empty, loading engine, ready, analyzing, no suggestions,
engine unavailable/unsupported, invalid or oversized input, and persistence error.
Engine failure cannot look like a successful document check. Retry, where offered,
reinitializes the real worker rather than generating a response. No permission,
rate-limit, paywall, or administrator state is fabricated without such a feature.

All controls have visible labels or accessible names, logical keyboard order,
visible focus, and sufficient target sizes. The editor has a persistent label;
placeholder text is supplemental. Status changes use a polite live region and
do not repeatedly announce the whole document or steal focus. Error explanations
are associated with the affected control. Headings and landmarks identify the
editor, suggestions, metrics, and settings. Category meaning is not color-only.

At widths from 320 px through desktop, controls remain reachable and the page
has no unintended horizontal overflow. Long source content scrolls within the
editor. Respect reduced motion, forced colors, text zoom, and user-selected
direction (`dir="auto"` or equivalent preserves mixed/RTL source). Fonts and
engine assets are served from the same origin; no font CDN is required.

## Acceptance evidence

Use an actual built browser application and real compiled worker/WASM. Existing
native/WASM tests alone do not prove this UI. Automated checks must be able to
fail for the following behavior:

| Case | Expected observation |
| --- | --- |
| Fresh context | Empty editable source; memory-only storage; real engine becomes ready |
| Plain text with a known double space | Current card appears from WASM and current metrics match native fixtures |
| Accept + Undo around emoji/line breaks/RTL | Only the indicated spacing changes; Undo restores exact adopted source; raw CRLF is tested at the core boundary |
| Edit before accepting / out-of-order worker response | Stale action/result is ignored; newer text and metrics remain current |
| IME composition | No replacement or committed analysis during composition; completion analyzes once |
| Markdown code, links, syntax, hard breaks | Protected source remains unchanged; exclusions/metric omissions are visible |
| Empty, short, numeric, non-Latin input | Bounded meaningful counts and truthful score omissions; no fake grades |
| Input over the contract limit / malformed value | Source remains editable, error is bounded, no stale successful action remains |
| Worker load/initialization failure | Actual unavailable state, editing/export of valid source still usable |
| Persistence off, on, reload, then off | Text is absent from storage until consent; enabled draft restores; opt-out prevents restore |
| Storage throws / malformed stored record | Memory editing survives and no invalid record silently replaces source |
| Clear local data | Only owned keys are removed; current source survives; unrelated test key remains |
| Export | Local file bytes equal the current source and no text-bearing request occurs |
| Network observation | Document edits create no external requests; first-party engine/font assets are bounded |
| Keyboard/320 px/reduced motion/forced colors | Controls remain operable, focus visible, no page overflow |
| Automated accessibility scan | No known serious/critical violations in supported tested states; preserve findings rather than suppress them |

Capture a screenshot of the implemented UI and actual browser/tool versions,
commands, outcomes, and scope. Measure sample analysis response timing, but do
not infer the S12 target-device budgets from a fast local demonstration. Axe or
similar automation plus keyboard smoke checks do not replace manual screen-reader,
zoom, contrast-over-glass, mobile-browser, or physical-device testing. Keep those
unexecuted outcomes unknown, and keep local browser results separate from CI.
