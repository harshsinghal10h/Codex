// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
//! Counts and explicitly gated readability estimates over original source spans.
use nw_schema::{InputFormat, MetricKind, MetricScore, Span, TextMetrics, Token};
use nw_text::TextOffsets;
use std::collections::HashSet;
use unicode_segmentation::UnicodeSegmentation;

const MAX_SOURCE_BYTES: usize = 1_048_576;
const WARNING_ORDER: [&str; 12] = [
    "markdown_source_projection",
    "markdown_content_omitted",
    "markdown_prose_fragmented",
    "markdown_ambiguous_construct",
    "no_words",
    "short_readability_sample",
    "non_ascii_words",
    "numeric_tokens",
    "unsupported_readability_tokens",
    "incomplete_readability_projection",
    "smog_requires_30_sentences",
    "english_readability_estimate",
];

/// Borrowed source and segmentation; no joined or normalized document is built.
pub struct MetricInput<'a> {
    pub text: &'a str,
    pub prose_regions: &'a [Span],
    pub tokens: &'a [Token],
    pub sentences: &'a [Span],
    pub paragraphs: &'a [Span],
    pub input_format: InputFormat,
    /// The projector's four `markdown_*` warning kinds, in any order.
    pub projection_warnings: &'a [String],
    /// True when any Markdown source region was protected.
    pub incomplete_projection: bool,
}

/// Measure canonical source spans, rejecting malformed coordinates or oversized input.
///
/// Sentence/paragraph boundaries are supplied by the canonical segmenter. English
/// estimates are omitted unless every frozen specification gate succeeds.
pub fn measure(input: MetricInput<'_>) -> Result<TextMetrics, String> {
    let MetricInput {
        text,
        prose_regions,
        tokens,
        sentences,
        paragraphs,
        input_format,
        projection_warnings,
        incomplete_projection,
    } = input;
    if text.len() > MAX_SOURCE_BYTES {
        return Err("metric input exceeds 1048576 UTF-8 bytes".into());
    }
    let offsets = TextOffsets::new(text);
    validate_spans(prose_regions.iter(), &offsets, None)?;
    validate_spans(
        tokens.iter().map(|token| &token.span),
        &offsets,
        Some(prose_regions),
    )?;
    validate_spans(sentences.iter(), &offsets, Some(prose_regions))?;
    validate_spans(paragraphs.iter(), &offsets, Some(prose_regions))?;

    let mut warnings = [false; WARNING_ORDER.len()];
    warnings[0] = input_format == InputFormat::Markdown;
    for warning in projection_warnings {
        let index = WARNING_ORDER[..4]
            .iter()
            .position(|kind| *kind == warning)
            .ok_or("unknown Markdown projection warning")?;
        warnings[index] = true;
    }
    let incomplete = input_format == InputFormat::Markdown && incomplete_projection;
    warnings[9] = incomplete;

    let mut unique = HashSet::new();
    let mut letter_count = 0;
    let mut digit_count = 0;
    let mut syllable_covered_words = 0;
    let mut estimated_syllables = 0;
    let mut estimated_polysyllable_words = 0;
    for token in tokens {
        let word = &text[token.span.utf8[0]..token.span.utf8[1]];
        if !word.chars().any(|ch| ch.is_alphabetic() || ch.is_numeric()) {
            return Err("metric token must contain an Alphabetic or Numeric character".into());
        }
        unique.insert(word.to_lowercase());
        warnings[6] |= !word.is_ascii();
        for ch in word.chars() {
            letter_count += u64::from(ch.is_alphabetic());
            digit_count += u64::from(ch.is_numeric());
            warnings[7] |= ch.is_numeric();
        }
        if let Some(syllables) = syllables(word) {
            syllable_covered_words += 1;
            estimated_syllables += syllables;
            estimated_polysyllable_words += u64::from(syllables >= 3);
        }
    }

    let word_count = tokens.len() as u64;
    let sentence_count = sentences.len() as u64;
    let unique_word_count = unique.len() as u64;
    warnings[4] = word_count == 0;
    warnings[5] = word_count > 0 && (word_count < 30 || sentence_count < 3);
    warnings[8] = syllable_covered_words != word_count;
    warnings[10] = word_count > 0 && sentence_count < 30;
    let eligible = word_count >= 30
        && sentence_count >= 3
        && syllable_covered_words == word_count
        && !incomplete;
    warnings[11] = eligible;

    let mut scores = Vec::new();
    let mut score = |kind, value| scores.push(MetricScore { kind, value });
    let w = word_count as f64;
    let s = sentence_count as f64;
    if word_count > 0 {
        score(MetricKind::TypeTokenRatio, unique_word_count as f64 / w);
    }
    if eligible {
        let y = estimated_syllables as f64;
        let p = estimated_polysyllable_words as f64;
        let l = letter_count as f64;
        let c = (letter_count + digit_count) as f64;
        score(
            MetricKind::FleschReadingEase,
            206.835 - 1.015 * (w / s) - 84.6 * (y / w),
        );
        score(
            MetricKind::FleschKincaidGrade,
            0.39 * (w / s) + 11.8 * (y / w) - 15.59,
        );
        score(MetricKind::GunningFog, 0.4 * ((w / s) + 100.0 * (p / w)));
        if sentence_count >= 30 {
            score(
                MetricKind::SmogGrade,
                1.043 * (30.0 * p / s).sqrt() + 3.1291,
            );
        }
        score(
            MetricKind::ColemanLiauIndex,
            0.0588 * (100.0 * l / w) - 0.296 * (100.0 * s / w) - 15.8,
        );
        score(
            MetricKind::AutomatedReadabilityIndex,
            4.71 * (c / w) + 0.5 * (w / s) - 21.43,
        );
    }
    Ok(TextMetrics {
        source_utf8_bytes: text.len() as u64,
        source_utf16_units: text.encode_utf16().count() as u64,
        source_scalar_count: text.chars().count() as u64,
        source_grapheme_count: text.graphemes(true).count() as u64,
        analyzed_scalar_count: prose_regions
            .iter()
            .map(|span| (span.scalar[1] - span.scalar[0]) as u64)
            .sum(),
        word_count,
        sentence_count,
        paragraph_count: paragraphs.len() as u64,
        letter_count,
        digit_count,
        unique_word_count,
        syllable_covered_words,
        estimated_syllables,
        estimated_polysyllable_words,
        reading_seconds: 60.0 * w / 238.0,
        speaking_seconds: 60.0 * w / 150.0,
        scores,
        warnings: WARNING_ORDER
            .into_iter()
            .zip(warnings)
            .filter(|(_, present)| *present)
            .map(|(kind, _)| kind.to_owned())
            .collect(),
    })
}

