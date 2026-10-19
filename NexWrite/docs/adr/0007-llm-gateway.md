<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# ADR-0007: Optional LLM gateway boundary (planned)

Status: accepted for Task 0; future-surface choices remain provisional pending their stated evidence.
Date: 2026-10-19. Source: docs/inputs/operator-brief.md (E4).

## Context

Future composition/rewrite features need multiple providers while treating documents as untrusted data. No real provider integration is part of Task 0 and no credentials are requested.

## Options

1. Call providers directly from every client: easier demos, scattered consent/keys/budgets.
2. One provider hardwired into analysis: simple but vendor-specific and harms offline behavior.
3. Opt-in gateway with provider adapters, schema-validated outputs and bounded budgets/cancellation.

## Decision

Choose a future gateway supporting NIM/OpenAI-compatible, Gemini, OpenAI, Anthropic and compatible local endpoints as separate adapters. Route by declared capability/latency/cost only after measured evidence. Use versioned prompt/input/output schemas, bounded validation/retries, cancellation, explicit budgets and short-lived encrypted content-hash cache where justified. User text stays delimited untrusted data; analysis prompts cannot reach tools. Local endpoints are also opt-in capability choices, not silently activated services.

## Consequences

No provider adapter or simulated response is called live. Provider terms/retention/regions and secret storage need review first (UNK-RISK-04). Prompt injection tests, content-free diagnostics and safe fallback are required when implemented. A mock may test protocol behavior only when prominently labeled simulated.

## Revisit trigger

A provider data-policy change, failed injection/privacy evaluation, measured cost/SLO issue or better local capability can change routing/availability in a reviewed ADR.
