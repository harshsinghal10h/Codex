// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
//! Wire types generated from the canonical JSON Schema. Structural deserialization
//! does not replace JSON Schema validation of bounds and string patterns.
mod generated;
pub use generated::*;

/// The canonical schema, embedded without generating another schema from Rust.
pub const SCHEMA: &str = include_str!("../../../packages/protocol/schema/suggestion.schema.json");

/// Canonical analysis contract. References resolve against [`SCHEMA`].
pub const ANALYSIS_SCHEMA: &str =
    include_str!("../../../packages/protocol/schema/analysis.schema.json");

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn severity_is_a_bounded_wire_number() {
        for (value, expected) in [(1, Severity::One), (2, Severity::Two), (3, Severity::Three)] {
            assert_eq!(
                serde_json::from_str::<Severity>(&value.to_string()).unwrap(),
                expected
            );
            assert_eq!(serde_json::to_string(&expected).unwrap(), value.to_string());
        }
        for invalid in ["0", "4", "-1", "1.1", "\"1\"", "null"] {
            assert!(serde_json::from_str::<Severity>(invalid).is_err());
        }
    }

    #[test]
    fn offsets_have_exactly_two_unsigned_values() {
        for invalid in [
            r#"{"utf8":[0],"utf16":[0,1],"scalar":[0,1]}"#,
            r#"{"utf8":[0,1,2],"utf16":[0,1],"scalar":[0,1]}"#,
            r#"{"utf8":[-1,1],"utf16":[0,1],"scalar":[0,1]}"#,
        ] {
            assert!(serde_json::from_str::<Span>(invalid).is_err());
        }
        assert!(serde_json::from_str::<serde_json::Value>(SCHEMA).is_ok());
    }
}
