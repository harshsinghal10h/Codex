// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { buildOutputs, validateContract, validationId } from '../api-codegen.mjs';

const document = JSON.parse(await readFile(new URL('../../docs/api/owned-app.openapi.json', import.meta.url), 'utf8'));
const outputs = buildOutputs(document);
const runtime = JSON.parse(outputs['validation.schema.json']);
const require = createRequire(new URL('../../packages/protocol/package.json', import.meta.url));
const Ajv2020 = require('ajv/dist/2020.js').default;
const validator = new Ajv2020({ strict: true, allErrors: true, unicodeRegExp: true });
validator.addKeyword({ keyword: 'x-spdx', schemaType: 'string', valid: true });
validator.addKeyword({ keyword: 'x-unicode-scalar-string', schemaType: 'boolean', type: 'string', validate: (enabled, value) => !enabled || value.isWellFormed() });
validator.addKeyword({ keyword: 'x-utf8-max-bytes', schemaType: 'number', type: 'string', validate: (limit, value) => Buffer.byteLength(value, 'utf8') <= limit });
for (const name of ['suggestion', 'analysis']) validator.addSchema(JSON.parse(await readFile(new URL(`../../packages/protocol/schema/${name}.schema.json`, import.meta.url), 'utf8')));
validator.addSchema(runtime);
const validate = name => validator.compile({ $ref: `${validationId}#/$defs/${name}` });
const analyze = { text: 'Hi  😀!', doc_version: 4, locale: 'en-US', input_format: 'plain' };
const rewrite = { text: 'A writing brief.', doc_version: 7, locale: 'en-US', task: 'rewrite', preset: 'clearer', provider: 'openai', model: 'gpt-4.1-mini', cloud_consent: true };

test('API output generation is deterministic and preserves optional problem field', () => {
  assert.deepEqual(buildOutputs(structuredClone(document)), outputs);
  assert.match(outputs['owned-api.ts'], /import type \{ AnalysisResult, InputFormat \} from '@nexwrite\/protocol'/);
  assert.match(outputs['owned-api.ts'], /retry_after_seconds\?: number/);
  assert.match(outputs['owned-api.kt'], /retry_after_seconds: Long\? = null/);
  assert.match(outputs['owned-api.swift'], /retry_after_seconds: UInt64\? = nil/);
  assert.match(outputs['owned-api.kt'], /@SerialName\("en-US"\) EN_US/);
  assert.match(outputs['owned-api.swift'], /case enUS = "en-US"/);
});

test('API runtime registry reuses canonical input format without duplicating analysis', () => {
  assert.equal(runtime.$id, validationId);
  assert.equal(runtime.$defs.AnalyzeRequest.properties.input_format.$ref, 'https://nexwrite.local/schema/analysis.schema.json#/$defs/InputFormat');
  assert.equal(Object.hasOwn(runtime.$defs, 'AnalysisResult'), false);
  assert.equal(Object.hasOwn(runtime.$defs, 'Suggestion'), false);
  assert.equal(validate('AnalyzeRequest')(analyze), true);
});

test('unknown and unsafe references/schema shapes fail closed without a resolver', () => {
  for (const ref of ['https://example.com/evil.schema.json', '../../../outside.json', '#/components/schemas/__proto__', '#/components/schemas/Missing']) {
    const copy = structuredClone(document);
    copy.components.schemas.AnalyzeRequest.properties.input_format.$ref = ref;
    assert.throws(() => buildOutputs(copy), /unregistered reference/);
  }
  const copy = structuredClone(document);
  copy.components.schemas.AnalyzeRequest.properties.text.anyOf = [];
  assert.throws(() => validateContract(copy), /text.anyOf/);
  copy.components.schemas.AnalyzeRequest.properties.text = { type: 'string' };
  assert.throws(() => validateContract(copy), /must reject lone surrogates/);
});

test('all API object schemas reject unknown fields and invalid document versions', () => {
  const valid = validate('AnalyzeRequest');
  assert.equal(valid({ ...analyze, extra: true }), false);
  for (const doc_version of [-1, 0.5, Number.NaN, Number.POSITIVE_INFINITY, 9007199254740992, '1']) assert.equal(valid({ ...analyze, doc_version }), false);
  assert.equal(valid({ ...analyze, doc_version: 9007199254740991 }), true);
  assert.equal(valid({ ...analyze, locale: 'fr-FR' }), false);
  assert.equal(valid({ ...analyze, input_format: 'html' }), false);
});

