// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';

const result = spawnSync('cargo', ['run', '--quiet', '--release', '--locked', '-p', 'nw-cli', '--', 'bench'], { encoding: 'utf8', cwd: new URL('..', import.meta.url) });
if (result.status !== 0) throw new Error(result.stderr || 'native benchmark failed');
const report = JSON.parse(result.stdout);
assert.deepEqual(report.measurements.map(m => m.words), [1000, 5000]);
assert.ok(report.measurements.every(m => Number.isFinite(m.p95_ms) && m.p95_ms >= 0 && m.iterations === 100));
console.log(JSON.stringify({ ...report, node_version: process.version, architecture: process.arch, platform: process.platform }));
