// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { randomBytes } from 'node:crypto';
import fs from 'node:fs';
import { isMain } from '../../tools/lint-common.mjs';
export function initialize(destination = new URL('./.env', import.meta.url)) {
  const keys = ['POSTGRES_PASSWORD', 'REDIS_PASSWORD', 'MINIO_ROOT_PASSWORD', 'GRAFANA_ADMIN_PASSWORD'];
  const contents = '# Local development only. Do not commit or reuse in deployment.\n' + keys.map(key => `${key}=${randomBytes(32).toString('hex')}`).join('\n') + '\n';
  try {
    fs.writeFileSync(destination, contents, { flag: 'wx', mode: 0o600 });
    return 'Created ignored local credentials with independent cryptographic random values.';
  } catch (error) {
    if (error.code !== 'EEXIST') throw error;
    return 'Preserved existing local credential file.';
  }
}
if (isMain(import.meta.url)) console.log(initialize());
