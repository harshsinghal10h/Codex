<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# ADR-0003: Rust core and schema-first native/WASM contracts

Status: accepted for Task 0; future-surface choices remain provisional pending their stated evidence.
Date: 2026-10-19. Source: docs/inputs/operator-brief.md (E4).

## Context

Clients need equivalent suggestions without computing offsets themselves. S5.4 calls JSON Schema the source of truth; S4 also mentions schema generation from Rust. The bootstrap must choose one authoritative direction and avoid drift.

## Options

1. Independent TypeScript/Kotlin/Swift engines and contracts: fast surface starts but divergent Unicode/rule behavior.
2. Rust-owned types with separately maintained JSON/client definitions: familiar native code but multiple sources of truth.
3. Canonical JSON Schema with generated Rust/TS/Kotlin/Swift types and one Rust engine exposed through native CLI/WASM.

## Decision

Use packages/protocol/schema/suggestion.schema.json as canonical (the explicit S5.4 contract), generate Rust and client declarations with tools/codegen, and check regeneration. Build nw-text, nw-schema, nw-rules, nw-engine, nw-cli and nw-wasm. Canonical text is valid UTF-8, emitting half-open UTF-8 byte, UTF-16 code-unit and Unicode-scalar spans. Use wasm-bindgen 0.2.129 for the tested Node bootstrap. Future UniFFI bridges require real consuming builds before claiming mobile integration.

## Consequences

One original double-space rule exercises the full contract; this is neither a complete tokenizer nor a structured-text grammar engine. Schema-first generation deliberately resolves the brief's direction ambiguity. Generated client files prove declarations, not compiler compatibility. Native and WASM parity tests must instantiate actual compiled code, include Unicode cases and preserve document versions.

## Revisit trigger

If the schema cannot express required invariants, generated client compilation fails, or native bridge/performance evidence requires a different contract, revisit in a contract-first PR.
