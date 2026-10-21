<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# ADR-0013: First Android engine transport

Status: accepted, native/host JVM evidence obtained; device evidence separate.
Date: 2026-10-21. References: ADR-0012, core/bridge/README.md.

## Context

Android needs the existing Rust engine and canonical source offsets. The first
release must preserve Unicode and avoid introducing a second rules engine. The
preferred generated bridge would expand the reviewed dependency/license scope.

## Options

1. Generate UniFFI bindings now and complete its additional runtime intake.
2. Duplicate analysis in Kotlin, creating another rules and span implementation.
3. Use a small pinned JNI byte-array adapter around the canonical JSON result.

## Decision

Choose option 3 for this preview. jni 0.21.1 is MIT OR Apache-2.0, with JVM
invocation features disabled. Strict UTF-8 byte arrays avoid modified UTF-8 and
CESU-8 conversions. Kotlin rejects unpaired UTF-16 surrogates; Rust validates
bytes, settings and versions before invoking Engine::analyze. Errors contain
fixed categories, never source. The bridge serializes the unchanged canonical
AnalysisResult and has no network or storage path.

Six native boundary/parity tests and an actual host JVM -Xcheck:jni smoke passed.
The three Android ABI builds use NDK 27.2.12479018 and 16 KiB ELF page alignment.
Host execution does not establish Android-device correctness or performance.

## Consequences and revisit

Android uses bounded serialized background analysis, discards canceled/stale
results and preserves the JNI symbol under minification. Cancellation cannot
interrupt Rust that is already executing. The UI bounds documents to 64 KiB and
IME selections to 16 KiB; the canonical engine cap remains 1 MiB. Revisit UniFFI
after a compatible dependency review and measurements across Android and Apple
hosts; never call this adapter generated UniFFI support.
