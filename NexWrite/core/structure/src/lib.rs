// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
//! Conservative CommonMark source projection. This does not render or decode text.
use nw_schema::{InputFormat, ProtectedRegion, ProtectionKind, Span};
use nw_text::TextOffsets;
use pulldown_cmark::{Event, Parser, Tag, TagEnd};
use std::ops::Range;

#[derive(Debug, Clone, PartialEq)]
pub struct Projection {
    pub prose_regions: Vec<Span>,
    pub protected_regions: Vec<ProtectedRegion>,
    pub warnings: Vec<String>,
}

#[derive(Clone, Debug)]
struct Protected {
    range: Range<usize>,
    kind: ProtectionKind,
}

#[derive(Clone, Debug)]
struct Line {
    start: usize,
    content_end: usize,
    end: usize,
}

/// Retain original parser Text ranges, subtracting conservative source protections.
/// No separate retained fragments are joined across an excluded source byte.
pub fn project(text: &str, format: InputFormat) -> Result<Projection, String> {
    let offsets = TextOffsets::new(text);
    let span = |range: Range<usize>| {
        offsets
            .from_utf8([range.start, range.end])
            .map_err(|error| format!("invalid Markdown source boundary: {error:?}"))
    };
    if format == InputFormat::Plain {
        return Ok(Projection {
            prose_regions: if text.is_empty() {
                Vec::new()
            } else {
                vec![span(0..text.len())?]
            },
            protected_regions: Vec::new(),
            warnings: Vec::new(),
        });
    }
    let lines = source_lines(text);
    let parser = Parser::new(text);
    let mut protected: Vec<Protected> = parser
        .reference_definitions()
        .iter()
        .map(|(_, definition)| Protected {
            range: containing_lines(&lines, definition.span.clone()),
            kind: ProtectionKind::ReferenceDefinition,
        })
        .collect();
    let bracket_offsets: Vec<_> = text.match_indices('[').map(|(index, _)| index).collect();
    let mut candidates = Vec::new();
    let mut reference_barriers = Vec::new();
    for (event, range) in parser.into_offset_iter() {
        if matches!(
            &event,
            Event::Start(Tag::BlockQuote(_) | Tag::List(_) | Tag::Item)
        ) {
            let first = bracket_offsets.partition_point(|byte| *byte < range.start);
            if !bracket_offsets
                .get(first)
                .is_some_and(|byte| *byte < range.end)
            {
                reference_barriers.push(range.clone());
            }
        }
        if matches!(
            &event,
            Event::Start(Tag::Paragraph | Tag::Heading { .. } | Tag::CodeBlock(_) | Tag::HtmlBlock)
                | Event::Rule
        ) {
            reference_barriers.push(range.clone());
        }
        match event {
            Event::Start(Tag::CodeBlock(_)) => push(
                &mut protected,
                containing_lines(&lines, range),
                ProtectionKind::Code,
            ),
            Event::Code(_) => push(&mut protected, range, ProtectionKind::Code),
            Event::Start(Tag::HtmlBlock) => push(
                &mut protected,
                containing_lines(&lines, range),
                ProtectionKind::Html,
            ),
            Event::Html(_) => push(&mut protected, range, ProtectionKind::Html),
            Event::InlineHtml(_) => push(
                &mut protected,
                containing_lines(&lines, range),
                ProtectionKind::Html,
            ),
            Event::Start(Tag::Link { .. }) => push(&mut protected, range, ProtectionKind::Link),
            Event::Start(Tag::Image { .. }) => push(&mut protected, range, ProtectionKind::Image),
            Event::Text(_) | Event::SoftBreak => candidates.push(range),
            Event::HardBreak => {
                let line = lines.get(lines.partition_point(|line| line.end <= range.start));
                if let Some(line) = line {
                    let mut trailing = line.content_end;
                    while trailing > line.start && text.as_bytes()[trailing - 1] == b' ' {
                        trailing -= 1;
                    }
                    if line.content_end - trailing >= 2 {
                        push(
                            &mut protected,
                            trailing..line.content_end,
                            ProtectionKind::HardBreak,
                        );
                    } else if text.as_bytes().get(line.content_end.wrapping_sub(1)) == Some(&b'\\')
                    {
                        push(
                            &mut protected,
                            line.content_end - 1..line.content_end,
                            ProtectionKind::HardBreak,
                        );
                    }
                }
                push(&mut protected, range, ProtectionKind::HardBreak);
            }
            _ => {}
        }
    }
    candidates = merge_ranges(candidates);
    // The parser's map keeps only the first definition for each label. Reparse
    // disjoint hidden source runs, preserving entire multiline destinations/titles.
    // A run qualifies only when the parser emits definitions and container events:
    // ordinary source text and non-reference blocks remain outside these runs.
    reference_barriers.extend_from_slice(&candidates);
    reference_barriers = merge_ranges(reference_barriers);
    let mut hidden_start = None;
    for line in &lines {
        let first = reference_barriers.partition_point(|range| range.end <= line.start);
        let hidden = !reference_barriers
            .get(first)
            .is_some_and(|range| range.start < line.end);
        let blank = text.as_bytes()[line.start..line.content_end]
            .iter()
            .all(|b| matches!(b, b' ' | b'\t' | b'>'));
        if hidden && !blank {
            hidden_start.get_or_insert(line.start);
        } else if let Some(start) = hidden_start.take() {
            protect_reference_run(text, &lines, start..line.start, &mut protected);
        }
    }
    if let Some(start) = hidden_start {
        protect_reference_run(text, &lines, start..text.len(), &mut protected);
    }
    for line in &lines {
        let source = &text[line.start..line.content_end];
        let indentation = source
            .bytes()
            .take_while(|b| matches!(b, b' ' | b'\t'))
            .count();
        push(
            &mut protected,
            line.start..line.start + indentation,
            ProtectionKind::Indentation,
        );
    }
    let mut semantic: Vec<_> = protected
        .iter()
        .filter(|part| priority(part.kind) <= priority(ProtectionKind::Entity))
        .cloned()
        .collect();
    let mut fragmenting: Vec<_> = protected
        .iter()
        .filter(|part| priority(part.kind) > priority(ProtectionKind::Entity))
        .cloned()
        .collect();
    protected = coalesce(protected);
    let previous = protected.len();
    protect_escapes_and_entities(text, &mut protected);
    semantic.extend_from_slice(&protected[previous..]);
    protected = coalesce(protected);
    let html_opaque = coalesce(
        semantic
            .iter()
            .filter(|part| part.kind != ProtectionKind::Html)
            .cloned()
            .collect(),
    );
    let previous = protected.len();
    let ambiguous = protect_ambiguous(text, &lines, &html_opaque, &mut protected);
    semantic.extend_from_slice(&protected[previous..]);
    protected = coalesce(protected);
    // Only parser text can be prose. Non-whitespace source outside it is markup;
    // blank inter-block whitespace may remain neutral. Existing semantic exclusions
    // do not themselves create syntax-fragmentation warnings.
    let mut syntax = Vec::new();
    let mut cursor = 0;
    for candidate in &candidates {
        for gap in subtract(cursor..candidate.start, &protected) {
            protect_syntax(text, gap, &mut syntax);
        }
        cursor = candidate.end;
    }
    for gap in subtract(cursor..text.len(), &protected) {
        protect_syntax(text, gap, &mut syntax);
    }
    fragmenting.extend_from_slice(&syntax);
    protected.extend(syntax);
    protected = coalesce(protected);
    semantic = coalesce(semantic);
    let content_omitted = !semantic.is_empty();
    let fragmented = fragmenting
        .iter()
        .any(|part| !subtract(part.range.clone(), &semantic).is_empty())
        && candidates.iter().any(|candidate| {
            subtract(candidate.clone(), &protected).iter().any(|range| {
                text[range.clone()]
                    .chars()
                    .any(|ch| ch.is_alphabetic() || ch.is_numeric())
            })
        });
    let prose = merge_ranges(
        candidates
            .into_iter()
            .flat_map(|range| subtract(range, &protected))
            .collect(),
    );
    let mut warnings = vec!["markdown_source_projection".to_owned()];
    if content_omitted {
        warnings.push("markdown_content_omitted".into());
    }
    if fragmented {
        warnings.push("markdown_prose_fragmented".into());
    }
    if ambiguous {
        warnings.push("markdown_ambiguous_construct".into());
    }
    Ok(Projection {
        prose_regions: prose.into_iter().map(&span).collect::<Result<_, _>>()?,
        protected_regions: protected
            .into_iter()
            .map(|part| {
                Ok(ProtectedRegion {
                    span: span(part.range)?,
                    kind: part.kind,
                })
            })
            .collect::<Result<_, String>>()?,
        warnings,
    })
}

