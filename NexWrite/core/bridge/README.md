<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# Native Android analysis bridge

`nw-bridge` is the small JNI transport permitted by ADR-0012. It calls the real
`nw_engine::Engine::analyze` and serializes the canonical `AnalysisResult` with
Serde. It has no alternate rules, duplicated wire model, network, storage or
intentional document logging. The exact pinned `jni` 0.21.1 dependency is
MIT/Apache-2.0; its invocation/embedded-JVM features are disabled. This initial
adapter does not generate or ship UniFFI bindings. Revisit generated bindings
when the reviewed dependency scope and measured platform needs justify them.

Include `core/bridge/kotlin` in Android's main Kotlin source directories. The
original wrapper is `app.nexwrite.core.NativeBridge`; it loads `nw_bridge` and
exposes:

```kotlin
NativeBridge.analyzeJson(text, docVersion, locale = "en-US", inputFormat = "plain")
```

This synchronous method must run on `Dispatchers.Default`, with serialized work
and version/cancellation checks around delivery. Canceling a coroutine does not
interrupt a Rust call already running. Discard its result when the request is
canceled or the document changed. Catch a missing-library/error state and show
analysis unavailable; never invent a result or upload source as a fallback.

`sourceUtf8Bytes(text, maximumUtf8Bytes)` rejects unpaired UTF-16 surrogates before
the JVM encoder can replace them, counts exact UTF-8 bytes before allocation and
never normalizes/truncates. The UI must disclose and enforce
`EDITOR_MAX_UTF8_BYTES` (65,536) and `IME_MAX_UTF8_BYTES` (16,384), bounding IME
context before analysis. Core transport preserves the engine's 1,048,576-byte
cap and portable nonnegative version maximum 9,007,199,254,740,991. Only `en-US`
and exact `plain`/`markdown` formats are supported. Input arrays use strict Rust
UTF-8 decoding, not Java modified UTF-8 or CESU-8. Errors contain fixed category
messages without source/settings payloads.

The JNI ABI is the static method below. Kotlin callers should use the checked
String wrapper; direct byte callers must already supply strict UTF-8:

```kotlin
@JvmStatic external fun analyzeUtf8(
    text: ByteArray, docVersion: Long, locale: ByteArray, inputFormat: ByteArray,
): ByteArray
```

The exported symbol is
`Java_app_nexwrite_core_NativeBridge_analyzeUtf8`. Preserve this class/method
identity when enabling minification. Build `nw-bridge` as a `cdylib` with the
Android NDK linker for each Rust Android target, and copy `libnw_bridge.so` into
`target/android-jniLibs/<abi>/`. Gradle can consume that root as `jniLibs`.
No code generation or Java runtime shared library is required on Android.
The pure Rust API is `nw_bridge::analyze_utf8(&[u8], i64, &[u8], &[u8])`.

Run from the NexWrite root with the repository's pinned Rust and Java 17 tools:

```sh
cargo test --locked -p nw-bridge
node core/bridge/tools/jvm-smoke.mjs
```

The Rust tests compare actual bridge output to direct engine output for Unicode,
CRLF, Markdown protection, original offsets, maximum version and strict failure
cases. The JVM smoke builds and loads the actual host shared library under
`-Xcheck:jni`, exercises its exact native symbol and verifies recovery after
malformed/null/size/version errors. Host JVM results do not establish Android
device execution. Cross-compilation, installed APK/runtime, Kotlin UI lifecycle,
IME secure-field exclusions and real-device performance need their own evidence.
The existing dense-result memory limit remains: the lower UI input limits bound
the first native surface but do not prove bounded end-to-end core report memory.
