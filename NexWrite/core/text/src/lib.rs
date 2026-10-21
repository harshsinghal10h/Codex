// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
//! Lossless offsets in the original UTF-8 document. No bidi reordering is performed.
use nw_schema::Span;
use unicode_segmentation::UnicodeSegmentation;

mod segment;
pub use segment::{Segmentation, Segments, segment};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum OffsetError {
    InvalidUtf8,
    Reversed,
    OutOfBounds,
    InsideCodePoint,
    InsideSurrogatePair,
}

/// Character-boundary index. All public ranges are half-open; empty ranges are valid.
pub struct TextOffsets<'a> {
    text: &'a str,
    boundaries: Vec<(usize, usize)>,
}

impl<'a> TextOffsets<'a> {
    pub fn new(text: &'a str) -> Self {
        let mut utf16 = 0;
        let mut boundaries = Vec::with_capacity(text.chars().count() + 1);
        for (byte, character) in text.char_indices() {
            boundaries.push((byte, utf16));
            utf16 += character.len_utf16();
        }
        boundaries.push((text.len(), utf16));
        Self { text, boundaries }
    }

    pub fn from_bytes(bytes: &'a [u8]) -> Result<Self, OffsetError> {
        std::str::from_utf8(bytes)
            .map(Self::new)
            .map_err(|_| OffsetError::InvalidUtf8)
    }

    fn range(&self, range: [usize; 2], unit: usize) -> Result<Span, OffsetError> {
        if range[0] > range[1] {
            return Err(OffsetError::Reversed);
        }
        let maximum = match unit {
            0 => self.text.len(),
            1 => self.boundaries.last().expect("end boundary").1,
            _ => self.boundaries.len() - 1,
        };
        if range[1] > maximum {
            return Err(OffsetError::OutOfBounds);
        }
        let index = |value| match unit {
            0 => self
                .boundaries
                .binary_search_by_key(&value, |b| b.0)
                .map_err(|_| OffsetError::InsideCodePoint),
            1 => self
                .boundaries
                .binary_search_by_key(&value, |b| b.1)
                .map_err(|_| OffsetError::InsideSurrogatePair),
            _ => Ok(value),
        };
        let (start, end) = (index(range[0])?, index(range[1])?);
        Ok(Span {
            utf8: [self.boundaries[start].0, self.boundaries[end].0],
            utf16: [self.boundaries[start].1, self.boundaries[end].1],
            scalar: [start, end],
        })
    }

    pub fn from_utf8(&self, range: [usize; 2]) -> Result<Span, OffsetError> {
        self.range(range, 0)
    }
    pub fn from_utf16(&self, range: [usize; 2]) -> Result<Span, OffsetError> {
        self.range(range, 1)
    }
    pub fn from_scalar(&self, range: [usize; 2]) -> Result<Span, OffsetError> {
        self.range(range, 2)
    }

    pub fn is_grapheme_boundary(&self, byte: usize) -> bool {
        byte == self.text.len()
            || self
                .text
                .grapheme_indices(true)
                .any(|(start, _)| start == byte)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const CORPUS: &[&str] = &[
        "",
        "ASCII  text",
        "😀",
        "👩‍💻  👨‍👩‍👧‍👦",
        "e\u{301}  café",
        "שלום  عالم",
        "a\r\n  b\tc\u{a0}d\u{200b}e",
        "𐐀🦀中",
    ];

    #[test]
    fn every_range_and_round_trip_in_all_units() {
        for text in CORPUS {
            let offsets = TextOffsets::new(text);
            let bytes: Vec<_> = (0..=text.len())
                .filter(|i| text.is_char_boundary(*i))
                .collect();
            for (start, &s) in bytes.iter().enumerate() {
                for (end, &e) in bytes.iter().enumerate().skip(start) {
                    let expected = Span {
                        utf8: [s, e],
                        utf16: [
                            text[..s].encode_utf16().count(),
                            text[..e].encode_utf16().count(),
                        ],
                        scalar: [start, end],
                    };
                    assert_eq!(offsets.from_utf8([s, e]), Ok(expected.clone()));
                    assert_eq!(offsets.from_utf16(expected.utf16), Ok(expected.clone()));
                    assert_eq!(offsets.from_scalar([start, end]), Ok(expected));
                }
            }
            for byte in 0..=text.len() {
                assert_eq!(
                    offsets.from_utf8([byte, byte]).is_ok(),
                    text.is_char_boundary(byte)
                );
            }
            let units: Vec<_> = bytes
                .iter()
                .map(|b| text[..*b].encode_utf16().count())
                .collect();
            for unit in 0..=text.encode_utf16().count() {
                assert_eq!(
                    offsets.from_utf16([unit, unit]).is_ok(),
                    units.contains(&unit)
                );
            }
            for unit in 0..3 {
                assert_eq!(offsets.range([1, 0], unit), Err(OffsetError::Reversed));
                assert_eq!(
                    offsets.range([0, usize::MAX], unit),
                    Err(OffsetError::OutOfBounds)
                );
            }
        }
    }

    #[test]
    fn malformed_utf8_and_surrogate_boundaries_rejected() {
        for bytes in [
            &[0xff][..],
            &[0xc0, 0x80],
            &[0xf0, 0x9f],
            &[0xed, 0xa0, 0x80],
            &[0x80],
        ] {
            assert!(matches!(
                TextOffsets::from_bytes(bytes),
                Err(OffsetError::InvalidUtf8)
            ));
        }
        assert_eq!(
            TextOffsets::new("😀").from_utf16([0, 1]),
            Err(OffsetError::InsideSurrogatePair)
        );
        assert_eq!(
            TextOffsets::new("😀").from_utf8([1, 4]),
            Err(OffsetError::InsideCodePoint)
        );
    }

    #[test]
    fn graphemes_preserve_zwj_combining_and_crlf() {
        let text = "👩‍💻e\u{301}\r\n";
        let offsets = TextOffsets::new(text);
        let expected = [0, "👩‍💻".len(), "👩‍💻e\u{301}".len(), text.len()];
        for byte in 0..=text.len() + 1 {
            assert_eq!(offsets.is_grapheme_boundary(byte), expected.contains(&byte));
        }
    }
}