fn validate_spans<'a>(
    spans: impl Iterator<Item = &'a Span>,
    offsets: &TextOffsets<'_>,
    prose_regions: Option<&[Span]>,
) -> Result<(), String> {
    let mut previous_end = 0;
    let mut region_index = 0;
    for span in spans {
        let canonical = offsets
            .from_utf8(span.utf8)
            .map_err(|error| format!("invalid metric span: {error:?}"))?;
        if canonical != *span || span.utf8[0] < previous_end || span.utf8[0] == span.utf8[1] {
            return Err("metric spans must be nonempty, canonical and ordered/disjoint".into());
        }
        previous_end = span.utf8[1];
        if let Some(regions) = prose_regions {
            while region_index < regions.len() && regions[region_index].utf8[1] <= span.utf8[0] {
                region_index += 1;
            }
            if !regions.get(region_index).is_some_and(|region| {
                region.utf8[0] <= span.utf8[0] && span.utf8[1] <= region.utf8[1]
            }) {
                return Err("metric span must be wholly contained in one prose region".into());
            }
        }
    }
    Ok(())
}

fn vowel(byte: u8) -> bool {
    matches!(
        byte.to_ascii_lowercase(),
        b'a' | b'e' | b'i' | b'o' | b'u' | b'y'
    )
}

fn syllables(word: &str) -> Option<u64> {
    if word.is_empty() || !word.bytes().all(|byte| byte.is_ascii_alphabetic()) {
        return None;
    }
    let bytes = word.as_bytes();
    let mut groups = 0_u64;
    let mut previous_vowel = false;
    for &byte in bytes {
        let current_vowel = vowel(byte);
        groups += u64::from(current_vowel && !previous_vowel);
        previous_vowel = current_vowel;
    }
    let consonant_le = bytes.len() >= 3
        && bytes[bytes.len() - 2].eq_ignore_ascii_case(&b'l')
        && !vowel(bytes[bytes.len() - 3]);
    if groups > 1 && bytes[bytes.len() - 1].eq_ignore_ascii_case(&b'e') && !consonant_le {
        groups -= 1;
    }
    Some(groups.max(1))
}

