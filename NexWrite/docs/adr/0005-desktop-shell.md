<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# ADR-0005: Desktop shell and capability tiers (planned)

Status: accepted for Task 0; future-surface choices remain provisional pending their stated evidence.
Date: 2026-10-19. Source: docs/inputs/operator-brief.md (E4).

## Context

The future desktop assistant needs a shared editor plus narrowly scoped OS accessibility helpers. Overlay and in-place editing support varies by compositor, host app and permission state.

## Options

1. Electron shell and JS-specific native add-ons: large runtime, different native interface ownership.
2. Independent native apps on each OS: strongest platform fit but greater duplicated UI/contracts.
3. Tauri 2 with Rust capability traits and original React UI: shared local core with explicit native helpers.

## Decision

Choose Tauri 2 provisionally, pin exact dependencies at M4. Platform traits represent focused text/selection/geometry/editing, overlay, hotkeys, permissions and app rules. Tier A is inline/edit, Tier B pill/panel, Tier C selection/clipboard fallback. Skip elevated unsupported fields explicitly. No DLL injection or process hooks. GNOME Wayland defaults to panel mode until permitted capabilities are measured.

## Consequences

Task 0 contains no functioning desktop host, accessibility permission wizard, overlay, clipboard replacement or signed updater. Platform UIA/AX/AT-SPI, compositor geometry, clipboard restoration and update signing remain runtime obligations. Tier descriptions are expectations, not compatibility assertions.

## Revisit trigger

SP-02/03/04 evidence, resource budgets, store constraints or a shell security/support issue can require a native-shell alternative or reduced capability tier.
