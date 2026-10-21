// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { availableParallelism, cpus } from 'node:os';

const result = spawnSync('cargo', ['run', '--quiet', '--release', '--locked', '-p', 'nw-cli', '--', 'bench', 'analysis'], { encoding: 'utf8', cwd: new URL('..', import.meta.url) });
if (result.status !== 0) throw new Error(result.stderr || 'analysis benchmark failed');
const report = JSON.parse(result.stdout);
assert.deepEqual(report.measurements.map(m => [m.input_format, m.fixture_prose_words]), [['plain', 1000], ['plain', 5000], ['markdown', 1000], ['markdown', 5000]]);
assert.ok(report.measurements.every(m => Number.isFinite(m.p95_ms) && m.p95_ms >= 0 && m.iterations === 50));
console.log(JSON.stringify({ ...report, node_version: process.version, architecture: process.arch, platform: process.platform, cpu_model: cpus()[0]?.model ?? 'unknown', available_parallelism: availableParallelism() }));
