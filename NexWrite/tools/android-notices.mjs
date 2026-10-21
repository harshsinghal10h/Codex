// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
// Package notices from installed inputs; optional pinned legal-text fetch, no vendored implementation.
import { readFile, writeFile, mkdir, readdir, stat } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { dirname, resolve, join, relative, isAbsolute } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const noticeName = /^(license|copying|copyright|notice)([._-]|$)/i;
const hash = text => createHash('sha256').update(text).digest('hex');
const protobufNotice = {
  coordinate: 'androidx.datastore:datastore-preferences-external-protobuf:1.1.7',
  artifact_sha256: 'fcb3f373cf743426c85ebb0d3cc644aefaf26f39b7fa55bb90238d41b689eca7',
  filename: 'protobuf-v28.2-LICENSE',
  url: 'https://raw.githubusercontent.com/protocolbuffers/protobuf/v28.2/LICENSE',
  sha256: '6e5e117324afd944dcf67f36cf329843bc1a92229a8cd9bb573d7a83130fea7d',
};
const run = (command, args, options = {}) => {
  const result = spawnSync(command, args, { cwd: root, encoding: 'utf8', maxBuffer: 32 * 1024 * 1024, ...options });
  if (result.error || result.status !== 0) throw new Error(`${command} notice collection failed: ${result.error?.message ?? result.stderr.trim()}`);
  return result.stdout;
};

export function inside(directory, path) {
  const suffix = relative(resolve(directory), resolve(path));
  return suffix !== '..' && !suffix.startsWith(`..${process.platform === 'win32' ? '\\' : '/'}`) && !isAbsolute(suffix);
}

export async function collectRust(metadata) {
  const inventory = [];
  const sections = ['NexWrite Android native engine notices\nThis is a conservative locked Cargo workspace superset, including build-time and other-platform dependencies. Presence here does not prove runtime linkage.\n'];
  const gaps = [];
  for (const dependency of metadata.packages.filter(item => item.source?.startsWith('registry+')).sort((a, b) => a.name.localeCompare(b.name) || a.version.localeCompare(b.version))) {
    const directory = dirname(dependency.manifest_path);
    const filenames = (await readdir(directory)).filter(name => noticeName.test(name));
    if (dependency.license_file && !filenames.includes(dependency.license_file)) filenames.push(dependency.license_file);
    const notices = [];
    for (const name of filenames.sort()) {
      const path = resolve(directory, name);
      if (!inside(directory, path)) throw new Error(`Unsafe notice path for ${dependency.name}`);
      if (!(await stat(path)).isFile()) continue;
      const text = await readFile(path, 'utf8');
      notices.push({ file: name, sha256: hash(text) });
      sections.push(`\n===== ${dependency.name} ${dependency.version} — ${dependency.license ?? 'undeclared license'} / ${name} =====\n${text}\n`);
    }
    const buildOnly = dependency.targets?.every(target => target.kind?.includes('proc-macro')) ?? false;
    if (!notices.length) {
      gaps.push({ component: `${dependency.name}@${dependency.version}`, kind: buildOnly ? 'build-tool-license-text-missing' : 'license-text-missing', declared_license: dependency.license ?? null });
      sections.push(`\n===== ${dependency.name} ${dependency.version} =====\nDeclared license: ${dependency.license ?? 'undeclared'}. Installed package contains no complete license/notice text. ${buildOnly ? 'Proc-macro build tool; not a separately linked runtime library.' : 'Distribution notice review remains incomplete.'}\n`);
    }
    inventory.push({ name: dependency.name, version: dependency.version, declared_license: dependency.license ?? null, build_tool: buildOnly, notices });
  }
  return { text: sections.join(''), inventory, gaps };
}

