// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const read = (base, file) => fs.readFileSync(path.join(base, file), 'utf8');
export function localPath(base, file) {
  return typeof file === 'string' && file.length > 0 && !path.isAbsolute(file)
    && !file.includes('\\') && !file.split('/').includes('..')
    && fs.existsSync(path.join(base, file))
    && !path.relative(fs.realpathSync(base), fs.realpathSync(path.join(base, file))).startsWith('..');
}
export function files(base, prefix = '') {
  const ignored = new Set(['.git', 'node_modules', 'target', 'dist', '.turbo', '.pnpm-store']);
  const generated = new Set(['apps/web/public/engine', 'apps/web/public/notices', 'apps/web/test-results', 'apps/android/.gradle', 'apps/android/.kotlin', 'apps/android/build', 'apps/android/app/build']);
  return fs.readdirSync(path.join(base, prefix), { withFileTypes: true }).flatMap(entry => {
    if (ignored.has(entry.name) || entry.isSymbolicLink()) return [];
    const file = path.posix.join(prefix, entry.name);
    if (entry.isDirectory() && generated.has(file)) return [];
    return entry.isDirectory() ? files(base, file) : [file];
  });
}
export function report(label, problems) {
  if (problems.length) {
    console.error(problems.map(message => `${label}: ${message}`).join('\n'));
    process.exitCode = 1;
  } else console.log(`${label}: passed`);
}
export function isMain(url) {
  return process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(url);
}
