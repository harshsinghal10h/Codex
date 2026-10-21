// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
//! Unicode segmentation without joining separate original-source fragments.
use crate::TextOffsets;
use nw_schema::{Span, Token, TokenKind};
use unicode_segmentation::UnicodeSegmentation;

#[derive(Debug, Clone, PartialEq)]
pub struct Segments {
    pub tokens: Vec<Token>,
    pub sentences: Vec<Span>,
    pub paragraphs: Vec<Span>,
}

pub type Segmentation = Segments;

/// Segment each canonical, ordered source region independently using UAX #29.
pub fn segment(text: &str, prose_regions: &[Span]) -> Result<Segments, String> {
    let offsets = TextOffsets::new(text);
    let mut previous_end = 0;
    for region in prose_regions {
        let canonical = offsets
            .from_utf8(region.utf8)
            .map_err(|error| format!("invalid prose region: {error:?}"))?;
        if canonical != *region || region.utf8[0] < previous_end {
            return Err(
                "prose regions must have canonical coordinates and be ordered/disjoint".into(),
            );
        }
        previous_end = region.utf8[1];
    }
    let span = |start, end| {
        offsets
            .from_utf8([start, end])
            .map_err(|error| format!("invalid segmentation boundary: {error:?}"))
    };
    let mut result = Segments {
        tokens: Vec::new(),
        sentences: Vec::new(),
        paragraphs: Vec::new(),
    };
    for region in prose_regions {
        let [start, end] = region.utf8;
        let source = &text[start..end];
        for (relative, word) in source
            .split_word_bound_indices()
            .filter(|(_, s)| has_word(s))
        {
            result.tokens.push(Token {
                span: span(start + relative, start + relative + word.len())?,
                kind: TokenKind::Word,
            });
        }
        for (block_start, block_end) in paragraph_blocks(source) {
            let raw = &source[block_start..block_end];
            let trimmed = raw.trim_matches(char::is_whitespace);
            if !has_word(trimmed) {
                continue;
            }
            let leading = raw.len() - raw.trim_start_matches(char::is_whitespace).len();
            let paragraph_start = start + block_start + leading;
            result
                .paragraphs
                .push(span(paragraph_start, paragraph_start + trimmed.len())?);
            for (relative, sentence) in trimmed.split_sentence_bound_indices() {
                let sentence_text = sentence.trim_matches(char::is_whitespace);
                if !has_word(sentence_text) {
                    continue;
                }
                let leading =
                    sentence.len() - sentence.trim_start_matches(char::is_whitespace).len();
                let sentence_start = paragraph_start + relative + leading;
                result
                    .sentences
                    .push(span(sentence_start, sentence_start + sentence_text.len())?);
            }
        }
    }
    Ok(result)
}

fn has_word(text: &str) -> bool {
    text.chars().any(|ch| ch.is_alphabetic() || ch.is_numeric())
}

/// Nonblank blocks retain their original line endings. CRLF is one logical break.
fn paragraph_blocks(text: &str) -> Vec<(usize, usize)> {
    let bytes = text.as_bytes();
    let mut blocks = Vec::new();
    let mut block_start = None;
    let mut line_start = 0;
    while line_start < bytes.len() {
        let mut content_end = line_start;
        while content_end < bytes.len() && !matches!(bytes[content_end], b'\r' | b'\n') {
            content_end += 1;
        }
        let mut line_end = content_end;
        if line_end < bytes.len() {
            line_end += 1;
            if bytes[content_end] == b'\r' && bytes.get(line_end) == Some(&b'\n') {
                line_end += 1;
            }
        }
        let blank = bytes[line_start..content_end]
            .iter()
            .all(|b| matches!(b, b' ' | b'\t'));
        if blank {
            if let Some(start) = block_start.take() {
                blocks.push((start, line_start));
            }
        } else {
            block_start.get_or_insert(line_start);
        }
        line_start = line_end;
    }
    if let Some(start) = block_start {
        blocks.push((start, text.len()));
    }
    blocks
}

