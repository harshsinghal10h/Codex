// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { isIP } from "node:net";
import { fileURLToPath } from "node:url";

export const VERSION = "0.2.0-preview.1";
export interface ProviderConfig {
  id: "openai" | "gemini"; label: string; url: string; key?: string; models: string[];
}
export interface ApiConfig {
  host: string; port: number; token?: string; origins: string[]; cli: string;
  providers: ProviderConfig[]; allowPrivateUpstream: boolean;
  providerTimeoutMs: number; analysisTimeoutMs: number; rateLimit: number;
}
export function isLoopback(host: string): boolean {
  return host === "127.0.0.1" || host === "::1" || host === "localhost";
}
function models(value: string | undefined, defaults: string[]): string[] {
  const result = value === undefined ? defaults : value.split(",").map(v => v.trim()).filter(Boolean);
  if (result.length > 20 || result.some(v => !/^[a-zA-Z0-9][a-zA-Z0-9._:/-]{0,127}$/.test(v))) {
    throw new Error("Invalid configured model allowlist");
  }
  return [...new Set(result)];
}
function integer(value: string | undefined, fallback: number, min: number, max: number): number {
  if (value === undefined) return fallback;
  if (!/^\d+$/.test(value)) throw new Error("Invalid numeric server configuration");
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number < min || number > max) throw new Error("Invalid numeric server configuration");
  return number;
}
export function loadConfig(env: NodeJS.ProcessEnv = process.env): ApiConfig {
  const config: ApiConfig = {
    host: env.NEXWRITE_HOST ?? "127.0.0.1",
    port: integer(env.NEXWRITE_PORT, 8787, 1, 65535),
    token: env.NEXWRITE_ACCESS_TOKEN || undefined,
    origins: (env.NEXWRITE_ALLOWED_ORIGINS ?? "").split(",").filter(Boolean),
    cli: env.NEXWRITE_CLI ?? fileURLToPath(new URL("../../../target/release/nw", import.meta.url)),
    allowPrivateUpstream: env.NEXWRITE_ALLOW_PRIVATE_UPSTREAM === "true",
    providerTimeoutMs: 30_000, analysisTimeoutMs: 15_000,
    rateLimit: integer(env.NEXWRITE_RATE_LIMIT, 60, 1, 600),
    providers: [
      { id: "openai", label: "OpenAI-compatible", url: env.NEXWRITE_OPENAI_URL ?? "https://api.openai.com/v1/chat/completions",
        key: env.NEXWRITE_OPENAI_KEY || undefined, models: models(env.NEXWRITE_OPENAI_MODELS, ["gpt-4.1-mini"]) },
      { id: "gemini", label: "Google Gemini", url: env.NEXWRITE_GEMINI_URL ?? "https://generativelanguage.googleapis.com/v1beta/models/",
        key: env.NEXWRITE_GEMINI_KEY || undefined, models: models(env.NEXWRITE_GEMINI_MODELS, ["gemini-2.5-flash"]) },
    ],
  };
  validateConfig(config);
  return config;
}

export function validateConfig(config: ApiConfig): void {
  if (!isLoopback(config.host) && (!config.token || config.token.length < 32)) {
    throw new Error("Nonloopback listener requires a configured access token of at least 32 characters");
  }
  if (config.token && (config.token.length < 32 || config.token.length > 4096 || /[\s\x00-\x1f\x7f]/.test(config.token))) {
    throw new Error("Invalid access token configuration");
  }
  for (const origin of config.origins) {
    const parsed = new URL(origin);
    if (origin !== parsed.origin || parsed.username || parsed.password ||
        (parsed.protocol !== "https:" && !(parsed.protocol === "http:" && isLoopback(parsed.hostname)))) {
      throw new Error("Configured CORS origins must be exact HTTPS origins or local development origins");
    }
  }
  if (config.origins.includes("*")) throw new Error("Wildcard CORS is not supported");
  if (config.providers.length !== 2 || new Set(config.providers.map(p => p.id)).size !== 2) throw new Error("Invalid provider profiles");
  for (const provider of config.providers) {
    const url = new URL(provider.url);
    const host = url.hostname.replace(/^\[|\]$/g, "");
    if (url.username || url.password || url.search || url.hash ||
      (url.protocol !== "https:" && !(config.allowPrivateUpstream && url.protocol === "http:" && isLoopback(host)))) {
      throw new Error("Provider endpoints require HTTPS without credentials, query or fragment");
    }
    if (!config.allowPrivateUpstream && (isLoopback(host) || isIP(host) || !host.includes(".") || host.endsWith(".local"))) {
      throw new Error("Private upstream requires explicit self-host configuration");
    }
    if (provider.key && (provider.key.length > 4096 || /[\r\n\x00]/.test(provider.key))) throw new Error("Invalid provider key configuration");
    models(provider.models.join(","), []);
  }
}
