// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import Ajv2020 from 'ajv/dist/2020.js';

const suggestionSchema = JSON.parse(await readFile(new URL('./schema/suggestion.schema.json', import.meta.url), 'utf8'));
const analysisSchema = JSON.parse(await readFile(new URL('./schema/analysis.schema.json', import.meta.url), 'utf8'));
function validator(suggestion = suggestionSchema) {
  const ajv = new Ajv2020({ allErrors: true, strict: true, strictNumbers: true });
  ajv.addKeyword({ keyword: 'x-spdx', schemaType: 'string' });
  ajv.addSchema(suggestion);
  return ajv.compile(analysisSchema);
}
const whole = { utf8: [0, 15], utf16: [0, 12], scalar: [0, 11] };
const space = { utf8: [7, 9], utf16: [5, 7], scalar: [4, 6] };
// Handwritten contract fixture for “😀 Hi  café.”, independent of the engine.
function fixture() {
  return {
    doc_version: 42, locale: 'en-US', input_format: 'plain',
    prose_regions: [whole], protected_regions: [],
    tokens: [
      { span: { utf8: [5, 7], utf16: [3, 5], scalar: [2, 4] }, kind: 'word' },
      { span: { utf8: [9, 14], utf16: [7, 11], scalar: [6, 10] }, kind: 'word' },
    ],
    sentences: [whole], paragraphs: [whole],
    metrics: {
      source_utf8_bytes: 15, source_utf16_units: 12, source_scalar_count: 11,
      source_grapheme_count: 11, analyzed_scalar_count: 11, word_count: 2,
      sentence_count: 1, paragraph_count: 1, letter_count: 6, digit_count: 0,
      unique_word_count: 2, syllable_covered_words: 1, estimated_syllables: 1,
      estimated_polysyllable_words: 0, reading_seconds: 120 / 238,
      speaking_seconds: 120 / 150, scores: [{ kind: 'type_token_ratio', value: 1 }],
      warnings: ['short_readability_sample', 'non_ascii_words'],
    },
    suggestions: [{
      id: 'double-space-7', rule_id: 'NX-TYP-001', category: 'correctness',
      subcategory: 'typography', severity: 1, span: space, original: '  ',
      replacements: [{ text: ' ', confidence: 1, kind: 'replace' }],
      explanation: { short: 'Use one space.', long: 'Replace the repeated space.', examples: [] },
      flags: { autofix_safe: true, needs_context: false, style_guide: false, user_rule: false },
      source: 'rule', doc_version: 42, locale: 'en-US', goals_applied: [],
      provenance: 'own', telemetry_class: 'none',
    }],
  };
}

test('analysis schema validates fixtures through canonical Suggestion and Span references', () => {
  const validate = validator();
  const sample = fixture();
  assert.equal(validate(sample), true, JSON.stringify(validate.errors));
  const signedScore = structuredClone(sample);
  signedScore.metrics.scores = [{ kind: 'automated_readability_index', value: -2.771 }];
  assert.equal(validate(signedScore), true, JSON.stringify(validate.errors));
  const empty = structuredClone(sample);
  empty.prose_regions = []; empty.tokens = []; empty.sentences = []; empty.paragraphs = []; empty.suggestions = [];
  for (const [key, value] of Object.entries(empty.metrics)) empty.metrics[key] = typeof value === 'number' ? 0 : [];
  empty.metrics.warnings = ['no_words'];
  assert.equal(validate(empty), true, JSON.stringify(validate.errors));
  const markdown = structuredClone(sample);
  markdown.input_format = 'markdown';
  for (const kind of analysisSchema.$defs.ProtectionKind.enum) {
    markdown.protected_regions = [{ span: whole, kind }];
    assert.equal(validate(markdown), true, JSON.stringify(validate.errors));
  }
  const changedSuggestion = structuredClone(suggestionSchema);
  changedSuggestion.$defs.OffsetRange.prefixItems.forEach(item => { item.maximum = 1; });
  assert.equal(validator(changedSuggestion)(sample), false, 'Span must resolve to the registered canonical definition');
  const changedRoot = structuredClone(suggestionSchema);
  changedRoot.properties.id.minLength = 100;
  assert.equal(validator(changedRoot)(sample), false, 'Suggestion must resolve to the registered canonical root');
});

test('analysis schema rejects missing, extra, invalid, nullable and nonfinite wire values', () => {
  const validate = validator();
  const corruptions = [
    ...analysisSchema.required.map(key => value => { delete value[key]; }),
    ...analysisSchema.$defs.TextMetrics.required.map(key => value => { delete value.metrics[key]; }),
    value => { value.extra = true; }, value => { value.metrics.extra = true; },
    value => { value.tokens[0].text = 'Hi'; }, value => { value.tokens[0].kind = 'number'; },
    value => { delete value.tokens[0].span; }, value => { value.tokens[0].span.extra = true; },
    value => { value.tokens[0].span.utf8 = [0, 1, 2]; },
    value => { value.tokens[0].span.utf16 = [-1, 1]; },
    value => { value.tokens[0].span.scalar = [0, 4294967296]; },
    value => { value.protected_regions = [{ span: whole, kind: 'unknown' }]; },
    value => { value.protected_regions = [{ span: whole }]; },
    value => { value.input_format = 'html'; }, value => { value.locale = 'en-GB'; },
    value => { value.doc_version = -1; }, value => { value.doc_version = 0.1; },
    value => { value.doc_version = 9007199254740992; },
    value => { value.metrics.word_count = -1; }, value => { value.metrics.letter_count = 0.1; },
    value => { value.metrics.source_utf8_bytes = 4294967296; },
    value => { value.metrics.reading_seconds = -0.1; }, value => { value.metrics.speaking_seconds = null; },
    value => { value.metrics.warnings = [1]; }, value => { value.metrics.scores = null; },
    value => { value.metrics.scores[0].kind = 'unsupported'; },
    value => { value.metrics.scores[0].value = null; },
    value => { value.metrics.scores[0].extra = true; },
    value => { value.metrics.scores[0].value = NaN; },
    value => { value.metrics.scores[0].value = Infinity; },
    value => { value.metrics.scores[0].value = -Infinity; },
    value => { value.metrics.reading_seconds = Infinity; },
    value => { value.suggestions[0].severity = 4; },
    value => { value.suggestions[0].replacements[0].confidence = 1.1; },
    value => { value.suggestions[0].extra = true; },
  ];
  for (const corrupt of corruptions) {
    const malformed = structuredClone(fixture()); corrupt(malformed);
    assert.equal(validate(malformed), false, 'schema accepted malformed analysis');
  }
});