#[cfg(test)]
mod tests {
    use super::*;

    fn full(text: &str) -> Segments {
        let regions = if text.is_empty() {
            vec![]
        } else {
            vec![TextOffsets::new(text).from_utf8([0, text.len()]).unwrap()]
        };
        segment(text, &regions).unwrap()
    }
    fn slices<'a>(text: &'a str, spans: &[Span]) -> Vec<&'a str> {
        spans.iter().map(|s| &text[s.utf8[0]..s.utf8[1]]).collect()
    }

    #[test]
    fn independent_original_coordinates_and_unicode_words() {
        let text = "😀 Hi  café.";
        let result = full(text);
        assert_eq!(
            result.tokens[0].span,
            Span {
                utf8: [5, 7],
                utf16: [3, 5],
                scalar: [2, 4]
            }
        );
        assert_eq!(
            result.tokens[1].span,
            Span {
                utf8: [9, 14],
                utf16: [7, 11],
                scalar: [6, 10]
            }
        );
        let text = "👩‍💻 e\u{301} שלום عالم 12.5 Ⅷ ²\t中\u{a0}Z\u{200b}end";
        let result = full(text);
        let words: Vec<_> = result
            .tokens
            .iter()
            .map(|t| &text[t.span.utf8[0]..t.span.utf8[1]])
            .collect();
        assert_eq!(
            words,
            [
                "e\u{301}", "שלום", "عالم", "12.5", "Ⅷ", "²", "中", "Z", "end"
            ]
        );
        for token in result.tokens {
            assert_eq!(
                TextOffsets::new(text).from_utf8(token.span.utf8).unwrap(),
                token.span
            );
        }
    }

    #[test]
    fn crlf_atomic_blank_lines_unicode_trim_and_sentence_fragments() {
        let text = "\u{a0}First.\r\nNext!\r\n \t\r\n\rSecond\r\t\rThird\n\n😀 !!!\n\n";
        let result = full(text);
        assert_eq!(
            slices(text, &result.paragraphs),
            ["First.\r\nNext!", "Second", "Third"]
        );
        assert_eq!(
            slices(text, &result.sentences),
            ["First.", "Next!", "Second", "Third"]
        );
        let nonblank_nbsp = "One\n\u{a0}\nTwo";
        assert_eq!(full(nonblank_nbsp).paragraphs.len(), 1);
        for text in ["", " \t\r\n\r", "😀 !!!"] {
            let result = full(text);
            assert!(
                result.tokens.is_empty()
                    && result.paragraphs.is_empty()
                    && result.sentences.is_empty()
            );
        }
    }

    #[test]
    fn protected_gaps_never_create_words_or_sentences() {
        let text = "pre`code`fix";
        let offsets = TextOffsets::new(text);
        let regions = [
            offsets.from_utf8([0, 3]).unwrap(),
            offsets.from_utf8([9, 12]).unwrap(),
        ];
        let result = segment(text, &regions).unwrap();
        assert_eq!(slices(text, &result.paragraphs), ["pre", "fix"]);
        assert_eq!(slices(text, &result.sentences), ["pre", "fix"]);
        assert_eq!(result.tokens.len(), 2);
    }

    #[test]
    fn malformed_and_inconsistent_regions_return_errors() {
        let text = "😀 hello";
        let offsets = TextOffsets::new(text);
        let mut bad = offsets.from_utf8([0, 4]).unwrap();
        bad.utf16 = [0, 1];
        assert!(segment(text, &[bad]).is_err());
        for range in [[4, 0], [0, usize::MAX], [1, 4]] {
            assert!(
                segment(
                    text,
                    &[Span {
                        utf8: range,
                        utf16: [0, 0],
                        scalar: [0, 0]
                    }]
                )
                .is_err()
            );
        }
        let region = offsets.from_utf8([0, text.len()]).unwrap();
        assert!(segment(text, &[region.clone(), region]).is_err());
    }
}
