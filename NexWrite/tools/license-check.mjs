// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { files, isMain, read, report, root } from './lint-common.mjs';

export const permitted = new Set(['MIT', 'Apache-2.0', 'BSD-2-Clause', 'BSD-3-Clause', 'ISC', '0BSD', 'CC0-1.0', 'Unlicense', 'Unicode-3.0', 'Zlib']);
const fontPackages = new Set(['@fontsource-variable/inter', '@fontsource-variable/jetbrains-mono', '@fontsource-variable/space-grotesk']);
const accessibilityTools = new Set(['@axe-core/playwright', 'axe-core']);
const cssBuildTools = new Set(['lightningcss', 'lightningcss-darwin-x64', 'lightningcss-darwin-arm64', 'lightningcss-win32-x64-msvc', 'lightningcss-win32-arm64-msvc', 'lightningcss-linux-x64-gnu', 'lightningcss-linux-x64-musl', 'lightningcss-linux-arm64-gnu', 'lightningcss-linux-arm64-musl', 'lightningcss-linux-arm-gnueabihf', 'lightningcss-freebsd-x64', 'lightningcss-android-arm64']);
export function reviewedNpmLicense(pkg, { devOnly = false } = {}) {
  return (fontPackages.has(pkg?.name) && pkg.version === '5.3.0' && pkg.license === 'OFL-1.1')
    || (devOnly === true && pkg?.license === 'MPL-2.0'
      && ((accessibilityTools.has(pkg.name) && pkg.version === '4.14.0') || (cssBuildTools.has(pkg.name) && pkg.version === '1.33.0')));
}
export function productionNpmNames(workspaces, installed) {
  const dependencies = pkg => Object.keys({ ...pkg.dependencies, ...pkg.optionalDependencies, ...pkg.peerDependencies });
  const graph = new Map();
  // Merge versions conservatively: a production path to any installed version needs review.
  for (const pkg of [...workspaces, ...installed]) graph.set(pkg.name, [...(graph.get(pkg.name) ?? []), ...dependencies(pkg)]);
  const pending = workspaces.flatMap(dependencies); const production = new Set();
  while (pending.length) {
    const name = pending.pop(); if (production.has(name)) continue;
    production.add(name); pending.push(...(graph.get(name) ?? []));
  }
  return production;
}
// Cargo's legacy MIT/Apache-2.0 spelling denotes the same dual-license OR choice.
export function rustLicense(expression) {
  return expression === 'MIT/Apache-2.0' || expression === 'Apache-2.0/MIT' ? 'MIT OR Apache-2.0' : expression;
}
export function acceptable(expression) {
  if (typeof expression !== 'string') return false;
  // Parentheses only group conjunctions/disjunctions; unsupported SPDX operators fail closed.
  const tokens = expression.match(/[A-Za-z0-9.+-]+|[()]/g) ?? [];
  if (tokens.join('') !== expression.replace(/\s+/g, '')) return false;
  let i = 0;
  function atom() {
    if (tokens[i] === '(') { i++; const value = or(); if (tokens[i++] !== ')') throw Error('group'); return value; }
    const token = tokens[i++]; if (!token || ['AND', 'OR', ')'].includes(token)) throw Error('license');
    return permitted.has(token);
  }
  function and() { let value = atom(); while (tokens[i] === 'AND') { i++; const next = atom(); value = value && next; } return value; }
  function or() { let value = and(); while (tokens[i] === 'OR') { i++; const next = and(); value = value || next; } return value; }
  try { const value = or(); return value && i === tokens.length; } catch { return false; }
}
function run(base, command, args) {
  const result = spawnSync(command, args, { cwd: base, encoding: 'utf8', shell: process.platform === 'win32' && command === 'pnpm' });
  if (result.error || result.status !== 0) throw new Error(`${command} ${args.join(' ')} failed; install locked dependencies/toolchain first (${result.error?.message ?? result.stderr.trim()})`);
  return result.stdout;
}
export function lintLicenses(base) {
  const errors = [];
  if (!read(base, 'LICENSE').includes('All rights reserved')) errors.push('root LICENSE must preserve operator license decision');
  const metadata = JSON.parse(run(base, 'cargo', ['metadata', '--locked', '--format-version', '1']));
  for (const pkg of metadata.packages) {
    if (!pkg.source) {
      const licenseFile = pkg.license_file && path.resolve(path.dirname(pkg.manifest_path), pkg.license_file);
      if (pkg.license !== 'LicenseRef-NexWrite-Proprietary' && licenseFile !== path.join(base, 'LICENSE')) errors.push(`${pkg.name}: workspace license differs from operator decision`);
    } else if (!acceptable(rustLicense(pkg.license))) errors.push(`${pkg.name}@${pkg.version}: license ${pkg.license ?? 'MISSING'} requires review`);
  }
  const modules = path.join(base, 'node_modules/.pnpm');
  const installed = [];
  if (!fs.existsSync(modules)) errors.push('pnpm installation missing; license inventory cannot be complete');
  else for (const store of fs.readdirSync(modules, { withFileTypes: true })) {
    if (!store.isDirectory() || store.name === 'node_modules') continue;
    const manifests = path.join(modules, store.name, 'node_modules');
    if (!fs.existsSync(manifests)) continue;
    const folders = fs.readdirSync(manifests, { withFileTypes: true }).flatMap(entry => entry.name.startsWith('@')
      ? fs.readdirSync(path.join(manifests, entry.name), { withFileTypes: true }).map(child => path.join(manifests, entry.name, child.name))
      : [path.join(manifests, entry.name)]);
    for (const folder of folders) {
      if (fs.lstatSync(folder).isSymbolicLink() || !fs.existsSync(path.join(folder, 'package.json'))) continue;
      installed.push(JSON.parse(fs.readFileSync(path.join(folder, 'package.json'), 'utf8')));
    }
  }
  const workspaces = files(base).filter(file => /^(?:package\.json|(?:packages|apps|services)\/[^/]+\/package\.json)$/.test(file)).map(file => JSON.parse(read(base, file)));
  const production = productionNpmNames(workspaces, installed);
  for (const pkg of installed) {
    if (!reviewedNpmLicense(pkg, { devOnly: !production.has(pkg.name) }) && !acceptable(pkg.license)) errors.push(`${pkg.name}@${pkg.version}: npm license ${JSON.stringify(pkg.license) ?? 'MISSING'} requires review`);
  }
  return errors;
}
if (isMain(import.meta.url)) {
  try { report('license-check', lintLicenses(root)); } catch (error) { report('license-check', [error.message]); }
}