#[cfg(test)]
mod tests {
    use super::*;
    use nw_schema::TokenKind;
    use nw_text::segment;

    const SENTENCE: &str = "The cat sat by the log and saw the dog.";

    fn repeated(sentence: &str, count: usize) -> String {
        std::iter::repeat_n(sentence, count)
            .collect::<Vec<_>>()
            .join(" ")
    }

    fn full(text: &str) -> TextMetrics {
        let regions = if text.is_empty() {
            vec![]
        } else {
            vec![TextOffsets::new(text).from_utf8([0, text.len()]).unwrap()]
        };
        projected(text, &regions, InputFormat::Plain, &[], false)
    }

    fn projected(
        text: &str,
        prose_regions: &[Span],
        input_format: InputFormat,
        projection_warnings: &[String],
        incomplete_projection: bool,
    ) -> TextMetrics {
        let segments = segment(text, prose_regions).unwrap();
        measure(MetricInput {
            text,
            prose_regions,
            tokens: &segments.tokens,
            sentences: &segments.sentences,
            paragraphs: &segments.paragraphs,
            input_format,
            projection_warnings,
            incomplete_projection,
        })
        .unwrap()
    }

    fn assert_close(actual: f64, expected: f64) {
        assert!(
            (actual - expected).abs() < 0.000_001,
            "{actual} != {expected}"
        );
    }

    fn score(metrics: &TextMetrics, kind: MetricKind) -> Option<f64> {
        metrics
            .scores
            .iter()
            .find(|entry| entry.kind == kind)
            .map(|entry| entry.value)
    }

    fn assert_finite_serialization(metrics: &TextMetrics) {
        assert!(metrics.reading_seconds.is_finite());
        assert!(metrics.speaking_seconds.is_finite());
        assert!(metrics.scores.iter().all(|entry| entry.value.is_finite()));
        let encoded = serde_json::to_string(metrics).unwrap();
        assert!(!encoded.contains("null"));
        let decoded = serde_json::from_str::<TextMetrics>(&encoded).unwrap();
        assert_close(decoded.reading_seconds, metrics.reading_seconds);
        assert_close(decoded.speaking_seconds, metrics.speaking_seconds);
        assert_eq!(decoded.scores.len(), metrics.scores.len());
        for (actual, expected) in decoded.scores.iter().zip(&metrics.scores) {
            assert_eq!(actual.kind, expected.kind);
            assert_close(actual.value, expected.value);
        }
    }

    #[test]
    fn independent_three_sentence_formula_oracle_preserves_negative_estimates() {
        let metrics = full(&repeated(SENTENCE, 3));
        assert_eq!(
            (
                metrics.word_count,
                metrics.sentence_count,
                metrics.paragraph_count
            ),
            (30, 3, 1)
        );
        assert_eq!(
            (
                metrics.syllable_covered_words,
                metrics.estimated_syllables,
                metrics.estimated_polysyllable_words
            ),
            (30, 30, 0)
        );
        assert_eq!(
            (
                metrics.letter_count,
                metrics.digit_count,
                metrics.unique_word_count
            ),
            (87, 0, 8)
        );
        let expected = [
            (MetricKind::TypeTokenRatio, 8.0 / 30.0),
            (MetricKind::FleschReadingEase, 112.085),
            (MetricKind::FleschKincaidGrade, 0.11),
            (MetricKind::GunningFog, 4.0),
            (MetricKind::ColemanLiauIndex, -1.708),
            (MetricKind::AutomatedReadabilityIndex, -2.771),
        ];
        assert_eq!(metrics.scores.len(), expected.len());
        for (actual, (kind, value)) in metrics.scores.iter().zip(expected) {
            assert_eq!(actual.kind, kind);
            assert_close(actual.value, value);
        }
        assert_eq!(score(&metrics, MetricKind::SmogGrade), None);
        assert_close(metrics.reading_seconds, 1800.0 / 238.0);
        assert_eq!(metrics.speaking_seconds, 12.0);
        assert_eq!(
            metrics.warnings,
            ["smog_requires_30_sentences", "english_readability_estimate"]
        );
        assert_finite_serialization(&metrics);
    }

