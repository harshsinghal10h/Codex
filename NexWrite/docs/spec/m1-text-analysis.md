<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# M1 source text analysis: segmentation, protection and metric estimates

evidence_ref: docs/inputs/operator-brief.md
evidence_class: E4
retrieval_date: 2026-10-20
ledger: COR-10, CLA-04; existing COR-06 integration
status: original analyst specification; implementation follows in a separate review unit

This bounded M1 slice adds a source-oriented analysis report. It does not complete COR-10, M1, a rich editor, language detection, or any platform integration. All behavior and fixtures below are original. INS-02's user interface is specified separately in m1-editor-demo.md.

## Inputs, compatibility and errors

Analysis accepts canonical valid UTF-8 text, an explicit `plain` or `markdown` input format, locale `en-US`, and an integer document version from 0 through 9007199254740991. `analyze` rejects text larger than **1048576 UTF-8 bytes**, before segmentation, rule execution or metric computation. Exactly that many bytes are allowed. Unsupported formats/locales, invalid UTF-8, invalid versions and malformed options are errors with no partial report. Empty text is valid. There are no network calls, telemetry, background text logs, model downloads or automatic edits.

The existing `Engine::check`, `nw check`, `check_json`, and `nw schema` retain their exact bootstrap behavior/output: a plain-text suggestion array, including maximal ASCII double-space runs in leading/trailing spaces, code-like input and Markdown hard-break-like input. The new protected analysis must not silently filter those legacy calls.

Add `nw analyze FILE --format json [--input-format plain|markdown] [--doc-version N] [--locale en-US]`; input format defaults to plain. Reject duplicate/unknown/missing option values. File paths retain OS-native handling. Add `nw schema analysis` to print the new schema; bare `nw schema` still prints Suggestion. A new WASM `analyze_json(text, doc_version, locale, input_format)` accepts four strictly typed arguments and returns the analysis JSON string. Reject coercible nonstrings, nonnumber versions, and unpaired UTF-16 surrogates before converting JS text to UTF-8. All legacy validation remains unchanged.

## Additive wire contract

Canonical file: `packages/protocol/schema/analysis.schema.json`. Its definitions reuse the existing canonical Suggestion and Span types through an explicit, validated two-schema registry. Do not duplicate or change Suggestion/Span semantics. Generate required Rust/TypeScript/Kotlin/Swift declarations; unsupported or ambiguous schema references fail generation. No nullable fields or fabricated zero scores are needed.

Every AnalysisResult field is required; undeclared properties are rejected:

| Field | Value |
| --- | --- |
| doc_version | Portable safe nonnegative integer supplied by caller |
| locale | en-US |
| input_format | plain or markdown |
| prose_regions | Ordered, disjoint Span array; each retained original source fragment |
| protected_regions | Ordered, disjoint array of objects with required span: Span and kind: ProtectionKind |
| tokens | Ordered array of objects with required span: Span and kind: TokenKind; this slice emits only word |
| sentences | Ordered Span array of analyzed source sentence fragments |
| paragraphs | Ordered Span array of analyzed source paragraph fragments |
| metrics | TextMetrics object below |
| suggestions | Existing Suggestion array, filtered only for this new analysis path |

ProtectionKind values are `code`, `html`, `image`, `link`, `reference_definition`, `ambiguous_markup`, `escape`, `entity`, `hard_break`, `indentation`, `syntax`. When protected ranges overlap or touch, coalesce their union; choose the earliest kind in that list for the coalesced label. No source content becomes prose through coalescing. TokenKind has only `word` in this slice; do not invent POS/lemma data.

TextMetrics has required nonnegative integer counts `source_utf8_bytes`, `source_utf16_units`, `source_scalar_count`, `source_grapheme_count`, `analyzed_scalar_count`, `word_count`, `sentence_count`, `paragraph_count`, `letter_count`, `digit_count`, `unique_word_count`, `syllable_covered_words`, `estimated_syllables`, `estimated_polysyllable_words`; required finite nonnegative numbers `reading_seconds`, `speaking_seconds`; and required arrays `scores` and `warnings`. Counts are portable integers no greater than 4294967295. `scores` contains required `{kind, value}` objects; value is a finite number, never NaN/infinity. Kinds, in stable output order, are `type_token_ratio`, `flesch_reading_ease`, `flesch_kincaid_grade`, `gunning_fog`, `smog_grade`, `coleman_liau_index`, `automated_readability_index`. Emit a kind at most once. Omit unavailable scores entirely.

Warnings are deduplicated strings in this stable order: `markdown_source_projection`, `markdown_content_omitted`, `markdown_prose_fragmented`, `markdown_ambiguous_construct`, `no_words`, `short_readability_sample`, `non_ascii_words`, `numeric_tokens`, `unsupported_readability_tokens`, `incomplete_readability_projection`, `smog_requires_30_sentences`, `english_readability_estimate`. Warnings explain analysis limitations; they are not proof of language, writing quality or correctness.

