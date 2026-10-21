// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
use nw_engine::{Engine, MAX_ANALYSIS_UTF8_BYTES};
use nw_schema::{AnalysisResult, InputFormat, MetricKind, Span};
use nw_text::TextOffsets;

fn analyze(text: &str, format: InputFormat) -> AnalysisResult {
    Engine::new()
        .unwrap()
        .analyze(text, 17, "en-US", format)
        .unwrap()
}

fn valid_spans(text: &str, report: &AnalysisResult) {
    let offsets = TextOffsets::new(text);
    let spans = report
        .tokens
        .iter()
        .map(|token| &token.span)
        .chain(&report.sentences)
        .chain(&report.paragraphs)
        .chain(report.suggestions.iter().map(|s| &s.span));
    for span in spans {
        assert_eq!(offsets.from_utf8(span.utf8).unwrap(), *span);
        assert!(
            report
                .prose_regions
                .iter()
                .any(|prose| prose.utf8[0] <= span.utf8[0] && span.utf8[1] <= prose.utf8[1])
        );
        assert!(
            report
                .protected_regions
                .iter()
                .all(|protected| span.utf8[1] <= protected.span.utf8[0]
                    || protected.span.utf8[1] <= span.utf8[0])
        );
    }
}

#[test]
fn independent_unicode_coordinates_and_original_suggestions() {
    let text = "😀 Hi  café.";
    let report = analyze(text, InputFormat::Plain);
    assert_eq!(report.tokens.len(), 2);
    assert_eq!(
        report.tokens[0].span,
        Span {
            utf8: [5, 7],
            utf16: [3, 5],
            scalar: [2, 4]
        }
    );
    assert_eq!(
        report.tokens[1].span,
        Span {
            utf8: [9, 14],
            utf16: [7, 11],
            scalar: [6, 10]
        }
    );
    assert_eq!(
        report.suggestions[0].span,
        Span {
            utf8: [7, 9],
            utf16: [5, 7],
            scalar: [4, 6]
        }
    );
    assert_eq!(report.suggestions[0].doc_version, 17);
    assert_eq!(report, analyze(text, InputFormat::Plain));
    valid_spans(text, &report);
}

#[test]
fn markdown_protection_is_additive_and_no_source_fragments_are_joined() {
    let engine = Engine::new().unwrap();
    let text = "👩‍💻 Start  here.\n\n```text\ncode  stays\n```\n\nEnd  here.  \n`literal  code` and [link  label](https://example.test).";
    let legacy = engine.check(text, 17, "en-US").unwrap();
    let report = analyze(text, InputFormat::Markdown);
    assert!(legacy.len() > report.suggestions.len());
    assert_eq!(report.suggestions.len(), 2);
    assert!(
        report
            .metrics
            .warnings
            .iter()
            .any(|w| w == "incomplete_readability_projection")
    );
    assert!(
        report
            .metrics
            .scores
            .iter()
            .all(|score| score.kind == MetricKind::TypeTokenRatio)
    );
    for suggestion in &report.suggestions {
        assert!(legacy.contains(suggestion));
        assert_eq!(
            &text[suggestion.span.utf8[0]..suggestion.span.utf8[1]],
            suggestion.original
        );
    }
    valid_spans(text, &report);
    let split = analyze("hel*lo* world", InputFormat::Markdown);
    assert!(
        split
            .tokens
            .iter()
            .all(|token| &"hel*lo* world"[token.span.utf8[0]..token.span.utf8[1]] != "hello")
    );
    assert_eq!(engine.check("code  \n`a  b`", 0, "en-US").unwrap().len(), 2);
}

#[test]
fn independent_formulas_and_score_gates_are_preserved_end_to_end() {
    let sentence = "The cat sat by the log and saw the dog.";
    let report = analyze(&[sentence; 3].join(" "), InputFormat::Plain);
    assert_eq!(
        (
            report.metrics.word_count,
            report.metrics.sentence_count,
            report.metrics.estimated_syllables,
            report.metrics.letter_count
        ),
        (30, 3, 30, 87)
    );
    for (kind, expected) in [
        (MetricKind::FleschReadingEase, 112.085),
        (MetricKind::FleschKincaidGrade, 0.11),
        (MetricKind::GunningFog, 4.0),
        (MetricKind::ColemanLiauIndex, -1.708),
        (MetricKind::AutomatedReadabilityIndex, -2.771),
    ] {
        let actual = report
            .metrics
            .scores
            .iter()
            .find(|score| score.kind == kind)
            .unwrap()
            .value;
        assert!((actual - expected).abs() < 0.000001, "{kind:?}: {actual}");
    }
    assert!(
        !report
            .metrics
            .scores
            .iter()
            .any(|score| score.kind == MetricKind::SmogGrade)
    );
    let long = analyze(&[sentence; 30].join(" "), InputFormat::Plain);
    assert_eq!(
        long.metrics
            .scores
            .iter()
            .find(|score| score.kind == MetricKind::SmogGrade)
            .unwrap()
            .value,
        3.1291
    );
    for text in [
        "",
        "😀 !!!",
        "   \r\n\t",
        "42 3.14",
        "שלום عالم",
        "e\u{301} café",
        "short words",
    ] {
        let short = analyze(text, InputFormat::Plain);
        assert!(
            short
                .metrics
                .scores
                .iter()
                .all(|score| score.kind == MetricKind::TypeTokenRatio)
        );
        assert!(
            short
                .metrics
                .scores
                .iter()
                .all(|score| score.value.is_finite())
        );
        valid_spans(text, &short);
    }
    let code = analyze("```\nword  word\n```", InputFormat::Markdown);
    assert_eq!(code.metrics.word_count, 0);
    assert!(code.suggestions.is_empty());
}

#[test]
fn input_limit_settings_and_legacy_limit_are_independent() {
    let engine = Engine::new().unwrap();
    for text in [
        ".".repeat(MAX_ANALYSIS_UTF8_BYTES),
        "😀".repeat(MAX_ANALYSIS_UTF8_BYTES / 4),
    ] {
        let report = engine
            .analyze(&text, 0, "en-US", InputFormat::Plain)
            .unwrap();
        assert_eq!(
            report.metrics.source_utf8_bytes,
            MAX_ANALYSIS_UTF8_BYTES as u64
        );
        let too_large = format!("{text}x");
        let error = engine
            .analyze(&too_large, u64::MAX, "fr", InputFormat::Plain)
            .unwrap_err();
        assert!(error.contains("1048576"));
    }
    assert!(
        engine
            .analyze("", 9_007_199_254_740_992, "en-US", InputFormat::Plain)
            .is_err()
    );
    assert!(engine.analyze("", 0, "fr", InputFormat::Plain).is_err());
    assert_eq!(
        engine
            .analyze("", 9_007_199_254_740_991, "en-US", InputFormat::Plain)
            .unwrap()
            .doc_version,
        9_007_199_254_740_991
    );
    assert!(
        engine
            .check(&".".repeat(MAX_ANALYSIS_UTF8_BYTES + 1), 0, "en-US")
            .unwrap()
            .is_empty()
    );
}
