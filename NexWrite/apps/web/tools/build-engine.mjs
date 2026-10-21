// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { spawnSync } from 'node:child_process';
import { copyFile, mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const app = fileURLToPath(new URL('..', import.meta.url));
const root = path.resolve(app, '../..');
const output = path.join(app, 'public/engine');
const marker = '.nexwrite-generated';
await mkdir(output, { recursive: true });
const entries = await readdir(output);
if (entries.length && !(await readFile(path.join(output, marker), 'utf8').catch(() => '')).startsWith('NexWrite generated WASM')) {
  throw new Error('Refusing to overwrite an unrecognized public/engine directory.');
}
await writeFile(path.join(output, marker), 'NexWrite generated WASM; rebuild with the app build script.\n');
function run(command, args) {
  const result = spawnSync(command, args, { cwd: root, stdio: 'inherit', env: process.env });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}
run('cargo', ['build', '-p', 'nw-wasm', '--locked', '--release', '--target', 'wasm32-unknown-unknown']);
run('wasm-bindgen', [path.join(root, 'target/wasm32-unknown-unknown/release/nw_wasm.wasm'), '--target', 'web', '--out-dir', output, '--out-name', 'nw_wasm']);
const require = createRequire(import.meta.url);
const notices = path.join(app, 'public/notices');
await mkdir(notices, { recursive: true });
const noticeEntries = await readdir(notices);
if (noticeEntries.length && !(await readFile(path.join(notices, marker), 'utf8').catch(() => '')).startsWith('NexWrite generated notices')) {
  throw new Error('Refusing to overwrite an unrecognized public/notices directory.');
}
await writeFile(path.join(notices, marker), 'NexWrite generated notices; copied verbatim from installed dependencies.\n');
const licenses = [
  ['@fontsource-variable/inter', 'inter-OFL.txt'],
  ['@fontsource-variable/space-grotesk', 'space-grotesk-OFL.txt'],
  ['@fontsource-variable/jetbrains-mono', 'jetbrains-mono-OFL.txt'],
  ['react', 'react-MIT.txt'],
  ['react-dom', 'react-dom-MIT.txt'],
  ['scheduler', 'scheduler-MIT.txt'],
];
for (const [name, filename] of licenses) {
  const dependencyRequire = name === 'scheduler' ? createRequire(require.resolve('react-dom/package.json')) : require;
  await copyFile(path.join(path.dirname(dependencyRequire.resolve(`${name}/package.json`)), 'LICENSE'), path.join(notices, filename));
}
const metadataRun = spawnSync('cargo', ['metadata', '--locked', '--offline', '--filter-platform', 'wasm32-unknown-unknown', '--format-version', '1'], { cwd: root, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
if (metadataRun.status !== 0) throw new Error('Unable to collect locked engine dependency notices.');
const metadata = JSON.parse(metadataRun.stdout);
// Walk this artifact's production/build graph. Other workspace surfaces (e.g.
// JNI) are not linked into WASM and must not be advertised as its dependencies.
const nodes = new Map(metadata.resolve.nodes.map((node) => [node.id, node]));
const reachable = new Set();
const pending = [metadata.packages.find((dependency) => dependency.name === 'nw-wasm')?.id];
while (pending.length) {
  const id = pending.pop();
  if (!id || reachable.has(id)) continue;
  reachable.add(id);
  const node = nodes.get(id);
  if (!node) throw new Error('Incomplete WASM dependency graph.');
  for (const dependency of node.deps) if (dependency.dep_kinds.some((item) => item.kind !== 'dev')) pending.push(dependency.pkg);
}
const registryPackages = metadata.packages.filter((dependency) => reachable.has(dependency.id) && dependency.source?.startsWith('registry+'))
  .sort((a, b) => a.name.localeCompare(b.name) || a.version.localeCompare(b.version));
const engineNotices = ['NexWrite WASM engine — locked registry dependency notices\nProduction and build graph for wasm32-unknown-unknown; includes build-time dependencies.\n'];
for (const dependency of registryPackages) {
  const directory = path.dirname(dependency.manifest_path);
  const licenseFiles = (await readdir(directory)).filter((filename) => /^(license|copying|notice)([._-]|$)/i.test(filename));
  if (dependency.license_file && !licenseFiles.includes(dependency.license_file)) licenseFiles.push(dependency.license_file);
  if (!licenseFiles.length) throw new Error(`Missing license text for ${dependency.name}@${dependency.version}.`);
  engineNotices.push(`\n===== ${dependency.name} ${dependency.version} (${dependency.license ?? 'see license text'}) =====\n`);
  for (const filename of licenseFiles.sort()) engineNotices.push(`\n--- ${filename} ---\n${await readFile(path.join(directory, filename), 'utf8')}\n`);
}
await writeFile(path.join(notices, 'engine-third-party.txt'), engineNotices.join(''));
