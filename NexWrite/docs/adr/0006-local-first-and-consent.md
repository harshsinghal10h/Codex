<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# ADR-0006: Local-first text processing and explicit consent

Status: accepted for Task 0; future-surface choices remain provisional pending their stated evidence.
Date: 2026-10-19. Source: docs/inputs/operator-brief.md (E4).

## Context

User documents and keyboard input are sensitive. A working local grammar path must not depend on accounts or provider availability. The operator forbids content logging and training on user data.

## Options

1. Cloud analysis by default with a settings opt-out: broad capability but sends text before informed choice.
2. Entirely offline product with no optional cloud capability: strong default privacy but limits explicitly requested future workflows.
3. Local deterministic core by default with separate explicit per-feature/context cloud opt-in.

## Decision

Choose local-only default. The Task 0 engine/CLI/WASM have no analysis network path, telemetry or training. Caller-requested CLI JSON goes to stdout only. Future remote/LLM features require recipient/purpose/region/retention disclosure, consent records, revocation and an intact local path. Sensitive/private fields must be excluded on future host surfaces; per-site/app rules and local payload preview are explicit controls.

## Consequences

Build dependency downloads are not runtime analysis traffic. Diagnostics must omit content, and future replay/provenance content requires separate consent. A principles document is not GDPR/DPDP or store compliance certification. Tests must inspect actual traffic/logging and consent transitions when a remote implementation exists.

## Revisit trigger

A proposed new data flow, telemetry event, provider, sensitive host surface or retention purpose requires renewed privacy/threat review before implementation.
