// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile, writeFile, mkdtemp, rm } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import Ajv2020 from 'ajv/dist/2020.js';

test('real CLI output satisfies canonical schema; malformed wire suggestions fail', async () => {
  const schema = JSON.parse(await readFile(new URL('./schema/suggestion.schema.json', import.meta.url), 'utf8'));
  const ajv = new Ajv2020({ allErrors: true, strict: true });
  ajv.addKeyword({ keyword: 'x-spdx', schemaType: 'string' });
  const validate = ajv.compile(schema);
  const directory = await mkdtemp(resolve(tmpdir(), 'nw-schema-contract-'));
  try {
    const input = resolve(directory, 'sample.txt');
    await writeFile(input, '😀  e\u0301  שלום');
    const result = spawnSync('cargo', ['run', '--quiet', '--locked', '-p', 'nw-cli', '--', 'check', input, '--format', 'json', '--doc-version', '42'], {
      cwd: fileURLToPath(new URL('../../', import.meta.url)), encoding: 'utf8', timeout: 180_000,
    });
    assert.equal(result.status, 0, result.error?.message ?? result.stderr);
    const suggestions = JSON.parse(result.stdout);
    assert.equal(suggestions.length, 2);
    for (const suggestion of suggestions) assert.equal(validate(suggestion), true, ajv.errorsText(validate.errors));
    const sample = suggestions[0];
    const corruptions = [
      ...schema.required.map(key => suggestion => { delete suggestion[key]; }),
      suggestion => { suggestion.extra = true; },
      suggestion => { suggestion.flags.extra = true; },
      suggestion => { suggestion.severity = 0; }, suggestion => { suggestion.severity = 4; },
      suggestion => { suggestion.severity = '1'; }, suggestion => { suggestion.id = ''; },
      suggestion => { suggestion.category = 'unknown'; }, suggestion => { suggestion.source = 'unknown'; },
      suggestion => { suggestion.provenance = 'unlicensed'; }, suggestion => { suggestion.telemetry_class = 'content'; },
      suggestion => { suggestion.locale = '!'; }, suggestion => { suggestion.doc_version = -1; },
      suggestion => { suggestion.doc_version = 0.5; }, suggestion => { suggestion.doc_version = 9007199254740992; },
      suggestion => { suggestion.span.utf8 = [0, 1, 2]; }, suggestion => { suggestion.span.scalar = [-1, 2]; },
      suggestion => { suggestion.span.utf16 = [0, 4294967296]; },
      suggestion => { suggestion.replacements[0].confidence = -0.1; },
      suggestion => { suggestion.replacements[0].confidence = 1.1; },
      suggestion => { suggestion.replacements[0].kind = 'unknown'; },
    ];
    for (const corrupt of corruptions) {
      const malformed = structuredClone(sample); corrupt(malformed);
      assert.equal(validate(malformed), false, 'schema accepted malformed suggestion');
    }
  } finally { await rm(directory, { recursive: true, force: true }); }
});
