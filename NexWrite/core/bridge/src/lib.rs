// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
//! Strict UTF-8 JNI transport for the canonical, local Rust analysis engine.
//! This adapter performs no networking, storage, normalization, or logging.

use std::{fmt, ptr};

use jni::{
    JNIEnv,
    objects::{JByteArray, JClass},
    sys::{jbyteArray, jlong},
};

pub const MAX_DOCUMENT_VERSION: i64 = 9_007_199_254_740_991;

/// Errors contain only fixed categories, never document or provider content.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum BridgeError {
    InputTooLarge,
    InvalidUtf8,
    InvalidDocumentVersion,
    UnsupportedLocale,
    UnsupportedInputFormat,
    NullInput,
    EngineUnavailable,
    SerializationFailed,
    NativeBoundaryFailed,
}

impl fmt::Display for BridgeError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(match self {
            Self::InputTooLarge => "analysis input exceeds 1048576 UTF-8 byte limit",
            Self::InvalidUtf8 => "analysis arguments must contain valid UTF-8",
            Self::InvalidDocumentVersion => "document version must be a nonnegative safe integer",
            Self::UnsupportedLocale => "analysis supports en-US only",
            Self::UnsupportedInputFormat => "input format must be plain or markdown",
            Self::NullInput => "analysis arguments must not be null",
            Self::EngineUnavailable => "local analysis engine is unavailable",
            Self::SerializationFailed => "local analysis result could not be encoded",
            Self::NativeBoundaryFailed => "local analysis native boundary failed",
        })
    }
}

impl std::error::Error for BridgeError {}

impl BridgeError {
    fn exception_class(self) -> &'static str {
        match self {
            Self::EngineUnavailable | Self::SerializationFailed | Self::NativeBoundaryFailed => {
                "java/lang/IllegalStateException"
            }
            _ => "java/lang/IllegalArgumentException",
        }
    }
}

/// Analyze unchanged source and return canonical `AnalysisResult` JSON bytes.
///
/// The byte boundary deliberately avoids Java's modified UTF-8/CESU-8 strings.
/// JVM callers must reject unpaired UTF-16 surrogates before encoding source.
pub fn analyze_utf8(
    text: &[u8],
    doc_version: i64,
    locale: &[u8],
    input_format: &[u8],
) -> Result<Vec<u8>, BridgeError> {
    if text.len() > nw_engine::MAX_ANALYSIS_UTF8_BYTES {
        return Err(BridgeError::InputTooLarge);
    }
    validate_version(doc_version)?;
    let text = std::str::from_utf8(text).map_err(|_| BridgeError::InvalidUtf8)?;
    let locale = std::str::from_utf8(locale).map_err(|_| BridgeError::InvalidUtf8)?;
    let input_format = std::str::from_utf8(input_format).map_err(|_| BridgeError::InvalidUtf8)?;
    if locale != "en-US" {
        return Err(BridgeError::UnsupportedLocale);
    }
    let input_format = match input_format {
        "plain" => nw_schema::InputFormat::Plain,
        "markdown" => nw_schema::InputFormat::Markdown,
        _ => return Err(BridgeError::UnsupportedInputFormat),
    };
    let engine = nw_engine::Engine::new().map_err(|_| BridgeError::EngineUnavailable)?;
    let report = engine
        .analyze(text, doc_version as u64, locale, input_format)
        .map_err(|_| BridgeError::EngineUnavailable)?;
    serde_json::to_vec(&report).map_err(|_| BridgeError::SerializationFailed)
}

fn validate_version(doc_version: i64) -> Result<(), BridgeError> {
    if !(0..=MAX_DOCUMENT_VERSION).contains(&doc_version) {
        return Err(BridgeError::InvalidDocumentVersion);
    }
    Ok(())
}

fn read_array(
    env: &JNIEnv<'_>,
    value: &JByteArray<'_>,
    maximum: usize,
    limit_error: BridgeError,
) -> Result<Vec<u8>, BridgeError> {
    if value.is_null() {
        return Err(BridgeError::NullInput);
    }
    let length = env
        .get_array_length(value)
        .map_err(|_| BridgeError::NativeBoundaryFailed)?;
    if length < 0 || length as usize > maximum {
        return Err(limit_error);
    }
    env.convert_byte_array(value)
        .map_err(|_| BridgeError::NativeBoundaryFailed)
}

