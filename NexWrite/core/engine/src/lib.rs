// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
//! Deterministic, local-only plain-text bootstrap analysis. No I/O or networking.
use nw_rules::{BUNDLED_RULE, Rule};
use nw_schema::{
    Explanation, Flags, Replacement, ReplacementKind, Source, Suggestion, TelemetryClass,
};
use nw_text::TextOffsets;

pub struct Engine {
    rule: Rule,
}
impl Engine {
    pub fn new() -> Result<Self, String> {
        Rule::compile(BUNDLED_RULE).map(|rule| Self { rule })
    }

    pub fn check(
        &self,
        text: &str,
        doc_version: u64,
        locale: &str,
    ) -> Result<Vec<Suggestion>, String> {
        if locale != "en-US" {
            return Err("bootstrap supports plain-text en-US only".into());
        }
        if doc_version > 9_007_199_254_740_991 {
            return Err("document version exceeds portable safe-integer limit".into());
        }
        if text.len() > u32::MAX as usize {
            return Err("document exceeds portable offset limit".into());
        }
        let offsets = TextOffsets::new(text);
        let rule = &self.rule.definition;
        self.rule
            .ranges(text)
            .into_iter()
            .map(|[start, end]| {
                let span = offsets
                    .from_utf8([start, end])
                    .map_err(|_| "rule produced an invalid boundary")?;
                Ok(Suggestion {
                    id: format!("{}:{doc_version}:{start}:{end}", rule.id),
                    rule_id: rule.id.clone(),
                    category: rule.category,
                    subcategory: rule.subcategory.clone(),
                    severity: rule.severity,
                    span,
                    original: text[start..end].into(),
                    replacements: rule
                        .suggestions
                        .iter()
                        .map(|text| Replacement {
                            text: text.clone(),
                            confidence: 1.0,
                            kind: ReplacementKind::Replace,
                        })
                        .collect(),
                    explanation: Explanation {
                        short: rule.message.short.clone(),
                        long: rule.message.long.clone(),
                        examples: rule
                            .examples
                            .positive
                            .iter()
                            .take(1)
                            .map(|case| format!("{} → {}", case.text, case.result))
                            .collect(),
                    },
                    flags: Flags {
                        autofix_safe: true,
                        needs_context: false,
                        style_guide: false,
                        user_rule: false,
                    },
                    source: Source::Rule,
                    doc_version,
                    locale: locale.into(),
                    goals_applied: vec![],
                    provenance: rule.provenance.clone(),
                    telemetry_class: TelemetryClass::None,
                })
            })
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maximal_runs_original_replacements_and_versions() {
        let engine = Engine::new().unwrap();
        let text = "  a   b  ";
        let actual = engine.check(text, 42, "en-US").unwrap();
        assert_eq!(
            actual.iter().map(|s| s.span.utf8).collect::<Vec<_>>(),
            [[0, 2], [3, 6], [7, 9]]
        );
        for suggestion in &actual {
            assert_eq!(
                suggestion.original,
                text[suggestion.span.utf8[0]..suggestion.span.utf8[1]]
            );
            assert_eq!(suggestion.replacements[0].text, " ");
            assert_eq!(suggestion.doc_version, 42);
            assert_eq!(suggestion.telemetry_class, TelemetryClass::None);
        }
        assert_eq!(actual, engine.check(text, 42, "en-US").unwrap());
        assert!(
            engine
                .check(text, 43, "en-US")
                .unwrap()
                .iter()
                .zip(actual)
                .all(|(a, b)| a.id != b.id)
        );
    }

    #[test]
    fn unicode_neighbors_and_every_output_span() {
        let engine = Engine::new().unwrap();
        for text in [
            "😀  x",
            "👩‍💻  e\u{301}",
            "שלום  عالم",
            "a\r\n  b\t  c",
            "\u{200b}  x",
            "\u{a0}  \u{a0}",
        ] {
            let suggestions = engine.check(text, 0, "en-US").unwrap();
            assert!(!suggestions.is_empty());
            for suggestion in suggestions {
                let [s, e] = suggestion.span.utf8;
                assert_eq!(
                    suggestion.span.utf16,
                    [
                        text[..s].encode_utf16().count(),
                        text[..e].encode_utf16().count()
                    ]
                );
                assert_eq!(
                    suggestion.span.scalar,
                    [text[..s].chars().count(), text[..e].chars().count()]
                );
                assert!(TextOffsets::new(text).is_grapheme_boundary(s));
                assert!(TextOffsets::new(text).is_grapheme_boundary(e));
            }
        }
        let suggestion = engine.check("👩‍💻  x", 0, "en-US").unwrap().remove(0);
        assert_eq!(suggestion.span.utf8, [11, 13]);
        assert_eq!(suggestion.span.utf16, [5, 7]);
        assert_eq!(suggestion.span.scalar, [3, 5]);
    }

    #[test]
    fn empty_single_unicode_whitespace_and_long_runs() {
        let engine = Engine::new().unwrap();
        for text in [
            "",
            " ",
            "a b",
            "\t\t",
            "\r\n",
            "\u{a0}\u{a0}",
            " \t ",
            " \n ",
        ] {
            assert!(engine.check(text, 0, "en-US").unwrap().is_empty());
        }
        let long = " ".repeat(20_000);
        let actual = engine.check(&long, 0, "en-US").unwrap();
        assert_eq!(actual.len(), 1);
        assert_eq!(actual[0].span.utf8, [0, 20_000]);
        assert!(engine.check("text", 0, "fr").is_err());
        assert!(
            engine
                .check("text", 9_007_199_254_740_992, "en-US")
                .is_err()
        );
    }
}
