<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# ADR-0004: Browser extension framework (planned)

Status: accepted for Task 0; future-surface choices remain provisional pending their stated evidence.
Date: 2026-10-19. Source: docs/inputs/operator-brief.md (E4).

## Context

The roadmap targets Chromium, Firefox and Safari under MV3 with least-privilege access. No extension app or permissions are implemented in Task 0.

## Options

1. Handwritten independent browser manifests and build pipelines: maximum control, high drift risk.
2. Plasmo: integrated framework with a different abstraction and compatibility profile.
3. WXT: TypeScript-first multi-browser build model with explicit manifests and adapter tests.

## Decision

Choose WXT provisionally for the future extension milestone, subject to SP-07 and SP-08. Keep the core in a Worker/offscreen path where supported; capability-detect browser APIs and request optional site access. No remote executable code. Honor sensitive fields, composition, spellcheck=false and the project's own ignore marker. No third-party source/bundle inspection is needed to implement original fixtures.

## Consequences

No framework package, extension zip, site adapter or store readiness is asserted now. Framework, permissions and license versions must be pinned/reviewed when installed. Real OSS editor fixtures and browser conformance tests precede claims. Safari limitations and service-worker lifetime remain open, not inherited from a framework marketing claim.

## Revisit trigger

SP-07/08 evidence, a framework license/support issue or a documented browser capability regression can change the decision before L4 implementation.
