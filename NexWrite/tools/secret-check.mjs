// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { spawnSync } from 'node:child_process';
import { root } from './lint-common.mjs';
const result = spawnSync('gitleaks', ['dir', '.', '--config', '.gitleaks.toml', '--redact', '--no-banner'], { cwd: root, stdio: 'inherit' });
if (result.error) {
  console.error('secret-check: gitleaks is required; install version 8.30.1 from its checksum-verified official release.');
  process.exitCode = 2;
} else process.exitCode = result.status ?? 2;
