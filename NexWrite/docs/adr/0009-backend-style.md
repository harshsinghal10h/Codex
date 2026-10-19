<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# ADR-0009: Modular-monolith backend and optional local services (planned)

Status: accepted for Task 0; future-surface choices remain provisional pending their stated evidence.
Date: 2026-10-19. Source: docs/inputs/operator-brief.md (E4).

## Context

The complete roadmap includes auth/docs/teams/billing/LLM services, but the Task 0 core has no backend runtime dependency. Prematurely splitting services would multiply security, deployment and contract obligations.

## Options

1. Many independently deployed domain microservices immediately: isolation but high operational/authentication cost.
2. One monolith including CPU-heavy native analysis: simpler topology but mixed workload scaling.
3. TypeScript modular-monolith API plus stateless Rust analysis and a worker, extracting services only on evidence.

## Decision

Choose a future Fastify TypeScript modular monolith with domain boundaries, Postgres 16 and Drizzle; separate stateless Rust analysis via documented contracts and an async worker. Prefer NATS JetStream for durable event boundaries, Redis for bounded ephemeral/cache needs and S3-compatible object storage. OpenAPI 3.1 and problem+json define HTTP contracts. Optional compose declares Postgres/Redis/MinIO/NATS/Mailpit/OTel/Grafana for development, not a production deployment.

## Consequences

No auth, database migration, queue consumer, invoice processing, live telemetry or API endpoint is implemented by this ADR. Security gates include tenant authorization, retention/encryption, SSRF, rate limits, idempotency, backup/restore and failover evidence. Adding compose files is not proof of running services. Exact framework/service versions are reviewed/pinned at implementation.

## Revisit trigger

Measured load/SLO/security boundaries or deployment ownership justify extracting a service; actual ORM/queue constraints may change details before backend code lands.