fn protect_reference_run(
    text: &str,
    lines: &[Line],
    range: Range<usize>,
    protected: &mut Vec<Protected>,
) {
    let parser = Parser::new(&text[range.clone()]);
    let has_definition = parser.reference_definitions().iter().next().is_some();
    let only_containers = parser.into_iter().all(|event| {
        matches!(
            event,
            Event::Start(Tag::BlockQuote(_) | Tag::List(_) | Tag::Item)
                | Event::End(TagEnd::BlockQuote(_) | TagEnd::List(_) | TagEnd::Item)
        )
    });
    if has_definition && only_containers {
        push(
            protected,
            containing_lines(lines, range),
            ProtectionKind::ReferenceDefinition,
        );
    }
}

fn push(protected: &mut Vec<Protected>, range: Range<usize>, kind: ProtectionKind) {
    if range.start < range.end {
        protected.push(Protected { range, kind });
    }
}

fn priority(kind: ProtectionKind) -> u8 {
    match kind {
        ProtectionKind::Code => 0,
        ProtectionKind::Html => 1,
        ProtectionKind::Image => 2,
        ProtectionKind::Link => 3,
        ProtectionKind::ReferenceDefinition => 4,
        ProtectionKind::AmbiguousMarkup => 5,
        ProtectionKind::Escape => 6,
        ProtectionKind::Entity => 7,
        ProtectionKind::HardBreak => 8,
        ProtectionKind::Indentation => 9,
        ProtectionKind::Syntax => 10,
    }
}

