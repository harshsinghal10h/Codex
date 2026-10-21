<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# ADR-0002: Monorepo, task runner and bootstrap scope

Status: accepted for Task 0; future-surface choices remain provisional pending their stated evidence.
Date: 2026-10-19. Source: docs/inputs/operator-brief.md (E4).

## Context

The existing Codex repository contains other projects. The new product needs durable governance and disjoint lanes without replacing them. S15 explicitly asks for a single cross-lane Task 0 deliverable while S2 ordinarily separates specification and implementation PRs.

## Options

1. Replace the current repository with a new product layout: conflicts with existing work and loses context.
2. Create a second Git repository or a worktree: unnecessary and outside the existing-checkout workflow.
3. Use a self-contained NexWrite directory, pnpm workspace/Turbo and Cargo workspace coordinated by just.

## Decision

Keep NexWrite under the current checkout; preserve sibling projects. Use pnpm 12.14.0, Turbo 2.11.9, Cargo workspace and just. just commands use build <surface> because colon recipe names are not supported. Task 0 is the explicit single-deliverable bootstrap exception: analyst writes original docs/spec/bootstrap-core.md before a separate builder implements its minimal rule. Later feature specifications and implementations retain separate tasks/PRs; no general exception is implied. Preserve MASTER_PROMPT verbatim as archival input, excluded from product-source brand scanning. The scanner itself may hold forbidden-pattern literals needed to test detection.

## Consequences

This interpretation is disclosed rather than claiming literal consistency between the brief's conflicting clauses. The coordinating root owns shared docs/contracts; subsequent slices use lane ownership and at most 12 feature rows. Cloud tasks use the existing isolated checkout, no worktree unless the user asks. Generated files/config are distinguished from handwritten code for the Task 0 line budget.

## Revisit trigger

A concrete repository split request, measured orchestration cost or cross-lane contract conflict can reopen the structure. The Task 0 exception ends when this deliverable is complete.