test('byte bounds distinguish Unicode byte length from scalar length', () => {
  const valid = validate('RewriteRequest');
  assert.equal(valid({ ...rewrite, text: '😀'.repeat(8192) }), true);
  assert.equal(valid({ ...rewrite, text: '😀'.repeat(8193) }), false);
  assert.equal(valid({ ...rewrite, text: 'a'.repeat(32768) }), true);
  assert.equal(valid({ ...rewrite, text: 'a'.repeat(32769) }), false);
  assert.equal(valid({ ...rewrite, text: '\ud800' }), false);
  assert.equal(valid({ ...rewrite, text: '\udc00' }), false);
});

test('cloud consent, nonblank input and task/preset combinations are enforced', () => {
  const valid = validate('RewriteRequest');
  assert.equal(valid(rewrite), true);
  assert.equal(valid({ ...rewrite, cloud_consent: false }), false);
  assert.equal(valid({ ...rewrite, cloud_consent: 'true' }), false);
  assert.equal(valid({ ...rewrite, text: ' \n\t' }), false);
  for (const task of ['summarize', 'compose']) {
    assert.equal(valid({ ...rewrite, task, preset: 'clearer' }), true);
    assert.equal(valid({ ...rewrite, task, preset: 'formal' }), false);
  }
  assert.equal(valid({ ...rewrite, provider: 'https://evil.invalid' }), false);
});

test('AI results are bounded closed plain-string proposals with exact context fields', () => {
  const valid = validate('AiResult');
  const result = { request_id: '6f0a4951-b196-4de9-ae14-2f0089165baf', doc_version: 7, provider: 'openai', model: 'gpt-4.1-mini', task: 'rewrite', preset: 'clearer', original: rewrite.text, replacement: 'Proposed words.', warnings: [] };
  assert.equal(valid(result), true);
  assert.equal(valid({ ...result, replacement: '😀'.repeat(16384) }), true);
  assert.equal(valid({ ...result, replacement: '😀'.repeat(16385) }), false);
  assert.equal(valid({ ...result, replacement: { text: 'tool call' } }), false);
  assert.equal(valid({ ...result, tools: [] }), false);
  assert.equal(valid({ ...result, warnings: ['\ud800'] }), false);
  assert.equal(valid({ ...result, request_id: 'not-a-uuid' }), false);
});

test('capabilities cannot pretend cloud documents/accounts or extra locales are available', () => {
  const valid = validate('Capabilities');
  const capabilities = { protocol_version: 1, version: '0.2.0-preview.1', engine: { available: true, rule_count: 1, locales: ['en-US'], input_formats: ['plain', 'markdown'] }, tasks: ['rewrite', 'summarize', 'compose'], providers: [{ id: 'openai', label: 'OpenAI compatible', configured: false, models: [] }], accounts: false, cloud_docs: false, auth_required: false };
  assert.equal(valid(capabilities), true);
  assert.equal(valid({ ...capabilities, accounts: true }), false);
  assert.equal(valid({ ...capabilities, cloud_docs: true }), false);
  assert.equal(valid({ ...capabilities, engine: { ...capabilities.engine, locales: ['en-US', 'fr-FR'] } }), false);
});

test('problem metadata has stable status and optional retry delay without content leaks', () => {
  const valid = validate('ApiProblem');
  const problem = { type: 'about:blank', title: 'Request unavailable', status: 503, code: 'PROVIDER_UNAVAILABLE', request_id: '6f0a4951-b196-4de9-ae14-2f0089165baf' };
  assert.equal(valid(problem), true);
  assert.equal(valid({ ...problem, retry_after_seconds: 12 }), true);
  assert.equal(valid({ ...problem, retry_after_seconds: -1 }), false);
  assert.equal(valid({ ...problem, source: 'private writing' }), false);
  assert.equal(valid({ ...problem, status: 200 }), false);
});