fn coalesce(mut protected: Vec<Protected>) -> Vec<Protected> {
    protected.sort_by_key(|part| (part.range.start, part.range.end));
    let mut result: Vec<Protected> = Vec::new();
    for part in protected {
        if let Some(last) = result
            .last_mut()
            .filter(|last| part.range.start <= last.range.end)
        {
            last.range.end = last.range.end.max(part.range.end);
            if priority(part.kind) < priority(last.kind) {
                last.kind = part.kind;
            }
        } else {
            result.push(part);
        }
    }
    result
}

fn merge_ranges(mut ranges: Vec<Range<usize>>) -> Vec<Range<usize>> {
    ranges.retain(|range| range.start < range.end);
    ranges.sort_by_key(|range| (range.start, range.end));
    let mut result: Vec<Range<usize>> = Vec::new();
    for range in ranges {
        if let Some(last) = result.last_mut().filter(|last| range.start <= last.end) {
            last.end = last.end.max(range.end);
        } else {
            result.push(range);
        }
    }
    result
}

fn subtract(range: Range<usize>, protected: &[Protected]) -> Vec<Range<usize>> {
    let mut result = Vec::new();
    let mut cursor = range.start;
    let first = protected.partition_point(|part| part.range.end <= cursor);
    for part in &protected[first..] {
        if part.range.start >= range.end {
            break;
        }
        if part.range.start > cursor {
            result.push(cursor..part.range.start.min(range.end));
        }
        cursor = cursor.max(part.range.end);
        if cursor >= range.end {
            break;
        }
    }
    if cursor < range.end {
        result.push(cursor..range.end);
    }
    result
}