// The parser reads only explicitly supplied local cache files. ElementTree never fetches URLs.
const pythonInspector = String.raw`
import io,json,pathlib,re,sys,zipfile,xml.etree.ElementTree as ET
p=json.load(sys.stdin); result={'poms':[],'notices':[],'gaps':[]}
seen=set()
def pom(filename,depth=0):
 if filename in seen:return
 if depth>8:raise ValueError('POM parent depth exceeded')
 seen.add(filename)
 text=pathlib.Path(filename).read_text(encoding='utf-8')
 if '<!DOCTYPE' in text.upper() or '<!ENTITY' in text.upper(): raise ValueError('DTD/entity POM rejected')
 root=ET.fromstring(text); local=lambda n:n.tag.split('}')[-1]
 def children(node,name):return [n for n in node if local(n)==name]
 def childtext(node,name):return next((n.text.strip() for n in children(node,name) if n.text),'')
 licenses=[]
 for parent in children(root,'licenses'):
  for license in children(parent,'license'):licenses.append({'name':childtext(license,'name'),'url':childtext(license,'url')})
 result['poms'].append({'file':pathlib.Path(filename).name,'inherited_parent':depth>0,'licenses':licenses,'name':childtext(root,'name'),'description':childtext(root,'description'),'url':childtext(root,'url')})
 for parent in children(root,'parent'):
  coordinates=[childtext(parent,n) for n in ['groupId','artifactId','version']]
  if any(not re.fullmatch('[A-Za-z0-9_.+-]+',v) for v in coordinates):raise ValueError('unsafe parent POM coordinate')
  directory=pathlib.Path(p['cache'])/'caches/modules-2/files-2.1'/pathlib.Path(*coordinates)
  for cached in sorted(directory.glob('*/*.pom')):pom(str(cached),depth+1)
for filename in p['poms']:pom(filename)
def scan(data,prefix='',depth=0):
 with zipfile.ZipFile(data) as z:
  for info in sorted(z.infolist(),key=lambda i:i.filename):
   if info.is_dir():continue
   name=pathlib.PurePosixPath(info.filename).name.lower()
   if depth==0 and name=='classes.jar':
    if info.file_size>32*1024*1024: result['gaps'].append('nested classes.jar exceeds notice inspection cap');continue
    scan(io.BytesIO(z.read(info)),prefix+info.filename+'!/',1)
   if not any(name==k or name.startswith(k+'.') or name.startswith(k+'-') or name.startswith(k+'_') for k in ['license','copying','copyright','notice']):continue
   if info.file_size>2*1024*1024:result['gaps'].append(prefix+info.filename+' exceeds notice size cap');continue
   try:text=z.read(info).decode('utf-8')
   except UnicodeDecodeError:result['gaps'].append(prefix+info.filename+' is not UTF-8 notice text');continue
   result['notices'].append({'file':prefix+info.filename,'text':text})
scan(p['file']);json.dump(result,sys.stdout)
`;

export async function inspectGradleComponent(component, cache) {
  for (const key of ['group', 'name', 'version']) if (typeof component[key] !== 'string' || !/^[A-Za-z0-9_.+-]+$/.test(component[key])) throw new Error(`Unsafe Gradle component ${key}`);
  if (typeof component.file !== 'string' || !/\.(jar|aar)$/.test(component.file)) throw new Error('Expected resolved Gradle jar/aar path');
  if (!inside(cache, component.file)) throw new Error('Resolved Gradle artifact is outside the selected cache');
  const directory = join(cache, 'caches/modules-2/files-2.1', component.group, component.name, component.version);
  const poms = [];
  try {
    for (const folder of await readdir(directory, { withFileTypes: true })) {
      if (!folder.isDirectory()) continue;
      for (const filename of await readdir(join(directory, folder.name))) if (filename.endsWith('.pom')) poms.push(join(directory, folder.name, filename));
    }
  } catch (error) { if (error.code !== 'ENOENT') throw error; }
  const parsed = JSON.parse(run('python3', ['-c', pythonInspector], { input: JSON.stringify({ file: component.file, poms: poms.sort(), cache }) }));
  return { ...parsed, artifact_sha256: hash(await readFile(component.file)) };
}

function apacheDeclaration(license) {
  return /^https?:\/\/(?:www\.)?apache\.org\/licenses\/LICENSE-2\.0(?:\.txt)?\/?$/i.test(license.url)
    || /^(?:The )?Apache(?: Software)? License,? (?:Version )?2\.0$/i.test(license.name)
    || /^Apache(?: License)? 2\.0$/i.test(license.name);
}

