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
    const schemaPath = resolve(target, 'packages/protocol/schema/suggestion.schema.json');
    await writeFile(schemaPath, canonical);
    const run = (...args) => spawnSync(process.execPath, [resolve(target, 'tools/codegen/generate.mjs'), ...args], { encoding: 'utf8' });
    assert.equal(run().status, 0);
    assert.equal(run('--check').status, 0);
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
  } finally { await rm(target, { recursive: true, force: true }); }
});