fn invoke<'local>(
    env: &JNIEnv<'local>,
    text: &JByteArray<'local>,
    doc_version: jlong,
    locale: &JByteArray<'local>,
    input_format: &JByteArray<'local>,
) -> Result<jbyteArray, BridgeError> {
    validate_version(doc_version)?;
    // Reject null references first and bound each array before copying it.
    for value in [text, locale, input_format] {
        if value.is_null() {
            return Err(BridgeError::NullInput);
        }
    }
    let text = read_array(
        env,
        text,
        nw_engine::MAX_ANALYSIS_UTF8_BYTES,
        BridgeError::InputTooLarge,
    )?;
    let locale = read_array(env, locale, 5, BridgeError::UnsupportedLocale)?;
    let input_format = read_array(env, input_format, 8, BridgeError::UnsupportedInputFormat)?;
    let output = analyze_utf8(&text, doc_version, &locale, &input_format)?;
    // JNI array lengths are signed 32-bit, even on a 64-bit Android process.
    i32::try_from(output.len()).map_err(|_| BridgeError::SerializationFailed)?;
    env.byte_array_from_slice(&output)
        .map(JByteArray::into_raw)
        .map_err(|_| BridgeError::NativeBoundaryFailed)
}

/// Entry point for `app.nexwrite.core.NativeBridge.analyzeUtf8`.
///
/// The JVM supplies valid JNI references for this exact static method signature.
/// Null arrays, malformed UTF-8, settings and portable-version violations become
/// content-free Java exceptions. A pending JVM exception (including allocation
/// failure) is retained rather than replaced.
#[unsafe(no_mangle)]
pub extern "system" fn Java_app_nexwrite_core_NativeBridge_analyzeUtf8<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    text: JByteArray<'local>,
    doc_version: jlong,
    locale: JByteArray<'local>,
    input_format: JByteArray<'local>,
) -> jbyteArray {
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        invoke(&env, &text, doc_version, &locale, &input_format)
    }))
    .unwrap_or(Err(BridgeError::NativeBoundaryFailed));
    match result {
        Ok(output) => output,
        Err(error) => {
            if let Ok(false) = env.exception_check() {
                let _ = env.throw_new(error.exception_class(), error.to_string());
            }
            ptr::null_mut()
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use nw_schema::InputFormat;

    fn report(text: &str, version: i64, format: &str) -> serde_json::Value {
        serde_json::from_slice(
            &analyze_utf8(text.as_bytes(), version, b"en-US", format.as_bytes()).unwrap(),
        )
        .unwrap()
    }

    #[test]
    fn native_transport_matches_actual_engine_for_unicode_and_markdown() {
        for (text, format, wire_format) in [
            ("", InputFormat::Plain, "plain"),
            (
                "😀 café e\u{301} אבג\r\nIt is  clear.",
                InputFormat::Plain,
                "plain",
            ),
            (
                "Before `a  b` and **clear**  prose.\n\n```text\na  b\n```\n[a  b](https://example.test/a)",
                InputFormat::Markdown,
                "markdown",
            ),
            ("NUL\0\r\nvery", InputFormat::Plain, "plain"),
        ] {
            let actual = analyze_utf8(
                text.as_bytes(),
                MAX_DOCUMENT_VERSION,
                b"en-US",
                wire_format.as_bytes(),
            )
            .unwrap();
            let expected = nw_engine::Engine::new()
                .unwrap()
                .analyze(text, MAX_DOCUMENT_VERSION as u64, "en-US", format)
                .unwrap();
            // Compare the actual JSON transport bytes, including its floating
            // point spelling, to the direct engine's identical serializer.
            assert_eq!(actual, serde_json::to_vec(&expected).unwrap());
            let actual: serde_json::Value = serde_json::from_slice(&actual).unwrap();
            assert_eq!(actual["doc_version"], MAX_DOCUMENT_VERSION);
        }
    }

    #[test]
    fn markdown_protection_and_original_unicode_offsets_are_retained() {
        let text = "😀 e\u{301} `a  b` clear  words";
        let actual = report(text, 7, "markdown");
        let suggestions = actual["suggestions"].as_array().unwrap();
        assert_eq!(suggestions.len(), 1);
        let start = text.rfind("  ").unwrap();
        let expected = serde_json::json!({
            "utf8": [start, start + 2],
            "utf16": [text[..start].encode_utf16().count(), text[..start].encode_utf16().count() + 2],
            "scalar": [text[..start].chars().count(), text[..start].chars().count() + 2],
        });
        assert_eq!(suggestions[0]["span"], expected);
        assert_eq!(suggestions[0]["rule_id"], "NW-SPACE-001");
        assert_eq!(suggestions[0]["original"], "  ");
        assert!(!actual["protected_regions"].as_array().unwrap().is_empty());
    }

    #[test]
    fn malformed_utf8_never_uses_lossy_decoding() {
        for bytes in [
            &[0xff][..],
            &[0xc0, 0xaf],
            &[0xed, 0xa0, 0x80],
            &[0xf0, 0x9f, 0x98],
        ] {
            assert_eq!(
                analyze_utf8(bytes, 0, b"en-US", b"plain"),
                Err(BridgeError::InvalidUtf8)
            );
            assert_eq!(
                analyze_utf8(b"", 0, bytes, b"plain"),
                Err(BridgeError::InvalidUtf8)
            );
            assert_eq!(
                analyze_utf8(b"", 0, b"en-US", bytes),
                Err(BridgeError::InvalidUtf8)
            );
        }
    }

    #[test]
    fn portable_version_and_supported_settings_are_strict() {
        for version in [-1, MAX_DOCUMENT_VERSION + 1, i64::MAX] {
            assert_eq!(
                analyze_utf8(b"", version, b"en-US", b"plain"),
                Err(BridgeError::InvalidDocumentVersion)
            );
        }
        for locale in [b"en".as_slice(), b"EN-us", b"en-US\0", b"ar"] {
            assert_eq!(
                analyze_utf8(b"", 0, locale, b"plain"),
                Err(BridgeError::UnsupportedLocale)
            );
        }
        for format in [b"".as_slice(), b"Plain", b"html", b"plain\0"] {
            assert_eq!(
                analyze_utf8(b"", 0, b"en-US", format),
                Err(BridgeError::UnsupportedInputFormat)
            );
        }
    }

    #[test]
    fn exact_core_byte_cap_is_preserved() {
        // Whitespace exercises the exact cap without manufacturing a huge report.
        let at_limit = vec![b' '; nw_engine::MAX_ANALYSIS_UTF8_BYTES];
        assert!(analyze_utf8(&at_limit, 0, b"en-US", b"plain").is_ok());
        let over_limit = vec![b' '; nw_engine::MAX_ANALYSIS_UTF8_BYTES + 1];
        assert_eq!(
            analyze_utf8(&over_limit, 0, b"en-US", b"plain"),
            Err(BridgeError::InputTooLarge)
        );
        let multibyte = "😀".repeat(nw_engine::MAX_ANALYSIS_UTF8_BYTES / 4);
        assert_eq!(multibyte.len(), nw_engine::MAX_ANALYSIS_UTF8_BYTES);
        let over_limit = format!("{multibyte}x");
        assert_eq!(
            analyze_utf8(over_limit.as_bytes(), 0, b"en-US", b"plain"),
            Err(BridgeError::InputTooLarge)
        );
    }

    #[test]
    fn errors_cannot_echo_untrusted_values() {
        let secret_marker = b"private-source-should-never-appear";
        let error = analyze_utf8(b"", 0, secret_marker, b"plain").unwrap_err();
        assert_eq!(error.to_string(), "analysis supports en-US only");
        assert_eq!(
            error.exception_class(),
            "java/lang/IllegalArgumentException"
        );
        assert_eq!(
            BridgeError::NativeBoundaryFailed.exception_class(),
            "java/lang/IllegalStateException"
        );
    }
}
