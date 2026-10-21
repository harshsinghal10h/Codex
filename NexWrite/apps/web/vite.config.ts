// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { createHash } from 'node:crypto';
import { readdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';

const productionPolicy = "default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self'; font-src 'self'; img-src 'self' data:; connect-src 'self' https:; worker-src 'self'; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

export default defineConfig(({ command }) => ({
  plugins: [react(), {
    name: 'production-local-policy',
    transformIndexHtml: command === 'build' ? () => [{ tag: 'meta', attrs: { 'http-equiv': 'Content-Security-Policy', content: productionPolicy.replace("; frame-ancestors 'none'", '') }, injectTo: 'head' }] : undefined,
  }, {
    name: 'static-only-offline-shell',
    apply: 'build',
    async closeBundle() {
      const output = path.resolve(import.meta.dirname, 'dist');
      async function inventory(directory: string): Promise<string[]> {
        const entries = await readdir(directory, { withFileTypes: true });
        const nested = await Promise.all(entries.filter((entry) => !entry.name.startsWith('.') && entry.name !== 'sw.js' && !entry.name.endsWith('.d.ts')).map(async (entry) => entry.isDirectory() ? inventory(path.join(directory, entry.name)) : [path.join(directory, entry.name)]));
        return nested.flat().sort();
      }
      const files = await inventory(output);
      const hash = createHash('sha256');
      for (const filename of files) { hash.update(path.relative(output, filename)); hash.update(await readFile(filename)); }
      const urls = files.map((filename) => '/' + path.relative(output, filename).split(path.sep).join('/'));
      await writeFile(path.join(output, 'sw.js'), `// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
// Only the immutable build inventory is cached. No API, token or document data.
const CACHE = 'nexwrite-shell-${hash.digest('hex').slice(0, 20)}';
const FILES = ${JSON.stringify(urls)};
const STATIC = new Set(FILES);
self.addEventListener('install', event => event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(FILES))));
self.addEventListener('activate', event => event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key.startsWith('nexwrite-shell-') && key !== CACHE).map(key => caches.delete(key)))).then(() => self.clients.claim())));
self.addEventListener('fetch', event => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== 'GET' || url.origin !== self.location.origin || url.search || request.headers.has('authorization')) return;
  const key = url.pathname === '/' ? '/index.html' : url.pathname;
  if (!STATIC.has(key)) return;
  event.respondWith(caches.open(CACHE).then(cache => cache.match(key)).then(hit => hit || fetch(request)));
});
`);
    },
  }],
  server: { host: '0.0.0.0' },
  preview: {
    host: '127.0.0.1',
    headers: {
      'Content-Security-Policy': productionPolicy,
      'Referrer-Policy': 'no-referrer',
      'X-Content-Type-Options': 'nosniff',
    },
  },
  build: { sourcemap: false, assetsInlineLimit: 0 },
  worker: { format: 'es' },
}));
