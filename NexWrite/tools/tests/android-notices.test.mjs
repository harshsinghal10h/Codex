// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { test, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { collectRust, inspectGradleComponent, inside, fetchLicenseInputs } from '../android-notices.mjs';

const temporary = [];
async function fixture() { const path = await mkdtemp(join(tmpdir(), 'nw-notices-')); temporary.push(path); return path; }
afterEach(async () => { for (const path of temporary.splice(0)) await rm(path, { recursive: true, force: true }); });

test('Cargo notice collector preserves copyright files and exact license text', async () => {
  const base = await fixture();
  await writeFile(join(base, 'COPYRIGHT-RUST.txt'), 'Upstream attribution\nApache 2.0 or MIT license.\n');
  const result = await collectRust({ packages: [{ source: 'registry+https://example.invalid', name: 'fixture', version: '1.0.0', license: 'MIT OR Apache-2.0', manifest_path: join(base, 'Cargo.toml'), targets: [{ kind: ['lib'] }] }] });
  assert.equal(result.inventory.length, 1);
  assert.equal(result.inventory[0].notices[0].file, 'COPYRIGHT-RUST.txt');
  assert.match(result.text, /Upstream attribution\nApache 2.0 or MIT license\./);
  assert.equal(result.gaps.length, 0);
});

test('missing Cargo license text is a visible gap, including proc-macro build tools', async () => {
  const base = await fixture();
  const result = await collectRust({ packages: [{ source: 'registry+https://example.invalid', name: 'fixture-macro', version: '1.0.0', license: 'MIT', manifest_path: join(base, 'Cargo.toml'), targets: [{ kind: ['proc-macro'] }] }] });
  assert.equal(result.inventory[0].build_tool, true);
  assert.equal(result.gaps[0].kind, 'build-tool-license-text-missing');
  assert.match(result.text, /contains no complete license\/notice text/);
});

test('license file paths cannot escape installed package directories', async () => {
  const base = await fixture();
  assert.equal(inside(base, join(base, 'LICENSE')), true);
  assert.equal(inside(base, join(base, '../LICENSE')), false);
  await assert.rejects(collectRust({ packages: [{ source: 'registry+x', name: 'fixture', version: '1', license_file: '../outside.txt', manifest_path: join(base, 'Cargo.toml') }] }), /Unsafe notice path/);
  await assert.rejects(inspectGradleComponent({ group: '../outside', name: 'fixture', version: '1', file: 'fixture.jar' }, base), /Unsafe Gradle/);
  await assert.rejects(inspectGradleComponent({ group: 'example', name: 'fixture', version: '1', file: join(base, '../outside.jar') }, base), /outside the selected cache/);
});

test('local POM declarations and nested AAR notices are inspected without extraction', async () => {
  const base = await fixture();
  const directory = join(base, 'caches/modules-2/files-2.1/example.library/sample/1.0/hash');
  await mkdir(directory, { recursive: true });
  const artifact = join(directory, 'sample.aar');
  const create = spawnSync('python3', ['-c', "import io,sys,zipfile; b=io.BytesIO(); z=zipfile.ZipFile(b,'w'); z.writestr('META-INF/LICENSE.txt','Nested license preserved\\n'); z.close(); a=zipfile.ZipFile(sys.argv[1],'w'); a.writestr('classes.jar',b.getvalue()); a.writestr('META-INF/NOTICE.txt','Outer attribution\\n'); a.close()", artifact], { encoding: 'utf8' });
  assert.equal(create.status, 0, create.stderr);
  await writeFile(join(directory, 'sample.pom'), '<project xmlns="http://maven.apache.org/POM/4.0.0"><name>Sample</name><licenses><license><name>Apache License, Version 2.0</name><url>https://www.apache.org/licenses/LICENSE-2.0.txt</url></license></licenses></project>');
  const result = await inspectGradleComponent({ group: 'example.library', name: 'sample', version: '1.0', file: artifact }, base);
  assert.equal(result.poms[0].licenses[0].name, 'Apache License, Version 2.0');
  assert.deepEqual(result.notices.map(notice => notice.text).sort(), ['Nested license preserved\n', 'Outer attribution\n']);
  assert.match(result.artifact_sha256, /^[a-f0-9]{64}$/);
  const parentDirectory = join(base, 'caches/modules-2/files-2.1/example.library/parent/1.0/hash');
  await mkdir(parentDirectory, { recursive: true });
  await writeFile(join(parentDirectory, 'parent.pom'), '<project><name>Parent</name><licenses><license><name>Apache-2.0</name></license></licenses></project>');
  await writeFile(join(directory, 'sample.pom'), '<project><parent><groupId>example.library</groupId><artifactId>parent</artifactId><version>1.0</version></parent></project>');
  const inherited = await inspectGradleComponent({ group: 'example.library', name: 'sample', version: '1.0', file: artifact }, base);
  assert.equal(inherited.poms[1].inherited_parent, true);
  assert.equal(inherited.poms[1].licenses[0].name, 'Apache-2.0');
  await writeFile(join(directory, 'sample.pom'), '<!DOCTYPE x [<!ENTITY x SYSTEM "https://example.invalid/secret">]><project/>');
  await assert.rejects(inspectGradleComponent({ group: 'example.library', name: 'sample', version: '1.0', file: artifact }, base), /DTD\/entity POM rejected/);
});

test('cached supplemental license text must match its reviewed SHA-256 pin', async () => {
  const base = await fixture();
  await mkdir(join(base, 'target/android-license-inputs'), { recursive: true });
  await writeFile(join(base, 'target/android-license-inputs/protobuf-v28.2-LICENSE'), 'Unexpected replacement text');
  await assert.rejects(fetchLicenseInputs(base), /SHA-256 mismatch/);
});