    #[test]
    fn independent_thirty_sentence_smog_and_positive_polysyllable_oracles() {
        let metrics = full(&repeated(SENTENCE, 30));
        assert_eq!(
            (
                metrics.word_count,
                metrics.sentence_count,
                metrics.estimated_syllables,
                metrics.estimated_polysyllable_words
            ),
            (300, 30, 300, 0)
        );
        assert_close(score(&metrics, MetricKind::SmogGrade).unwrap(), 3.1291);
        assert_eq!(
            metrics
                .scores
                .iter()
                .map(|entry| entry.kind)
                .collect::<Vec<_>>(),
            [
                MetricKind::TypeTokenRatio,
                MetricKind::FleschReadingEase,
                MetricKind::FleschKincaidGrade,
                MetricKind::GunningFog,
                MetricKind::SmogGrade,
                MetricKind::ColemanLiauIndex,
                MetricKind::AutomatedReadabilityIndex,
            ]
        );
        assert_eq!(metrics.warnings, ["english_readability_estimate"]);

        // Thirty one-word sentences: W=S=P=30, Y=90 and L=C=180.
        let metrics = full(&repeated("Banana.", 30));
        assert_eq!(
            (
                metrics.word_count,
                metrics.sentence_count,
                metrics.estimated_syllables,
                metrics.estimated_polysyllable_words,
                metrics.letter_count
            ),
            (30, 30, 90, 30, 180)
        );
        for (kind, expected) in [
            (MetricKind::FleschReadingEase, -47.98),
            (MetricKind::FleschKincaidGrade, 20.2),
            (MetricKind::GunningFog, 40.4),
            (MetricKind::SmogGrade, 8.841_846_274_778_883),
            (MetricKind::ColemanLiauIndex, -10.12),
            (MetricKind::AutomatedReadabilityIndex, 7.33),
        ] {
            assert_close(score(&metrics, kind).unwrap(), expected);
        }
        assert_finite_serialization(&metrics);
    }

    #[test]
    fn empty_punctuation_and_emoji_have_no_fabricated_scores() {
        for text in ["", " \t\r\n", "😀 !!! 👩‍💻"] {
            let metrics = full(text);
            assert_eq!(
                (
                    metrics.word_count,
                    metrics.sentence_count,
                    metrics.paragraph_count
                ),
                (0, 0, 0)
            );
            assert_eq!(
                (
                    metrics.letter_count,
                    metrics.digit_count,
                    metrics.unique_word_count,
                    metrics.syllable_covered_words,
                    metrics.estimated_syllables,
                    metrics.estimated_polysyllable_words
                ),
                (0, 0, 0, 0, 0, 0)
            );
            assert_eq!(
                (metrics.reading_seconds, metrics.speaking_seconds),
                (0.0, 0.0)
            );
            assert!(metrics.scores.is_empty());
            assert_eq!(metrics.warnings, ["no_words"]);
            assert_finite_serialization(&metrics);
        }
    }

    #[test]
    fn original_unicode_source_counts_and_retained_prose_are_distinct() {
        let metrics = full("😀 Hi  café.");
        assert_eq!(
            (
                metrics.source_utf8_bytes,
                metrics.source_utf16_units,
                metrics.source_scalar_count,
                metrics.source_grapheme_count,
                metrics.analyzed_scalar_count
            ),
            (15, 12, 11, 11, 11)
        );
        assert_eq!(
            (
                metrics.word_count,
                metrics.letter_count,
                metrics.digit_count,
                metrics.syllable_covered_words,
                metrics.estimated_syllables
            ),
            (2, 6, 0, 1, 1)
        );
        assert_eq!(
            metrics.warnings,
            [
                "short_readability_sample",
                "non_ascii_words",
                "unsupported_readability_tokens",
                "smog_requires_30_sentences"
            ]
        );

        let text = "👩‍💻 e\u{301}\r\n";
        let region = TextOffsets::new(text).from_utf8([12, 15]).unwrap();
        let metrics = projected(text, &[region], InputFormat::Markdown, &[], true);
        assert_eq!(
            (
                metrics.source_utf8_bytes,
                metrics.source_utf16_units,
                metrics.source_scalar_count,
                metrics.source_grapheme_count,
                metrics.analyzed_scalar_count
            ),
            (17, 10, 8, 4, 2)
        );
        assert_eq!(metrics.word_count, 1);
        assert_eq!(metrics.letter_count, 1);
        assert_finite_serialization(&metrics);
    }

