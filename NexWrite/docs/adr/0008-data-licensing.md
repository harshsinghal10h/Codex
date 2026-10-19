<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# ADR-0008: Dataset, dictionary and model provenance gate

Status: accepted for Task 0; future-surface choices remain provisional pending their stated evidence.
Date: 2026-10-19. Source: docs/inputs/operator-brief.md (E4).

## Context

The roadmap asks for dictionaries, GEC models and fairness evaluation, but commonly cited corpora may be research-only. A code repository's license need not cover text, dictionaries or model weights.

## Options

1. Use any public/downloadable corpus: rapid collection without reliable reuse rights.
2. Exclude all third-party data and models permanently: simple governance but unnecessarily limits lawful functionality.
3. Per-artifact license/privacy review, preferring original/public-domain/CC0/permissive text and synthetic errors.

## Decision

Choose a deny-until-reviewed data intake gate in docs/legal/DATASETS.md. Initial spacing/Unicode fixtures are original, with no imported corpus/model/dictionary. Future model cards must record source/license, metrics, dialect/genre/non-native fairness, limitations and approved purpose. No reference outputs for tests, training or distillation. No weights committed; release packs require checksums and license notices.

## Consequences

Task 0 cannot claim spelling/GEC/tone/AI-likelihood capability from a schema or dataset plan. Every dictionary/model distribution requires independent license and provenance review. An incompatible data discovery requires removal and reevaluation/retraining rather than a changed checksum or waived gate.

## Revisit trigger

SP-10 resolution, a dataset license/consent change, new jurisdictional constraint or discovered contamination requires reopening the intake decision and affected model evidence.