fn is_protected(byte: usize, protected: &[Protected]) -> bool {
    let index = protected.partition_point(|part| part.range.end <= byte);
    protected
        .get(index)
        .is_some_and(|part| part.range.start <= byte)
}

fn source_lines(text: &str) -> Vec<Line> {
    let bytes = text.as_bytes();
    let mut lines = Vec::new();
    let mut start = 0;
    while start < bytes.len() {
        let mut content_end = start;
        while content_end < bytes.len() && !matches!(bytes[content_end], b'\r' | b'\n') {
            content_end += 1;
        }
        let mut end = content_end;
        if end < bytes.len() {
            end += 1;
            if bytes[content_end] == b'\r' && bytes.get(end) == Some(&b'\n') {
                end += 1;
            }
        }
        lines.push(Line {
            start,
            content_end,
            end,
        });
        start = end;
    }
    lines
}

fn containing_lines(lines: &[Line], range: Range<usize>) -> Range<usize> {
    let first = lines.partition_point(|line| line.end <= range.start);
    let end_byte = range.end.saturating_sub(1).max(range.start);
    let last = lines.partition_point(|line| line.end <= end_byte);
    match (lines.get(first), lines.get(last)) {
        (Some(first), Some(last)) => first.start..last.end,
        _ => range,
    }
}

fn protect_escapes_and_entities(text: &str, protected: &mut Vec<Protected>) {
    let known = protected.clone();
    let bytes = text.as_bytes();
    let mut index = 0;
    while index < bytes.len() {
        if is_protected(index, &known) {
            index += 1;
            continue;
        }
        if bytes[index] == b'\\' && bytes.get(index + 1).is_some_and(u8::is_ascii_punctuation) {
            push(protected, index..index + 2, ProtectionKind::Escape);
            index += 2;
        } else if bytes[index] == b'&' {
            let limit = (index + 34).min(bytes.len());
            let mut end = index + 1;
            while end < limit && (bytes[end].is_ascii_alphanumeric() || bytes[end] == b'#') {
                end += 1;
            }
            if bytes.get(end) == Some(&b';') {
                let candidate = &text[index..end + 1];
                // Recognition only: decoded text never supplies source coordinates.
                let recognized = Parser::new(candidate).any(
                    |event| matches!(event, Event::Text(value) if value.as_ref() != candidate),
                );
                if recognized {
                    push(protected, index..end + 1, ProtectionKind::Entity);
                    index = end + 1;
                    continue;
                }
            }
            index += 1;
        } else {
            index += 1;
        }
    }
}

fn protect_ambiguous(
    text: &str,
    lines: &[Line],
    html_opaque: &[Protected],
    protected: &mut Vec<Protected>,
) -> bool {
    let known = protected.clone();
    let bytes = text.as_bytes();
    let mut ambiguous = false;
    let mut openers = Vec::new();
    let mut index = 0;
    while index < bytes.len() {
        if is_protected(index, &known) {
            index += 1;
            continue;
        }
        match bytes[index] {
            b'`' => {
                push(
                    protected,
                    index..bytes.len(),
                    ProtectionKind::AmbiguousMarkup,
                );
                ambiguous = true;
                break;
            }
            b'[' => {
                let start =
                    if index > 0 && bytes[index - 1] == b'!' && !is_protected(index - 1, &known) {
                        index - 1
                    } else {
                        index
                    };
                openers.push(start);
            }
            b']' => {
                openers.pop();
            }
            _ => {}
        }
        index += 1;
    }
    if let Some(start) = openers.first() {
        push(
            protected,
            *start..bytes.len(),
            ProtectionKind::AmbiguousMarkup,
        );
        ambiguous = true;
    }
    for line in lines {
        // One reverse scan per logical line; never rescan a suffix per opener.
        let last_close = bytes[line.start..line.content_end]
            .iter()
            .rposition(|b| *b == b'>')
            .map(|relative| line.start + relative);
        let mut index = line.start;
        while index < line.content_end {
            if bytes[index] == b'<'
                && !is_protected(index, html_opaque)
                && bytes
                    .get(index + 1)
                    .is_some_and(|b| b.is_ascii_alphabetic() || matches!(b, b'/' | b'!'))
                && last_close.is_none_or(|last| last < index)
            {
                push(
                    protected,
                    line.start..line.end,
                    ProtectionKind::AmbiguousMarkup,
                );
                ambiguous = true;
                break;
            }
            index += 1;
        }
    }
    ambiguous
}