    #[test]
    fn lowercase_uniqueness_uses_unicode_without_normalization_or_full_folding() {
        let metrics = full("É é E\u{301} e\u{301} İ i ß ss");
        assert_eq!((metrics.word_count, metrics.unique_word_count), (8, 6));
        assert_eq!(score(&metrics, MetricKind::TypeTokenRatio), Some(0.75));
        assert_eq!(metrics.syllable_covered_words, 2);
    }

    #[test]
    fn numeric_and_non_latin_tokens_keep_counts_times_and_type_token_ratio() {
        let metrics = full("12.5 ² Ⅷ café سلام");
        assert_eq!(
            (
                metrics.word_count,
                metrics.unique_word_count,
                metrics.digit_count
            ),
            (5, 5, 5)
        );
        // The Roman numeral is both Alphabetic and Numeric under Unicode properties.
        assert_eq!(metrics.letter_count, 9);
        assert_eq!(
            (
                metrics.syllable_covered_words,
                metrics.estimated_syllables,
                metrics.estimated_polysyllable_words
            ),
            (0, 0, 0)
        );
        assert_eq!(
            metrics.scores,
            [MetricScore {
                kind: MetricKind::TypeTokenRatio,
                value: 1.0
            }]
        );
        assert_close(metrics.reading_seconds, 300.0 / 238.0);
        assert_eq!(metrics.speaking_seconds, 2.0);
        assert_eq!(
            metrics.warnings,
            [
                "short_readability_sample",
                "non_ascii_words",
                "numeric_tokens",
                "unsupported_readability_tokens",
                "smog_requires_30_sentences"
            ]
        );
        assert_finite_serialization(&metrics);
    }

    #[test]
    fn ascii_heuristic_has_explicit_coverage_and_silent_e_rules() {
        for (word, expected) in [
            ("a", 1),
            ("rhythm", 1),
            ("brrr", 1),
            ("FILE", 1),
            ("table", 2),
            ("APPLE", 2),
            ("style", 1),
            ("banana", 3),
            ("beautiful", 3),
            ("queue", 1),
            ("eye", 1),
            ("le", 1),
        ] {
            assert_eq!(syllables(word), Some(expected), "{word}");
        }
        for word in [
            "",
            "can't",
            "can’t",
            "mother-in-law",
            "word1",
            "café",
            "e\u{301}",
            "سلام",
            "²",
        ] {
            assert_eq!(syllables(word), None, "{word}");
        }
        let metrics = full("can't can’t café e\u{301} 12");
        assert_eq!(
            (
                metrics.word_count,
                metrics.syllable_covered_words,
                metrics.estimated_syllables
            ),
            (5, 0, 0)
        );
    }

    #[test]
    fn each_readability_gate_omits_estimates_but_preserves_defined_metrics() {
        for text in [
            repeated("cat", 30),
            repeated("Cat.", 29),
            format!("{} café.", repeated("Cat.", 30)),
            format!("{} 1.", repeated("Cat.", 30)),
            format!("{} can't.", repeated("Cat.", 30)),
        ] {
            let metrics = full(&text);
            assert_eq!(metrics.scores.len(), 1, "{text}");
            assert_eq!(metrics.scores[0].kind, MetricKind::TypeTokenRatio);
            assert!(metrics.reading_seconds > 0.0);
            assert!(metrics.speaking_seconds > 0.0);
            assert!(
                !metrics
                    .warnings
                    .iter()
                    .any(|warning| warning == "english_readability_estimate")
            );
            assert_finite_serialization(&metrics);
        }
    }