## Original-source span invariants

Every span is half-open and refers to the **unmodified complete input**, with UTF-8 byte, UTF-16 code-unit and Unicode-scalar ranges. Endpoints are valid scalar boundaries within the source and round-trip through the existing offset index. Source order is stable. No token copies document text into a new field; callers may slice their own input when needed. No normalization, decoding, bidi reordering or whitespace rewriting changes source coordinates.

Prose and protected regions are disjoint. Every token, sentence, paragraph and emitted suggestion is wholly contained in one prose region and intersects no protected region. Never concatenate separate prose regions to synthesize a word, sentence, paragraph or suggestion. A suggestion retains its existing rule ID, original text, replacement, version and three offset ranges from the original source. A match spanning even one protected byte is omitted by analyze. Protected regions may contain arbitrary Unicode; offsets after them remain original offsets.

A useful independent coordinate fixture is `😀 Hi  café.`: Hi is UTF-8 [5,7], UTF-16 [3,5], scalar [2,4]; café is [9,14], [7,11], [6,10]; the double-space suggestion is [7,9], [5,7], [4,6]. Numeric ranges are expectations, not derived from implementation output.

## Plain text and Unicode segmentation

Plain mode has one prose region covering the nonempty source and no protected regions. Empty input has neither. Segmentation uses the pinned unicode-segmentation UAX #29 behavior: word tokens are its Unicode word-boundary segments containing at least one Alphabetic or Number character. Numeric/decimal tokens remain counted words; emoji or punctuation alone do not become words. No language-specific dictionary tokenization or abbreviation tailoring is claimed.

Within each prose region, source paragraph fragments are maximal blocks separated by one or more blank logical lines. A logical line break is CRLF, LF or standalone CR; CRLF is atomic. A blank line contains only ASCII space/tab or no characters. Trim exterior Unicode whitespace from a paragraph's span; retain its original internal characters. Emit a paragraph only if it contains at least one word token. Run Unicode sentence-boundary segmentation independently within each paragraph, trim exterior Unicode whitespace, and emit only fragments containing at least one word. A final unpunctuated word-bearing fragment is a sentence. Abbreviations such as Mr. and initialisms use the dependency's default behavior, with an explicit open tailoring obligation; do not imply mature English sentence recognition.

Token spans remain the word-boundary spans in their region. All source counts cover the complete input, while analyzed_scalar_count covers retained prose regions only. letter_count counts Alphabetic characters in emitted word tokens; digit_count counts Numeric characters in those tokens. unique_word_count uses full Unicode lowercase strings as keys, without NFC normalization or locale-sensitive/full case folding. The exact segmentation dependency/version is part of reproducibility evidence.

## Conservative Markdown protection

Use the maintained, license-reviewed pulldown-cmark 0.13.4 source-offset parser, with Cargo default features disabled and CommonMark parser options only. This is **source-prose analysis**, not a rendered-document or full CommonMark editing guarantee. Do not derive decoded offsets from rendered Text strings. Raw parser source ranges determine coordinates.

Protect entire fenced/indented code blocks and inline code, entire links/images including visible labels and destinations, reference-definition source lines, and HTML blocks. For raw inline HTML, conservatively protect the entire logical source line containing it, including its other prose; HTML editing is unsupported. Protect markup delimiters outside raw text ranges, backslash escapes as complete source sequences, character/numeric entity source sequences, each line's leading ASCII indentation, and ASCII trailing spaces/backslash creating Markdown hard breaks. Never report hard-break spaces or indentation as a double-space suggestion in analyze.

Retain raw text source intervals outside these exclusions. Merge retained intervals only if their original byte ranges are contiguous and no protected range lies between them. Neutral inter-block whitespace may remain outside both arrays. Ordinary line endings inside a retained interval remain source text. Trim neither internal content nor decoded entity expansion into a new offset coordinate space. Formatting such as emphasis may split prose into several source fragments; this is deliberate and must be warned about rather than joined.

Unclosed fenced code protects through EOF. For a source backtick run not consumed by a parsed inline/fenced-code construct, protect from that run through EOF and emit markdown_ambiguous_construct. If a source `[` or `![` opener is unresolved as a parsed link/image or matched literal bracket pair, protect from that opener through EOF and emit the same warning. Malformed HTML-like source beginning `<` followed by an ASCII letter, slash or exclamation mark, without a closing `>` on its logical line, protects that whole line and warns. No fetch, HTML execution, embedded resource loading or recovery that turns excluded source into prose is allowed.

Always emit markdown_source_projection in Markdown mode. Emit markdown_content_omitted when semantic content (code, HTML, whole links/images/reference definitions, escapes/entities, ambiguous constructs) is excluded. Emit markdown_prose_fragmented when protected syntax/indentation/hard-break handling splits or withholds source from otherwise prose-bearing Markdown. These source fragment counts can differ from visible/editor counts. If **any protected region exists**, emit incomplete_readability_projection and omit all six English readability scores rather than scoring a potentially biased projection. Unicode word counts, time estimates and the source-fragment type/token ratio remain available with the warning. Plain Markdown with no protected regions can use the ordinary readability gates.

