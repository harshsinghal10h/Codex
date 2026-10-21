// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile, writeFile, mkdtemp, rm } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
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

test('real CLI analysis validates against the canonical registry and retains exact source coordinates', async () => {
  const validate = validator();
  const directory = await mkdtemp(resolve(tmpdir(), 'nw-analysis-contract-'));
  const run = (...args) => {
    const result = spawnSync('cargo', ['run', '--quiet', '--locked', '-p', 'nw-cli', '--', ...args], {
      cwd: fileURLToPath(new URL('../../', import.meta.url)), encoding: 'utf8', timeout: 180_000,
    });
    assert.equal(result.status, 0, result.error?.message ?? result.stderr);
    return JSON.parse(result.stdout);
  };
  const samples = [
    { text: '😀 Hi  café.', input_format: 'plain', tokens: fixture().tokens, suggestions: [space], protected_regions: [] },
    {
      text: '😀 Hi  café.\n\n`ignore  this`', input_format: 'markdown', tokens: fixture().tokens,
      suggestions: [space], protected_regions: [{ kind: 'code', span: { utf8: [17, 31], utf16: [14, 28], scalar: [13, 27] } }],
    },
    {
      text: '  e\u0301  שלום\r\nA  123.', input_format: 'plain', protected_regions: [],
      tokens: [
        { kind: 'word', span: { utf8: [2, 5], utf16: [2, 4], scalar: [2, 4] } },
        { kind: 'word', span: { utf8: [7, 15], utf16: [6, 10], scalar: [6, 10] } },
        { kind: 'word', span: { utf8: [17, 18], utf16: [12, 13], scalar: [12, 13] } },
        { kind: 'word', span: { utf8: [20, 23], utf16: [15, 18], scalar: [15, 18] } },
      ],
      suggestions: [
        { utf8: [0, 2], utf16: [0, 2], scalar: [0, 2] },
        { utf8: [5, 7], utf16: [4, 6], scalar: [4, 6] },
        { utf8: [18, 20], utf16: [13, 15], scalar: [13, 15] },
      ],
    },
  ];
  try {
    assert.deepEqual(run('schema', 'analysis'), analysisSchema);
    for (const [index, sample] of samples.entries()) {
      const input = resolve(directory, `sample-${index}.txt`);
      await writeFile(input, sample.text);
      const args = ['analyze', input, '--format', 'json', '--doc-version', '42', '--locale', 'en-US'];
      // Exercise both the default plain format and explicit Markdown dispatch.
      if (sample.input_format === 'markdown') args.push('--input-format', 'markdown');
      const report = run(...args);
      assert.equal(validate(report), true, JSON.stringify(validate.errors));
      assert.equal(report.doc_version, 42);
      assert.equal(report.locale, 'en-US');
      assert.equal(report.input_format, sample.input_format);
      assert.deepEqual(report.tokens, sample.tokens);
      assert.deepEqual(report.protected_regions, sample.protected_regions);
      assert.deepEqual(report.suggestions.map(suggestion => suggestion.span), sample.suggestions);
      assert.equal(report.metrics.word_count, sample.tokens.length);
      assert.equal(report.metrics.source_utf8_bytes, Buffer.byteLength(sample.text));
      assert.equal(report.metrics.source_utf16_units, sample.text.length);
      assert.equal(report.metrics.source_scalar_count, [...sample.text].length);
      const spans = [...report.prose_regions, ...report.sentences, ...report.paragraphs,
        ...report.tokens.map(token => token.span), ...report.protected_regions.map(region => region.span),
        ...report.suggestions.map(suggestion => suggestion.span)];
      for (const span of spans) {
        const bytes = Buffer.from(sample.text).subarray(...span.utf8).toString('utf8');
        assert.equal(bytes, sample.text.slice(...span.utf16));
        assert.equal(bytes, [...sample.text].slice(...span.scalar).join(''));
      }
      for (const suggestion of report.suggestions) {
        assert.equal(sample.text.slice(...suggestion.span.utf16), suggestion.original);
        assert.equal(suggestion.doc_version, report.doc_version);
      }
      if (sample.input_format === 'markdown') {
        assert.deepEqual(report.prose_regions, [whole]);
        assert.equal(report.metrics.analyzed_scalar_count, 11);
        assert.ok(report.metrics.warnings.includes('incomplete_readability_projection'));
      }
    }
  } finally { await rm(directory, { recursive: true, force: true }); }
});
