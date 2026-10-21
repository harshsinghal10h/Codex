// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const productionPolicy = "default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self'; font-src 'self'; img-src 'self' data:; connect-src 'self'; worker-src 'self'; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

export default defineConfig(({ command }) => ({
  plugins: [react(), {
    name: 'production-local-policy',
    transformIndexHtml: command === 'build' ? () => [{ tag: 'meta', attrs: { 'http-equiv': 'Content-Security-Policy', content: productionPolicy.replace("; frame-ancestors 'none'", '') }, injectTo: 'head' }] : undefined,
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