fn protect_syntax(text: &str, range: Range<usize>, protected: &mut Vec<Protected>) {
    let mut start = None;
    for (relative, ch) in text[range.clone()].char_indices() {
        let index = range.start + relative;
        if ch.is_whitespace() {
            if let Some(start) = start.take() {
                push(protected, start..index, ProtectionKind::Syntax);
            }
        } else {
            start.get_or_insert(index);
        }
    }
    if let Some(start) = start {
        push(protected, start..range.end, ProtectionKind::Syntax);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use nw_text::segment;

    fn markdown(text: &str) -> Projection {
        project(text, InputFormat::Markdown).unwrap()
    }
    fn prose<'a>(text: &'a str, projection: &Projection) -> Vec<&'a str> {
        projection
            .prose_regions
            .iter()
            .map(|span| &text[span.utf8[0]..span.utf8[1]])
            .collect()
    }
    fn assert_invariants(text: &str, projection: &Projection) {
        let offsets = TextOffsets::new(text);
        for span in projection
            .prose_regions
            .iter()
            .chain(projection.protected_regions.iter().map(|p| &p.span))
        {
            assert!(span.utf8[0] < span.utf8[1]);
            assert_eq!(*span, offsets.from_utf8(span.utf8).unwrap());
        }
        for adjacent in projection.prose_regions.windows(2) {
            assert!(adjacent[0].utf8[1] < adjacent[1].utf8[0]);
        }
        for adjacent in projection.protected_regions.windows(2) {
            assert!(adjacent[0].span.utf8[1] < adjacent[1].span.utf8[0]);
        }
        for span in &projection.prose_regions {
            for protected in &projection.protected_regions {
                assert!(
                    span.utf8[1] <= protected.span.utf8[0]
                        || span.utf8[0] >= protected.span.utf8[1]
                );
            }
        }
        let segmented = segment(text, &projection.prose_regions).unwrap();
        for span in segmented
            .tokens
            .iter()
            .map(|t| &t.span)
            .chain(&segmented.sentences)
            .chain(&segmented.paragraphs)
        {
            assert!(
                projection
                    .prose_regions
                    .iter()
                    .any(|r| r.utf8[0] <= span.utf8[0] && span.utf8[1] <= r.utf8[1])
            );
        }
    }

    #[test]
    fn plain_is_lossless_and_markdown_plain_prose_keeps_soft_crlf() {
        for text in [
            "",
            "😀 Hi  café.",
            " [unclosed `code\n <bad",
            "One\r\nTwo.\n\nThree",
        ] {
            let projection = project(text, InputFormat::Plain).unwrap();
            assert_eq!(
                projection.prose_regions.len(),
                usize::from(!text.is_empty())
            );
            assert!(projection.protected_regions.is_empty() && projection.warnings.is_empty());
            if !text.is_empty() {
                assert_eq!(prose(text, &projection), [text]);
            }
            assert_invariants(text, &projection);
        }
        let text = "One\r\nTwo.\n\nThree";
        let projection = markdown(text);
        assert_eq!(prose(text, &projection), ["One\r\nTwo.", "Three"]);
        assert_eq!(projection.warnings, ["markdown_source_projection"]);
        assert!(projection.protected_regions.is_empty());
        assert_invariants(text, &projection);
    }

    #[test]
    fn code_links_images_and_reference_definition_are_wholly_protected() {
        let fixtures = [
            ("before `😀 code` after", "`😀 code`", ProtectionKind::Code),
            (
                "before\n\n```rs\n😀 code\n```\n\nafter",
                "```rs\n😀 code\n```\n",
                ProtectionKind::Code,
            ),
            (
                "before\n\n    indented\n    😀 code\n\nafter",
                "    indented\n    😀 code\n",
                ProtectionKind::Code,
            ),
            (
                "before [😀 label](https://example.test/a) after",
                "[😀 label](https://example.test/a)",
                ProtectionKind::Link,
            ),
            (
                "before ![😀 alt](image.png) after",
                "![😀 alt](image.png)",
                ProtectionKind::Image,
            ),
            (
                "[ref]: https://example.test \"title\"\n\nbefore [label][ref] after",
                "[ref]: https://example.test \"title\"\n",
                ProtectionKind::ReferenceDefinition,
            ),
            ("```\nunclosed 😀", "```\nunclosed 😀", ProtectionKind::Code),
        ];
        for (text, excluded, kind) in fixtures {
            let projection = markdown(text);
            let start = text.find(excluded).unwrap();
            assert!(
                projection.protected_regions.iter().any(|p| p.kind == kind
                    && p.span.utf8[0] <= start
                    && p.span.utf8[1] >= start + excluded.len()),
                "{text:?}: {projection:?}"
            );
            assert!(
                projection
                    .warnings
                    .iter()
                    .any(|w| w == "markdown_content_omitted")
            );
            assert_invariants(text, &projection);
        }
    }

    #[test]
    fn duplicate_multiline_reference_definitions_keep_whole_source_lines() {
        let text = "[a]:\n  /first \"title\"\n\n[a]:\n  /second \"second  title\"\n\nprose  text";
        let projection = markdown(text);
        assert_eq!(projection.protected_regions.len(), 2);
        assert_eq!(
            projection
                .protected_regions
                .iter()
                .map(|p| (&text[p.span.utf8[0]..p.span.utf8[1]], p.kind))
                .collect::<Vec<_>>(),
            [
                (
                    "[a]:\n  /first \"title\"\n",
                    ProtectionKind::ReferenceDefinition
                ),
                (
                    "[a]:\n  /second \"second  title\"\n",
                    ProtectionKind::ReferenceDefinition
                ),
            ]
        );
        assert_eq!(prose(text, &projection), ["prose  text"]);
        assert_eq!(
            projection.warnings,
            ["markdown_source_projection", "markdown_content_omitted"]
        );
        assert_invariants(text, &projection);
    }

    #[test]
    fn reference_titles_with_brackets_never_split_duplicate_definitions() {
        for text in [
            "[a]: /first\n\n[a]: /second\n  \"a [bracket]  title\"\n\nprose  text",
            "[a]: /first\n\n[a]:\n  /second \"a [b]  title\"\n\nprose  text",
            "[a]: /first\n\n[a]: /second \"title\n[b]: literal  title\ndone\"\n\nprose  text",
            "[a]: /first\n[a]: /second \"a [b]  title\"\n\nprose  text",
        ] {
            let projection = markdown(text);
            let semantic_end = text.find("\n\nprose").unwrap() + 1;
            assert!(
                projection
                    .protected_regions
                    .iter()
                    .all(|p| p.kind == ProtectionKind::ReferenceDefinition)
            );
            assert_eq!(
                projection.protected_regions.last().unwrap().span.utf8[1],
                semantic_end
            );
            assert_eq!(prose(text, &projection), ["prose  text"]);
            assert_eq!(
                projection.warnings,
                ["markdown_source_projection", "markdown_content_omitted"]
            );
            assert_invariants(text, &projection);
        }
        // Hidden markup and ordinary literal bracket text must retain their kinds.
        for text in [
            "[a]: /first\n***\n\nprose  text",
            "[a]: /first\n#\n\nprose  text",
            "[a]: /first\n\n[literal]  prose",
        ] {
            let projection = markdown(text);
            assert!(
                projection
                    .prose_regions
                    .iter()
                    .any(|p| text[p.utf8[0]..p.utf8[1]].contains("prose"))
            );
            assert_invariants(text, &projection);
        }
    }

    #[test]
    fn empty_containers_beside_definitions_remain_markup() {
        for text in [
            "[a]: /first\n\n-\n\nprose  text",
            "> [a]: /first\n>\n> [a]: /second\n\nprose  text",
        ] {
            let projection = markdown(text);
            assert!(
                projection
                    .warnings
                    .iter()
                    .any(|w| w == "markdown_prose_fragmented"),
                "{text:?}: {projection:?}"
            );
            assert_invariants(text, &projection);
        }
        let text = "[a]: /first\n\n[a]: /second \"title\n\\-\nend\"\n\nprose  text";
        let projection = markdown(text);
        assert_eq!(
            projection.warnings,
            ["markdown_source_projection", "markdown_content_omitted"]
        );
        assert_invariants(text, &projection);
    }

    #[test]
    fn repeated_html_openers_with_a_final_closer_do_not_become_ambiguous() {
        let mut text = "<a ".repeat(349_525);
        text.push('>');
        assert_eq!(text.len(), 1_048_576);
        let projection = markdown(&text);
        assert!(
            !projection
                .warnings
                .iter()
                .any(|w| w == "markdown_ambiguous_construct")
        );
        assert_invariants(&text, &projection);
    }

    #[test]
    fn html_blocks_and_inline_html_exclude_complete_source_lines() {
        for (text, excluded) in [
            (
                "before\n\n<div>😀 block</div>\n\nafter",
                "<div>😀 block</div>\n",
            ),
            (
                "before\n😀 entire <b>tag</b> line\r\nafter",
                "😀 entire <b>tag</b> line\r\n",
            ),
            ("before <span\nafter", "before <span\n"),
        ] {
            let projection = markdown(text);
            let start = text.find(excluded).unwrap();
            assert!(
                projection
                    .protected_regions
                    .iter()
                    .any(|p| p.span.utf8[0] <= start && p.span.utf8[1] >= start + excluded.len())
            );
            assert_invariants(text, &projection);
        }
        assert!(
            markdown("before <span\nafter")
                .warnings
                .iter()
                .any(|w| w == "markdown_ambiguous_construct")
        );
    }

    #[test]
    fn malformed_html_openers_warn_even_inside_parser_html_blocks() {
        for text in ["<script\nalert\n", "<!-- comment\ncontinued -->\n"] {
            let projection = markdown(text);
            assert!(
                projection
                    .warnings
                    .iter()
                    .any(|w| w == "markdown_ambiguous_construct")
            );
            assert!(projection.prose_regions.is_empty());
            assert_invariants(text, &projection);
        }
        for text in ["```\n<bad\n```", "`<bad`", "[<bad](url)", "\\<bad"] {
            let projection = markdown(text);
            assert!(
                !projection
                    .warnings
                    .iter()
                    .any(|w| w == "markdown_ambiguous_construct")
            );
            assert_invariants(text, &projection);
        }
    }

    #[test]
    fn escapes_entities_indentation_and_hard_breaks_keep_source_coordinates() {
        let text = "😀 pre\\*fix &amp; &#65; &#x1F600; post\r\n  next  \r\nlast\\\nend";
        let projection = markdown(text);
        for excluded in [
            "\\*",
            "&amp;",
            "&#65;",
            "&#x1F600;",
            "  next".get(..2).unwrap(),
            "  \r\n",
            "\\\n",
        ] {
            let start = if excluded == "  " {
                text.find("  next").unwrap()
            } else {
                text.find(excluded).unwrap()
            };
            assert!(
                projection
                    .protected_regions
                    .iter()
                    .any(|p| p.span.utf8[0] <= start && p.span.utf8[1] >= start + excluded.len()),
                "{excluded:?}: {projection:?}"
            );
        }
        assert!(
            projection
                .warnings
                .iter()
                .any(|w| w == "markdown_prose_fragmented")
        );
        let after = projection
            .prose_regions
            .iter()
            .find(|p| &text[p.utf8[0]..p.utf8[1]] == "end")
            .unwrap();
        assert_eq!(after.utf16[0], text[..after.utf8[0]].encode_utf16().count());
        assert_invariants(text, &projection);
        let escaped_amp = "\\&amp; literal &madeup; valid &copy;";
        let projection = markdown(escaped_amp);
        assert!(
            prose(escaped_amp, &projection)
                .iter()
                .any(|p| p.contains("amp;") && p.contains("&madeup;"))
        );
        assert_invariants(escaped_amp, &projection);
    }

    #[test]
    fn malformed_openers_protect_to_eof_but_literal_pairs_remain() {
        for (text, excluded) in [
            ("pre `unclosed 😀", "`unclosed 😀"),
            ("pre [unclosed 😀", "[unclosed 😀"),
            ("pre ![unclosed 😀", "![unclosed 😀"),
            ("pre `` mismatch ` tail", "`` mismatch ` tail"),
        ] {
            let projection = markdown(text);
            let start = text.find(excluded).unwrap();
            assert!(
                projection
                    .protected_regions
                    .iter()
                    .any(|p| p.kind == ProtectionKind::AmbiguousMarkup
                        && p.span.utf8 == [start, text.len()]),
                "{text:?}: {projection:?}"
            );
            assert_eq!(
                projection.warnings,
                [
                    "markdown_source_projection",
                    "markdown_content_omitted",
                    "markdown_ambiguous_construct"
                ]
            );
            assert_invariants(text, &projection);
        }
        for text in [
            "[literal pair] remains",
            "\\` literal tick",
            "\\[ literal bracket",
            "[nested [pair]]",
            "[unresolved][reference]",
        ] {
            let projection = markdown(text);
            assert!(
                !projection
                    .warnings
                    .iter()
                    .any(|w| w == "markdown_ambiguous_construct"),
                "{text:?}: {projection:?}"
            );
            assert_invariants(text, &projection);
        }
    }

    #[test]
    fn syntax_fragments_are_never_joined_and_priority_union_is_stable() {
        let text = "pre*mid*fix";
        let projection = markdown(text);
        assert_eq!(prose(text, &projection), ["pre", "mid", "fix"]);
        assert_eq!(
            projection.warnings,
            ["markdown_source_projection", "markdown_prose_fragmented"]
        );
        let result = segment(text, &projection.prose_regions).unwrap();
        assert_eq!(result.tokens.len(), 3);
        assert_eq!(result.sentences.len(), 3);
        assert_invariants(text, &projection);
        let merged = coalesce(vec![
            Protected {
                range: 4..8,
                kind: ProtectionKind::Link,
            },
            Protected {
                range: 0..2,
                kind: ProtectionKind::Syntax,
            },
            Protected {
                range: 2..4,
                kind: ProtectionKind::Code,
            },
            Protected {
                range: 9..10,
                kind: ProtectionKind::Html,
            },
        ]);
        assert_eq!(merged.len(), 2);
        assert_eq!(merged[0].range, 0..8);
        assert_eq!(merged[0].kind, ProtectionKind::Code);
    }
}
