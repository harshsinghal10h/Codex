// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { readFileSync } from "node:fs";
import Ajv2020 from "ajv/dist/2020.js";
import { ApiError } from "./errors.js";

export function scalarString(value: string): boolean {
  for (let index = 0; index < value.length; index++) {
    const code = value.charCodeAt(index);
    if (code >= 0xd800 && code <= 0xdbff) {
      const next = value.charCodeAt(++index);
      if (!(next >= 0xdc00 && next <= 0xdfff)) return false;
    } else if (code >= 0xdc00 && code <= 0xdfff) return false;
  }
  return true;
}
const root = new URL("../", import.meta.resolve("@nexwrite/protocol"));
const read = (path: string) => JSON.parse(readFileSync(new URL(path, root), "utf8"));
const ajv = new Ajv2020.default({ strict: true, allErrors: false, validateFormats: false });
ajv.addKeyword({ keyword: "x-spdx", schemaType: "string", valid: true });
ajv.addKeyword({ keyword: "x-unicode-scalar-string", type: "string", schemaType: "boolean", validate: (enabled: boolean, value: string) => !enabled || scalarString(value) });
ajv.addKeyword({ keyword: "x-utf8-max-bytes", type: "string", schemaType: "number", validate: (maximum: number, value: string) => Buffer.byteLength(value, "utf8") <= maximum });
ajv.addSchema(read("schema/suggestion.schema.json"));
ajv.addSchema(read("schema/analysis.schema.json"));
ajv.addSchema(read("api-generated/validation.schema.json"));
const validators = new Map<string, ReturnType<typeof ajv.compile>>();
export function valid(name: string, value: unknown): boolean {
  let validate = validators.get(name);
  if (!validate) {
    validate = ajv.compile({ $ref: name === "AnalysisResult" ? "https://nexwrite.local/schema/analysis.schema.json" : `https://nexwrite.local/schema/owned-api.schema.json#/$defs/${name}` });
    validators.set(name, validate);
  }
  return !!validate(value);
}
export function validateRequest(name: "AnalyzeRequest" | "RewriteRequest", body: unknown): void {
  if (body === null || typeof body !== "object" || Array.isArray(body)) throw new ApiError(400, "invalid_request", "Invalid request");
  const object = body as Record<string, unknown>;
  if (typeof object.text === "string") {
    if (!scalarString(object.text)) throw new ApiError(400, "invalid_unicode", "Text must contain valid Unicode");
    if (Buffer.byteLength(object.text) > (name === "AnalyzeRequest" ? 1_048_576 : 32_768)) throw new ApiError(413, "text_too_large", "Text exceeds the input limit");
  }
  if (name === "RewriteRequest" && object.cloud_consent !== true) throw new ApiError(403, "cloud_consent_required", "Confirm the destination before sending text");
  if (object.locale !== undefined && object.locale !== "en-US") throw new ApiError(422, "unsupported_locale", "Unsupported locale");
  if (name === "AnalyzeRequest" && object.input_format !== undefined && !["plain", "markdown"].includes(String(object.input_format))) throw new ApiError(422, "unsupported_format", "Unsupported input format");
  if (name === "RewriteRequest") {
    if (object.task !== undefined && !["rewrite", "summarize", "compose"].includes(String(object.task))) throw new ApiError(422, "unsupported_task", "Unsupported writing task");
    if (object.preset !== undefined && !["proofread", "clearer", "shorten", "expand", "formal", "friendly", "direct"].includes(String(object.preset))) throw new ApiError(422, "unsupported_preset", "Unsupported writing preset");
    if (["summarize", "compose"].includes(String(object.task)) && object.preset !== "clearer") throw new ApiError(422, "unsupported_preset", "Unsupported preset for this task");
    if (typeof object.text === "string" && !object.text.trim()) throw new ApiError(400, "empty_text", "Enter text or a brief first");
  }
  if (!valid(name, body)) throw new ApiError(400, "invalid_request", "Invalid request");
}
