// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { spawnSync } from 'node:child_process';
import { mkdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = fileURLToPath(new URL('../../../', import.meta.url));
const javaTool = (name) => process.env.JAVA_HOME
  ? path.join(process.env.JAVA_HOME, 'bin', name)
  : name;

function run(command, args, capture = false) {
  const result = spawnSync(command, args, {
    cwd: projectRoot,
    encoding: 'utf8',
    stdio: capture ? ['ignore', 'pipe', 'inherit'] : 'inherit',
  });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`JNI smoke command failed: ${command}`);
  return result.stdout;
}

run('cargo', ['build', '--locked', '-p', 'nw-bridge']);
const metadata = JSON.parse(run('cargo', ['metadata', '--locked', '--no-deps', '--format-version', '1'], true));
const classes = path.join(metadata.target_directory, 'bridge-jvm-classes');
mkdirSync(classes, { recursive: true });
run(javaTool('javac'), [
  '-encoding', 'UTF-8', '-d', classes,
  'core/bridge/tests/jvm/app/nexwrite/core/NativeBridge.java',
]);
run(javaTool('java'), [
  '-Xcheck:jni',
  `-Djava.library.path=${path.join(metadata.target_directory, 'debug')}`,
  '-cp', classes,
  'app.nexwrite.core.NativeBridge',
]);
