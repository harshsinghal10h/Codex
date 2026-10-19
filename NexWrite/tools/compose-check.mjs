// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { randomBytes } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { root } from './lint-common.mjs';
const env = { ...process.env };
for (const key of ['POSTGRES_PASSWORD', 'REDIS_PASSWORD', 'MINIO_ROOT_PASSWORD', 'GRAFANA_ADMIN_PASSWORD']) env[key] = randomBytes(32).toString('hex');
const result = spawnSync('docker', ['compose', '--env-file', 'infra/compose/.env.example', '-f', 'infra/compose/compose.yaml', '--profile', 'backend', '--profile', 'observability', 'config', '--format', 'json'], { cwd: root, env, encoding: 'utf8' });
if (result.error || result.status !== 0) {
  console.error(`compose-check: Docker Compose is required and configuration must parse (${result.error?.message ?? result.stderr.trim()})`);
  process.exitCode = 1;
} else {
  const config = JSON.parse(result.stdout);
  const expected = ['postgres', 'redis', 'minio', 'nats', 'mailpit', 'otel-collector', 'grafana'];
  for (const name of expected) {
    if (!config.services[name]) throw new Error(`Missing development service ${name}`);
  }
  for (const [name, service] of Object.entries(config.services))
    for (const port of service.ports ?? []) if (port.host_ip !== '127.0.0.1') throw new Error(`${name} publishes beyond loopback`);
  console.log('compose-check: seven optional service configurations parse and bind published ports to loopback; containers were not started.');
}
