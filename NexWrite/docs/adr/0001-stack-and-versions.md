<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# ADR-0001: Stack and reproducible toolchain

Status: accepted for Task 0; future-surface choices remain provisional pending their stated evidence.
Date: 2026-10-19. Source: docs/inputs/operator-brief.md (E4).

## Context

The operator requests a Rust/TypeScript monorepo with native, WASM and future platform clients. Task 0 must build a real local slice rather than install every future platform tool. Version claims must follow installed tool output and locked metadata.

## Options

1. Unpinned system tools and moving latest releases: quick setup, irreproducible results.
2. A container-only toolchain for every command: consistent image, higher startup/dependency cost and no macOS/iOS parity.
3. Pinned native tools plus a devcontainer and committed lockfiles: reproducible bootstrap with a direct local workflow.

## Decision

Choose pinned native tools with a matching devcontainer. Rust 1.99.1 with rustfmt/clippy and wasm32-unknown-unknown; Node 24.19.0; pnpm 12.14.0; Python 3.12.14; just 1.59.0; wasm-bindgen library/CLI 0.2.129; gitleaks 8.30.1; cargo-deny 0.20.2; REA 6.4.0 as an external tool. Cargo.lock and pnpm-lock.yaml pin resolved dependencies. Postgres 16 is the future database major required by the brief. Kotlin/Swift, Gradle, Android, Apple signing and platform build versions are pinned when their milestone is implemented, not declared installed from roadmap text. See actual setup evidence for observed versions.

## Consequences

Native core works without Docker services. A local activation script outside the checkout can supply cloud tools without mutating protected sibling projects. Tool upgrades require regeneration/checks; a devcontainer declaration is not proof that it was built. No unexecuted platform build is called verified.

## Revisit trigger

A required security fix, unsupported platform/toolchain, upstream stable transition or reproducibility mismatch warrants a reviewed pin update and rerun of affected checks.
