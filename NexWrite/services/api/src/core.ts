// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { access, chmod, mkdtemp, writeFile, rm } from "node:fs/promises";
import { constants } from "node:fs";
import { spawn } from "node:child_process";
import { tmpdir } from "node:os";
import { join } from "node:path";
import type { AnalysisResult } from "@nexwrite/protocol";
import type { AnalyzeRequest } from "@nexwrite/protocol/api";
import { ApiError, aborted } from "./errors.js";
import { valid } from "./validation.js";

async function execute(cli: string, args: string[], signal: AbortSignal, maxBytes: number): Promise<unknown> {
  aborted(signal);
  return new Promise((resolve, reject) => {
    const child = spawn(cli, args, { stdio: ["ignore", "pipe", "ignore"], windowsHide: true });
    const chunks: Buffer[] = [];
    let size = 0, settled = false;
    function finish(error?: ApiError, result?: unknown): void {
      if (settled) return;
      settled = true;
      signal.removeEventListener("abort", cancel);
      if (error) reject(error); else resolve(result);
    }
    function cancel(): void {
      child.kill("SIGKILL");
      finish(new ApiError(499, "request_canceled", "Request canceled"));
    }
    signal.addEventListener("abort", cancel, { once: true });
    child.stdout.on("data", (chunk: Buffer) => {
      size += chunk.byteLength;
      if (size > maxBytes) {
        child.kill("SIGKILL");
        finish(new ApiError(503, "analysis_output_limit", "Analysis exceeds the delivery limit"));
      } else chunks.push(chunk);
    });
    child.on("error", () => finish(new ApiError(503, "engine_unavailable", "Local analysis engine is unavailable")));
    child.on("close", code => {
      if (settled) return;
      if (code !== 0) return finish(new ApiError(503, "engine_failed", "Local analysis could not finish"));
      try { finish(undefined, JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(Buffer.concat(chunks)))); }
      catch { finish(new ApiError(503, "invalid_engine_response", "Local analysis returned an invalid response")); }
    });
    if (signal.aborted) cancel();
  });
}

export async function discoverEngine(cli: string): Promise<{ available: boolean; rule_count: number }> {
  try {
    await access(cli, constants.X_OK);
    const result = await execute(cli, ["rules", "test"], AbortSignal.timeout(5_000), 16_384) as { rules?: unknown };
    if (Number.isSafeInteger(result.rules) && Number(result.rules) >= 0) return { available: true, rule_count: Number(result.rules) };
  } catch { /* Safe discovery discloses availability only. */ }
  return { available: false, rule_count: 0 };
}

export async function analyze(cli: string, request: AnalyzeRequest, signal: AbortSignal): Promise<AnalysisResult> {
  const directory = await mkdtemp(join(tmpdir(), "nexwrite-analysis-"));
  try {
    await chmod(directory, 0o700);
    const file = join(directory, "source.txt");
    await writeFile(file, request.text, { encoding: "utf8", mode: 0o600, flag: "wx" });
    const result = await execute(cli, ["analyze", file, "--format", "json", "--doc-version", String(request.doc_version), "--locale", request.locale, "--input-format", request.input_format], signal, 8_388_608);
    if (!valid("AnalysisResult", result)) throw new ApiError(503, "invalid_engine_response", "Local analysis returned an invalid response");
    const report = result as AnalysisResult;
    if (report.doc_version !== request.doc_version || report.locale !== request.locale || report.input_format !== request.input_format) throw new ApiError(503, "invalid_engine_response", "Local analysis returned an invalid response");
    return report;
  } finally { await rm(directory, { recursive: true, force: true }); }
}
