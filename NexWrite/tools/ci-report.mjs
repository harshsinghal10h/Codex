// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
// Preserve command failures and expose a bounded diagnostic when CI log downloads
// are unavailable. This runner is for source-controlled tests, never user data.
import { spawn } from 'node:child_process';
import { createWriteStream, mkdirSync, readFileSync } from 'node:fs';
import path from 'node:path';

const arguments_ = process.argv.slice(2);
const separator = arguments_.indexOf('--');
const options = new Map();
if (separator < 0 || separator === arguments_.length - 1 || separator % 2 !== 0) {
  throw new Error('Usage: ci-report.mjs --label NAME --log PATH [--report PATH] -- COMMAND [ARGS]');
}
for (let index = 0; index < separator; index += 2) {
  const key = arguments_[index];
  if (!['--label', '--log', '--report'].includes(key) || options.has(key)) {
    throw new Error('Unsupported or duplicate CI reporter option');
  }
  options.set(key, arguments_[index + 1]);
}
if (!options.get('--label') || !options.get('--log')) throw new Error('CI label and log are required');

const logPath = options.get('--log');
mkdirSync(path.dirname(logPath), { recursive: true });
const log = createWriteStream(logPath, { flags: 'w' });
let tail = '';
const collect = (destination) => (chunk) => {
  destination.write(chunk);
  log.write(chunk);
  tail = (tail + chunk.toString('utf8')).slice(-12_000);
};
const command = arguments_[separator + 1];
const child = spawn(command, arguments_.slice(separator + 2), {
  stdio: ['inherit', 'pipe', 'pipe'],
  shell: false,
});
child.stdout.on('data', collect(process.stdout));
child.stderr.on('data', collect(process.stderr));
child.on('error', (error) => {
  tail += `\nCommand could not start: ${error.code ?? 'unknown error'}`;
});

const clean = (value) => value
  .replace(/\u001b\[[0-?]*[ -/]*[@-~]/g, '')
  .replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, '');
const escapeData = (value) => clean(value).replace(/%/g, '%25').replace(/\r/g, '%0D').replace(/\n/g, '%0A');
const escapeProperty = (value) => escapeData(value).replace(/:/g, '%3A').replace(/,/g, '%2C');

child.on('close', async (code, signal) => {
  await new Promise((resolve, reject) => log.end((error) => error ? reject(error) : resolve()));
  if (code !== 0) {
    let cases = '';
    const reportPath = options.get('--report');
    if (reportPath) {
      try {
        const report = JSON.parse(readFileSync(reportPath, 'utf8'));
        cases = report.cases?.filter((entry) => entry.status === 'failed').slice(0, 5)
          .map((entry) => `${entry.name}: ${entry.detail ?? 'failed'}`).join('\n') ?? '';
      } catch {
        cases = 'Structured test report was unavailable or invalid.';
      }
    }
    const detail = [clean(cases).slice(0, 4_000), clean(tail).slice(-4_000)]
      .filter(Boolean).join('\n').trim();
    const label = escapeProperty(options.get('--label'));
    process.stdout.write(`\n::error title=${label}::${escapeData(`Test command failed (${code ?? signal ?? 'unknown'}).\n${detail}`)}\n`);
    // An ordinary command's failure status is retained; signal termination also
    // fails instead of accidentally treating a null exit code as success.
    process.exitCode = Number.isInteger(code) && code > 0 && code <= 255 ? code : 1;
  }
});