export async function fetchLicenseInputs(base = root) {
  const directory = join(base, 'target/android-license-inputs');
  await mkdir(directory, { recursive: true });
  const destination = join(directory, protobufNotice.filename);
  let text;
  try { text = await readFile(destination); } catch (error) { if (error.code !== 'ENOENT') throw error; }
  if (!text) {
    // Keep inherited proxy/CA/credential configuration; never disable TLS verification.
    run('curl', ['--fail', '--location', '--silent', '--show-error', '--retry', '2', '--max-time', '45', '--max-filesize', '65536', protobufNotice.url, '--output', destination]);
    text = await readFile(destination);
  }
  if (hash(text) !== protobufNotice.sha256) throw new Error('Pinned Protobuf license input SHA-256 mismatch');
}

export async function generate({ base = root, gradleCache = process.env.GRADLE_USER_HOME ?? join(root, 'target/android-gradle-home'), componentsPath = join(root, 'target/android-runtime-components.json') } = {}) {
  const metadata = JSON.parse(run('cargo', ['metadata', '--offline', '--locked', '--format-version', '1'], { cwd: base }));
  const rust = await collectRust(metadata);
  const sysroot = run('rustc', ['--print', 'sysroot']).trim();
  const rustDoc = join(sysroot, 'share/doc/rust');
  const standardApache = await readFile(join(rustDoc, 'licenses/Apache-2.0.txt'), 'utf8');
  const runtimeFiles = ['COPYRIGHT-library.html', ...(await readdir(join(rustDoc, 'licenses'))).filter(name => name.endsWith('.txt')).map(name => `licenses/${name}`)];
  const out = join(base, 'target/android-assets/notices');
  await mkdir(out, { recursive: true });
  await writeFile(join(out, 'engine-third-party.txt'), rust.text);
  const standardNotices = ['Rust standard library and compiler runtime notice resources\nConservative toolchain distribution notice superset; included license texts do not imply every listed component is linked into this APK.\n'];
  for (const file of runtimeFiles) standardNotices.push(`\n===== ${file} =====\n${await readFile(join(rustDoc, file), 'utf8')}\n`);
  await writeFile(join(out, 'rust-runtime-third-party.txt'), standardNotices.join(''));
  const components = JSON.parse(await readFile(componentsPath, 'utf8'));
  if (!Array.isArray(components) || !components.length) throw new Error('Resolve releaseRuntimeClasspath before notice generation');
  const androidInventory = [];
  const androidTexts = ['NexWrite Android dependencies\nBased on resolved releaseRuntimeClasspath artifacts and their local POMs. Full embedded LICENSE/NOTICE/COPYRIGHT files are preserved below. A POM declaration plus the standard Apache license is marked separately from embedded notices. This engineering inventory is not complete legal clearance or an exact runtime reachability SBOM.\n'];
  const gaps = [...rust.gaps];
  for (const component of components.sort((a, b) => `${a.group}:${a.name}:${a.version}`.localeCompare(`${b.group}:${b.name}:${b.version}`))) {
    const inspected = await inspectGradleComponent(component, gradleCache);
    const coordinate = `${component.group}:${component.name}:${component.version}`;
    let supplemental = null;
    if (coordinate === protobufNotice.coordinate && inspected.artifact_sha256 === protobufNotice.artifact_sha256) {
      try {
        const text = await readFile(join(base, 'target/android-license-inputs', protobufNotice.filename), 'utf8');
        if (hash(text) !== protobufNotice.sha256) throw new Error('Pinned Protobuf license input SHA-256 mismatch');
        inspected.notices.push({ file: `upstream/${protobufNotice.filename}`, text });
        supplemental = { url: protobufNotice.url, sha256: protobufNotice.sha256, review: 'Exact shaded artifact RuntimeVersion constants identify Protobuf 4.28.2; upstream v28.2 full BSD-3-Clause copyright/license text.' };
      } catch (error) { if (error.code !== 'ENOENT') throw error; }
    }
    const licenses = inspected.poms.flatMap(pom => pom.licenses);
    const allApache = licenses.length > 0 && licenses.every(apacheDeclaration);
    const licenseCoverage = supplemental ? 'pinned-upstream-license-text' : inspected.notices.length ? 'embedded-notice-texts' : allApache ? 'pom-declaration-and-standard-Apache-text' : 'incomplete';
    if (!inspected.poms.length) gaps.push({ component: coordinate, kind: 'pom-missing' });
    if (licenseCoverage === 'incomplete') gaps.push({ component: coordinate, kind: 'license-text-review-required', declarations: licenses });
    for (const gap of inspected.gaps) gaps.push({ component: coordinate, kind: 'artifact-notice-inspection-gap', detail: gap });
    androidTexts.push(`\n===== ${coordinate} =====\nArtifact SHA-256: ${inspected.artifact_sha256}\nLicense coverage: ${licenseCoverage}\n`);
    if (supplemental) androidTexts.push(`Supplemental license source: ${supplemental.url}\nSource SHA-256: ${supplemental.sha256}\nReview: ${supplemental.review}\n`);
    for (const pom of inspected.poms) androidTexts.push(`POM name: ${pom.name}\nSource: ${pom.url}\nDeclared licenses: ${JSON.stringify(pom.licenses)}\n`);
    for (const notice of inspected.notices) androidTexts.push(`\n--- ${notice.file} ---\n${notice.text}\n`);
    androidInventory.push({ group: component.group, name: component.name, version: component.version, artifact_sha256: inspected.artifact_sha256, licenses, license_coverage: licenseCoverage, ...(supplemental ? { supplemental } : {}), notices: inspected.notices.map(notice => ({ file: notice.file, sha256: hash(notice.text) })) });
  }
  androidTexts.push(`\n===== Standard Apache License 2.0 text (from installed Rust documentation) =====\n${standardApache}\n`);
  await writeFile(join(out, 'android-third-party.txt'), androidTexts.join(''));
  await writeFile(join(out, 'THIRD_PARTY_NOTICES.txt'), ['NexWrite third-party notices\nGenerated from installed locked packages and resolved Android artifacts. Conservative dependency/toolchain supersets; not complete legal clearance or exact runtime reachability.\n\n', rust.text, '\n\n', standardNotices.join(''), '\n\n', androidTexts.join('')].join(''));
  const inventory = { format_version: 1, source: 'locked Cargo metadata + resolved Gradle releaseRuntimeClasspath + installed license/POM/notice files', status: gaps.length ? 'inventory-with-open-gaps' : 'notice-inventory-generated', exact_runtime_reachability: false, rust: rust.inventory, android: androidInventory, gaps };
  await writeFile(join(out, 'inventory.json'), JSON.stringify(inventory, null, 2) + '\n');
  await writeFile(join(out, 'index.txt'), ['NexWrite third-party notices', '', 'engine-third-party.txt — locked Rust registry notices (workspace superset)', 'rust-runtime-third-party.txt — Rust library/toolchain notices (superset)', 'android-third-party.txt — resolved Android runtime dependency notices', 'inventory.json — observed metadata, file hashes and open notice-review gaps', '', `Status: ${inventory.status}; ${gaps.length} recorded gap(s).`, 'No complete legal-clearance or exact runtime reachability claim is made.', ''].join('\n'));
  process.stdout.write(`Generated Android notices: ${rust.inventory.length} Rust registry packages; ${androidInventory.length} Android artifacts; ${gaps.length} recorded gap(s).\n`);
  return inventory;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (process.argv.slice(2).some(arg => !['--require-complete', '--fetch-license-inputs'].includes(arg))) throw new Error('Usage: node tools/android-notices.mjs [--fetch-license-inputs] [--require-complete]');
  if (process.argv.includes('--fetch-license-inputs')) await fetchLicenseInputs();
  const inventory = await generate();
  if (inventory.gaps.length) {
    process.stderr.write(`Notice review has open gaps; inspect target/android-assets/notices/inventory.json.\n`);
    if (process.argv.includes('--require-complete')) process.exitCode = 1;
  }
}
