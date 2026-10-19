// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
//! Bootstrap DSL subset: a single regex token, literal replacements, and regex antipatterns.
use nw_schema::{Category, Severity};
use regex::Regex;
use serde::Deserialize;

pub const BUNDLED_RULE: &str = include_str!("../../../rules/en-US/correctness/double-space.yaml");

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Definition {
    pub id: String,
    pub locale: String,
    pub category: Category,
    pub subcategory: String,
    pub severity: Severity,
    pub pattern: Vec<Pattern>,
    pub antipatterns: Vec<String>,
    pub message: Message,
    pub suggestions: Vec<String>,
    pub examples: Examples,
    pub tags: Vec<String>,
    pub provenance: String,
}
#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Pattern {
    pub regex: String,
}
#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Message {
    pub short: String,
    pub long: String,
}
#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Examples {
    pub positive: Vec<Positive>,
    pub negative: Vec<String>,
}
#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Positive {
    pub text: String,
    pub result: String,
}

pub struct Rule {
    pub definition: Definition,
    matcher: Regex,
    exclusions: Vec<Regex>,
}
impl Rule {
    pub fn compile(yaml: &str) -> Result<Self, String> {
        let definition: Definition =
            serde_saphyr::from_str(yaml).map_err(|e| format!("invalid rule YAML: {e}"))?;
        if definition.pattern.len() != 1
            || definition.suggestions.is_empty()
            || definition.id.is_empty()
            || definition.subcategory.is_empty()
            || definition.locale != "en-US"
            || definition.provenance != "own"
            || definition.examples.positive.len() < 3
            || definition.examples.negative.len() < 3
        {
            return Err("unsupported or incomplete bootstrap rule".into());
        }
        let matcher = Regex::new(&definition.pattern[0].regex).map_err(|e| e.to_string())?;
        if matcher.is_match("") {
            return Err("rule must not match empty text".into());
        }
        let exclusions = definition
            .antipatterns
            .iter()
            .map(|p| Regex::new(p))
            .collect::<Result<Vec<_>, _>>()
            .map_err(|e| e.to_string())?;
        Ok(Self {
            definition,
            matcher,
            exclusions,
        })
    }

    pub fn ranges(&self, text: &str) -> Vec<[usize; 2]> {
        self.matcher
            .find_iter(text)
            .filter(|m| !m.is_empty())
            .filter(|m| {
                !self.exclusions.iter().any(|r| {
                    r.find_iter(text)
                        .any(|x| x.start() < m.end() && m.start() < x.end())
                })
            })
            .map(|m| [m.start(), m.end()])
            .collect()
    }

    pub fn test_examples(&self) -> Result<usize, String> {
        for case in &self.definition.examples.positive {
            let ranges = self.ranges(&case.text);
            if ranges.is_empty() {
                return Err("positive example has no match".into());
            }
            let mut result = case.text.clone();
            for [start, end] in ranges.into_iter().rev() {
                result.replace_range(start..end, &self.definition.suggestions[0]);
            }
            if result != case.result {
                return Err("positive example replacement differs".into());
            }
        }
        if self
            .definition
            .examples
            .negative
            .iter()
            .any(|text| !self.ranges(text).is_empty())
        {
            return Err("negative example matched".into());
        }
        Ok(self.definition.examples.positive.len() + self.definition.examples.negative.len())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn examples_compile_and_run() {
        assert_eq!(Rule::compile(BUNDLED_RULE).unwrap().test_examples(), Ok(9));
    }
    #[test]
    fn rejects_malformed_and_unsupported_rules() {
        for yaml in [
            "{",
            "id: only",
            &BUNDLED_RULE.replace(" {2,}", "["),
            &BUNDLED_RULE.replace(" {2,}", ".*"),
            &BUNDLED_RULE.replace("severity: 1", "severity: 4"),
            &format!("{BUNDLED_RULE}\nunknown: true\n"),
        ] {
            assert!(Rule::compile(yaml).is_err());
        }
    }
    #[test]
    fn antipattern_excludes_overlapping_region() {
        let rule = Rule::compile(
            &BUNDLED_RULE.replace("antipatterns: []", "antipatterns: ['keep  this']"),
        )
        .unwrap();
        assert_eq!(rule.ranges("keep  this  okay"), vec![[10, 12]]);
    }
}