HTML and LaTeX document modes, math extensions, code-comment extraction, full URL/email/handle/hashtag recognition, frontmatter, tables/extensions and rendered-source mappings remain unimplemented COR-10 work. The unsupported-format error is preferable to claiming those integrations. Markdown literal syntax outside this documented protector remains literal source, not a promise of structural safety for every extension.

## Metric definitions, gates and uncertainty

Let W be emitted word_count, S sentence_count, Y estimated_syllables, P estimated_polysyllable_words, L letter_count and C = letter_count + digit_count. The reading/speaking estimates are respectively 60W/238 and 60W/150 seconds; both are exactly 0 for W=0. They describe analyzed source prose only and are not measured individual reading speed. The type_token_ratio is unique_word_count/W for W>0, otherwise omitted. It remains defined for Unicode/numeric words under the declared segmentation rules.

An English syllable-covered word contains **only ASCII letters A–Z/a–z**, no apostrophes, digits, hyphens, combining marks or accents. Lowercase it, count contiguous groups of a/e/i/o/u/y, subtract one terminal silent-e group only when the group count exceeds one and the word does not end in consonant+le, and bound the result below at one. P counts covered words whose resulting estimate is at least three. These are explicitly original heuristics, not dictionary pronunciations. Counts for unsupported words are not guessed: syllable_covered_words records coverage; Y/P sum only covered words.

Emit no_words when W=0. Otherwise emit short_readability_sample if W<30 or S<3. Emit non_ascii_words when any word contains a non-ASCII character, numeric_tokens when a word contains a Numeric character, and unsupported_readability_tokens when syllable_covered_words differs from W. If W>0 and S<30, emit smog_requires_30_sentences. All English formula scores require W>=30, S>=3, every word covered by the ASCII heuristic, and no incomplete Markdown projection. SMOG additionally requires S>=30. Any failed gate omits that score; no division by zero, clamped estimate or fabricated zero is allowed. Non-Latin/accented/numeric/short text still has its defined Unicode counts, type/token ratio and time estimates.

When eligible, emit the following **unclamped, unrounded finite estimates** and english_readability_estimate:

| Kind | Formula |
| --- | --- |
| flesch_reading_ease | 206.835 - 1.015(W/S) - 84.6(Y/W) |
| flesch_kincaid_grade | 0.39(W/S) + 11.8(Y/W) - 15.59 |
| gunning_fog | 0.4[(W/S) + 100(P/W)] |
| smog_grade | 1.043 sqrt(30P/S) + 3.1291 |
| coleman_liau_index | 0.0588(100L/W) - 0.296(100S/W) - 15.8 |
| automated_readability_index | 4.71(C/W) + 0.5(W/S) - 21.43 |

The language is assumed en-US, never detected from ASCII. Fog's polysyllable heuristic does not exclude proper names or specialized vocabulary; no grade-level certification, calibrated quality score or personal assessment is implied. Formula revisions require a versioned contract/ADR and renewed fixtures.

## Independent fixtures and required checks

Repeat the original sentence `The cat sat by the log and saw the dog.` three times separated by single spaces: W=30, S=3, Y=30, P=0, L=C=87, unique_word_count=8. Expect FRE=112.085, FK=0.11, Fog=4, Coleman-Liau=-1.708, ARI=-2.771 within 0.000001; SMOG is omitted. type_token_ratio=8/30, reading_seconds=1800/238 and speaking_seconds=12. Repeat it 30 times: W=300, S=30, Y=300, P=0 and SMOG=3.1291. These hand-calculated oracles do not depend on output from the builder or any reference product.

Required checks: exact legacy native/WASM fixtures unchanged; new schema/codegen negative and cross-reference cases; native/WASM report parity; strict JS types/lone-surrogate rejection; 1 MiB accepted and one byte over rejected including multibyte boundaries; deterministic output/version stamps; Unicode word/sentence/paragraph spans across emoji ZWJ, combining marks, RTL, tabs/NBSP/zero-width and CRLF; invalid boundary rejection; blank/punctuation/emoji/numeric/non-Latin/code-only inputs; short-text gate and finite score serialization; independent formulas above; Markdown fences/backticks/links/images/HTML/escapes/entities/reference definitions/indentation/hard breaks and malformed fallbacks; no span or suggestion crossing a protected range; no joining fragments; source offsets unchanged after Unicode exclusions; local-only runtime behavior and absence of document-content logs. Benchmarks identify this bounded pipeline and machine, not the full M1 engine or mobile budgets.

No feature is CI-verified from specification, generated declarations, local source review or local tests. COR-10/CLA-04 move from planned to spec in this analyst unit and only to in-progress when implemented; all unimplemented surfaces and the rest of M1 remain planned.
