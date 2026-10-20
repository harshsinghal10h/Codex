// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, readFile, writeFile, cp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

test('schema generator detects drift and rejects unsupported or ambiguous contracts', async () => {
  const target = await mkdtemp(resolve(tmpdir(), 'nw-codegen-'));
  try {
    await mkdir(resolve(target, 'tools/codegen'), { recursive: true });
    await mkdir(resolve(target, 'packages/protocol/schema'), { recursive: true });
    const origin = new URL('./', import.meta.url);
    await cp(new URL('generate.mjs', origin), resolve(target, 'tools/codegen/generate.mjs'));
    const canonical = await readFile(new URL('../../packages/protocol/schema/suggestion.schema.json', origin), 'utf8');
    const analysisCanonical = await readFile(new URL('../../packages/protocol/schema/analysis.schema.json', origin), 'utf8');
    const schemaPath = resolve(target, 'packages/protocol/schema/suggestion.schema.json');
    const analysisPath = resolve(target, 'packages/protocol/schema/analysis.schema.json');
    await writeFile(schemaPath, canonical);
    await writeFile(analysisPath, analysisCanonical);
    const run = (...args) => spawnSync(process.execPath, [resolve(target, 'tools/codegen/generate.mjs'), ...args], { encoding: 'utf8' });
    assert.equal(run().status, 0);
    assert.equal(run('--check').status, 0);
    const generated = await readFile(resolve(target, 'packages/protocol/generated/suggestion.ts'), 'utf8');
    assert.equal((generated.match(/export interface Span \{/g) ?? []).length, 1);
    assert.equal((generated.match(/export interface Suggestion \{/g) ?? []).length, 1);
    assert.match(generated, /prose_regions: Span\[\];/);
    assert.match(generated, /suggestions: Suggestion\[\];/);
    assert.match(generated, /export interface AnalysisResult \{/);
    await writeFile(resolve(target, 'packages/protocol/generated/suggestion.ts'), '// changed\n');
    assert.equal(run('--check').status, 1);
    for (const change of [
      schema => { schema.properties.original.type = ['string', 'null']; },
      schema => { schema.required.pop(); },
      schema => { schema.properties.original.format = 'email'; },
      schema => { schema.properties.span.$ref = '#/$defs/Missing'; },
      schema => { schema.$defs.OffsetRange.maxItems = 3; },
    ]) {
      const schema = JSON.parse(canonical); change(schema);
      await writeFile(schemaPath, JSON.stringify(schema));
      const result = run();
      assert.equal(result.status, 1);
      assert.match(result.stderr, /Unsupported schema:/);
    }
    await writeFile(schemaPath, canonical);
    for (const change of [
      schema => { schema.properties.suggestions.items.$ref = 'https://unregistered.example/schema.json'; },
      schema => { schema.properties.prose_regions.items.$ref = 'suggestion.schema.json#/$defs/Missing'; },
      schema => { schema.properties.prose_regions.items.type = 'string'; },
      schema => { schema.$defs.Span = { type: 'string' }; },
      schema => { schema.$id = 'https://nexwrite.local/schema/unregistered.schema.json'; },
      schema => { schema.title = 'Suggestion'; },
      schema => { schema.$defs.MetricScore.properties.value = { $ref: 'analysis.schema.json' }; },
      schema => { schema.$defs.Token.$id = 'https://unregistered.example/token'; },
      schema => { schema.$defs.TextMetrics.properties.warnings.items.type = ['string', 'null']; },
    ]) {
      const schema = JSON.parse(analysisCanonical); change(schema);
      await writeFile(analysisPath, JSON.stringify(schema));
      const result = run();
      assert.equal(result.status, 1);
      assert.match(result.stderr, /Unsupported schema:/);
    }
    await writeFile(analysisPath, analysisCanonical);
    assert.equal(run().status, 0);
    assert.equal(run('--check').status, 0);
  } finally { await rm(target, { recursive: true, force: true }); }
});