    #[test]
    fn markdown_warnings_are_deduplicated_ordered_and_any_protection_gates_grades() {
        let prose = repeated(SENTENCE, 3);
        let text = format!("{prose} `secret`");
        let region = TextOffsets::new(&text).from_utf8([0, prose.len()]).unwrap();
        let warnings = [
            "markdown_ambiguous_construct",
            "markdown_prose_fragmented",
            "markdown_content_omitted",
            "markdown_source_projection",
            "markdown_content_omitted",
        ]
        .map(str::to_owned);
        let metrics = projected(
            &text,
            std::slice::from_ref(&region),
            InputFormat::Markdown,
            &warnings,
            true,
        );
        assert_eq!(metrics.word_count, 30);
        assert_eq!(metrics.analyzed_scalar_count, prose.len() as u64);
        assert_eq!(metrics.source_scalar_count, (prose.len() + 9) as u64);
        assert_eq!(metrics.scores.len(), 1);
        assert_eq!(
            metrics.warnings,
            [
                "markdown_source_projection",
                "markdown_content_omitted",
                "markdown_prose_fragmented",
                "markdown_ambiguous_construct",
                "incomplete_readability_projection",
                "smog_requires_30_sentences"
            ]
        );
        let metrics = projected(&prose, &[region], InputFormat::Markdown, &[], false);
        assert_eq!(metrics.scores.len(), 6);
        assert_eq!(
            metrics.warnings,
            [
                "markdown_source_projection",
                "smog_requires_30_sentences",
                "english_readability_estimate"
            ]
        );
    }

    #[test]
    fn code_only_projection_retains_whole_source_counts_and_zero_analyzed_counts() {
        let metrics = projected(
            "`😀 code`",
            &[],
            InputFormat::Markdown,
            &["markdown_content_omitted".into()],
            true,
        );
        assert_eq!(
            (
                metrics.source_utf8_bytes,
                metrics.source_utf16_units,
                metrics.source_scalar_count,
                metrics.source_grapheme_count
            ),
            (11, 9, 8, 8)
        );
        assert_eq!(
            (
                metrics.analyzed_scalar_count,
                metrics.word_count,
                metrics.sentence_count,
                metrics.paragraph_count
            ),
            (0, 0, 0, 0)
        );
        assert_eq!(
            metrics.warnings,
            [
                "markdown_source_projection",
                "markdown_content_omitted",
                "no_words",
                "incomplete_readability_projection"
            ]
        );
        assert!(metrics.scores.is_empty());
        assert_finite_serialization(&metrics);
    }

    #[test]
    fn malformed_spans_and_projection_warnings_are_errors_without_panics() {
        let text = "😀 a`x`b";
        let offsets = TextOffsets::new(text);
        let regions = [
            offsets.from_utf8([5, 6]).unwrap(),
            offsets.from_utf8([9, 10]).unwrap(),
        ];
        let valid = |tokens: &[Token], prose_regions: &[Span], projection_warnings: &[String]| {
            measure(MetricInput {
                text,
                prose_regions,
                tokens,
                sentences: &[],
                paragraphs: &[],
                input_format: InputFormat::Markdown,
                projection_warnings,
                incomplete_projection: true,
            })
        };
        for range in [[1, 4], [6, 5], [0, usize::MAX], [5, 5]] {
            let bad = Span {
                utf8: range,
                utf16: [0, 0],
                scalar: [0, 0],
            };
            assert!(valid(&[], &[bad], &[]).is_err());
        }
        let mut mismatched = regions[0].clone();
        mismatched.utf16 = [0, 1];
        assert!(valid(&[], &[mismatched], &[]).is_err());
        assert!(valid(&[], &[regions[1].clone(), regions[0].clone()], &[]).is_err());
        assert!(valid(&[], &[regions[0].clone(), regions[0].clone()], &[]).is_err());
        let token = |range| Token {
            span: offsets.from_utf8(range).unwrap(),
            kind: TokenKind::Word,
        };
        assert!(valid(&[token([5, 10])], &regions, &[]).is_err());
        assert!(valid(&[token([6, 7])], &regions, &[]).is_err());
        assert!(valid(&[token([9, 10]), token([5, 6])], &regions, &[]).is_err());
        assert!(valid(&[], &regions, &["invented_warning".into()]).is_err());
    }

    #[test]
    fn source_cap_is_checked_before_any_coordinate_allocation() {
        for (text, accepted) in [
            ("x".repeat(MAX_SOURCE_BYTES), true),
            ("x".repeat(MAX_SOURCE_BYTES + 1), false),
        ] {
            let result = measure(MetricInput {
                text: &text,
                prose_regions: &[],
                tokens: &[],
                sentences: &[],
                paragraphs: &[],
                input_format: InputFormat::Markdown,
                projection_warnings: &[],
                incomplete_projection: true,
            });
            assert_eq!(result.is_ok(), accepted);
            if let Ok(metrics) = result {
                assert_eq!(metrics.source_utf8_bytes, MAX_SOURCE_BYTES as u64);
                assert!(metrics.scores.is_empty());
            }
        }
    }
}
