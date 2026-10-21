// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import Fastify, { type FastifyReply, type FastifyRequest } from "fastify";
import { createHash, randomUUID, timingSafeEqual } from "node:crypto";
import type { AiResult, AnalyzeRequest, ApiProblem, Capabilities, RewriteRequest } from "@nexwrite/protocol/api";
import { loadConfig, validateConfig, VERSION, type ApiConfig } from "./config.js";
import { analyze, discoverEngine } from "./core.js";
import { ApiError } from "./errors.js";
import { rewrite } from "./providers.js";
import { valid, validateRequest } from "./validation.js";

export async function buildApp(config: ApiConfig = loadConfig()) {
  validateConfig(config);
  const app = Fastify({ logger: false, bodyLimit: 2_097_152,
    requestTimeout: 35_000, connectionTimeout: 10_000, keepAliveTimeout: 5_000,
    trustProxy: false, genReqId: () => randomUUID(), routerOptions: { maxParamLength: 256 } });
  const engine = await discoverEngine(config.cli);
  const tokenDigest = config.token ? createHash("sha256").update(config.token).digest() : undefined;
  const limits = new Map<string, { count: number; until: number }>();
  let globalCount = 0, globalUntil = 0, activeAnalysis = 0, activeProvider = 0;
  const operations = new Set<AbortController>();

  app.removeContentTypeParser("application/json");
  app.addContentTypeParser("application/json", { parseAs: "buffer", bodyLimit: 2_097_152 }, (_request, body, done) => {
    try { done(null, JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(body as Buffer))); }
    catch { done(new ApiError(400, "invalid_json", "Request must contain valid UTF-8 JSON")); }
  });
  app.addHook("onRequest", async (request, reply) => {
    reply.header("cache-control", "no-store").header("x-content-type-options", "nosniff");
    const origin = request.headers.origin;
    if (origin) {
      reply.header("vary", "Origin");
      if (!config.origins.includes(origin)) throw new ApiError(403, "origin_not_allowed", "This browser origin is not allowed");
      reply.header("access-control-allow-origin", origin);
    }
    const now = Date.now();
    if (now >= globalUntil) { globalUntil = now + 60_000; globalCount = 0; }
    if (request.method === "POST") {
      if (tokenDigest) {
        const header = request.headers.authorization;
        const supplied = typeof header === "string" && header.startsWith("Bearer ") && header.length <= 4103 ? header.slice(7) : "";
        if (!timingSafeEqual(tokenDigest, createHash("sha256").update(supplied).digest())) throw new ApiError(401, "unauthorized", "A valid access token is required");
      }
      let entry = limits.get(request.ip);
      if (!entry || now >= entry.until) {
        if (limits.size >= 10_000) {
          for (const [key, value] of limits) if (now >= value.until) limits.delete(key);
          if (limits.size >= 10_000) throw new ApiError(429, "rate_limited", "Request limit reached", 60);
        }
        entry = { count: 0, until: now + 60_000 };
        limits.set(request.ip, entry);
      }
      if (++entry.count > config.rateLimit || ++globalCount > config.rateLimit * 2) throw new ApiError(429, "rate_limited", "Request limit reached", Math.max(1, Math.ceil((entry.until - now) / 1000)));
    }
  });
  app.setErrorHandler((error, request, reply) => {
    const known = error && typeof error === "object" ? error as { code?: string; statusCode?: number } : {};
    const problem = error instanceof ApiError ? error :
      known.code === "FST_ERR_CTP_BODY_TOO_LARGE" ? new ApiError(413, "body_too_large", "Request exceeds the body limit") :
      known.statusCode === 415 ? new ApiError(400, "invalid_content_type", "Send application/json") :
      known.statusCode === 400 ? new ApiError(400, "invalid_request", "Invalid request") :
      new ApiError(503, "internal_error", "Request could not be completed");
    const status = problem.status === 499 ? 400 : problem.status;
    const body: ApiProblem = { type: `urn:nexwrite:problem:${problem.code}`, title: problem.title,
      status: status as ApiProblem["status"], code: problem.code, request_id: request.id,
      ...(problem.retryAfter === undefined ? {} : { retry_after_seconds: problem.retryAfter }) };
    if (problem.retryAfter !== undefined) reply.header("retry-after", String(problem.retryAfter));
    reply.code(status).type("application/problem+json").send(body);
  });
  app.setNotFoundHandler((_request, _reply) => { throw new ApiError(404, "not_found", "Route not found"); });
  app.options("/*", async (request, reply) => {
    if (!request.headers.origin || !config.origins.includes(request.headers.origin)) throw new ApiError(403, "origin_not_allowed", "This browser origin is not allowed");
    reply.header("access-control-allow-methods", "GET, POST, OPTIONS")
      .header("access-control-allow-headers", "authorization, content-type")
      .header("access-control-max-age", "600").code(204).send();
  });
  app.get("/health", async () => ({ status: "ok" as const, version: VERSION }));
  app.get("/v1/capabilities", async (): Promise<Capabilities> => ({ protocol_version: 1, version: VERSION,
    engine: { ...engine, locales: ["en-US"], input_formats: ["plain", "markdown"] },
    tasks: ["rewrite", "summarize", "compose"],
    providers: config.providers.map(provider => ({ id: provider.id, label: provider.label, configured: !!provider.key && provider.models.length > 0, models: [...provider.models] })),
    accounts: false, cloud_docs: false, auth_required: !!config.token,
  }));

  async function operation<T>(request: FastifyRequest, reply: FastifyReply, timeout: number, action: (signal: AbortSignal) => Promise<T>): Promise<T> {
    const controller = new AbortController();
    operations.add(controller);
    let timedOut = false;
    const timer = setTimeout(() => { timedOut = true; controller.abort(); }, timeout);
    const cancel = () => controller.abort();
    request.raw.once("aborted", cancel);
    const close = () => { if (!reply.raw.writableEnded) cancel(); };
    reply.raw.once("close", close);
    try { return await action(controller.signal); }
    catch (error) { if (timedOut) throw new ApiError(504, "request_timeout", "Request timed out"); throw error; }
    finally { clearTimeout(timer); request.raw.off("aborted", cancel); reply.raw.off("close", close); operations.delete(controller); }
  }
  app.post("/v1/analyze", async (request, reply) => {
    validateRequest("AnalyzeRequest", request.body);
    if (!engine.available) throw new ApiError(503, "engine_unavailable", "Local analysis engine is unavailable");
    if (activeAnalysis >= 2) throw new ApiError(429, "analysis_busy", "Analysis is busy", 1);
    activeAnalysis++;
    try { return await operation(request, reply, config.analysisTimeoutMs, signal => analyze(config.cli, request.body as AnalyzeRequest, signal)); }
    finally { activeAnalysis--; }
  });
  app.post("/v1/rewrite", async (request, reply) => {
    validateRequest("RewriteRequest", request.body);
    const body = request.body as RewriteRequest;
    const provider = config.providers.find(profile => profile.id === body.provider);
    if (!provider) throw new ApiError(422, "unsupported_provider", "Unsupported provider profile");
    if (!provider.models.includes(body.model)) throw new ApiError(422, "unsupported_model", "Unsupported model for this provider");
    if (!provider.key) throw new ApiError(503, "provider_not_configured", "Configure this provider on the backend first");
    if (activeProvider >= 4) throw new ApiError(429, "provider_busy", "Writing service is busy", 1);
    activeProvider++;
    try {
      const replacement = await operation(request, reply, config.providerTimeoutMs, signal => rewrite(provider, body, config, signal));
      const result: AiResult = { request_id: request.id, doc_version: body.doc_version, provider: body.provider,
        model: body.model, task: body.task, preset: body.preset, original: body.text, replacement, warnings: [] };
      if (!valid("AiResult", result)) throw new ApiError(502, "invalid_provider_response", "Provider returned an invalid proposal");
      return result;
    } finally { activeProvider--; }
  });
  app.addHook("onClose", async () => { for (const controller of operations) controller.abort(); operations.clear(); });
  return app;
}
