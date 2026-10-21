<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# Owned Android preview release

The canonical APK is built from NexWrite Kotlin/Compose plus the shared Rust
engine. Apktool inspects and rebuilds a separate verification copy; that copy
never replaces the canonical signed Gradle output.

## Build and verify

Use the pinned toolchains from apps/android/README.md. From the NexWrite root:

```sh
pnpm install --frozen-lockfile
pnpm --filter @nexwrite/protocol build
bash tools/build-android-core.sh
bash apps/android/gradlew :app:runtimeComponentInventory
node tools/android-notices.mjs --fetch-license-inputs
bash apps/android/gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:assembleDebugAndroidTest
```

`--fetch-license-inputs` retrieves only a fixed, checksum-pinned upstream license
text needed by the shaded protobuf dependency. Runtime notices and inventory
must exist before packaging. Gradle verification metadata records checksums
observed over authenticated TLS; it is not an independent source-code audit.

Set `NEXWRITE_SIGNING_KEYSTORE` and `NEXWRITE_SIGNING_PASSWORD_FILE` to private
files outside the checkout, then invoke:

```sh
bash tools/sign-android.sh apps/android/app/build/outputs/apk/release/app-release-unsigned.apk /private/artifacts/NexWrite-0.2.0-preview.1-universal.apk
```

The script aligns native entries for 16 KiB pages and checks signatures,
certificate fingerprints and final alignment. Inspect every packaged native
library's ELF alignment, APK package/SDK/entry points, permissions, build size,
notices and absence of embedded provider credentials. Record the canonical
APK SHA-256 before handing it to the uploaded-source Apktool and REA tools.
Keep raw results and their limits; static audit is separate from emulator or
physical-device evidence. Instrumentation must run against the committed source
in supported CI; physical-device and assistive-technology claims require their
own evidence.

## Signing identity and publication

The signing keystore/password generated for this task are retained privately
under `/workspace/.toolchains/nexwrite/android-signing/`, outside Git. Back them
up to an operator-controlled private vault before replacing the cloud workspace.
Do not upload them as public release assets or print their contents. GitHub's
current integration denied Actions-secret access with HTTP403; no secret was
overwritten, no signing key was published and no token was requested. Future
signing automation requires an authorized private secret store.

Publish the exact verified APK plus checksums and bounded audit/build evidence
on the owned repository's release. Use an immutable source commit/tag and
`nexwrite-v0.2.0-preview.1` to avoid sibling-project tag collisions. Verify the
published asset by downloading it and comparing its SHA-256 and signing
certificate. Never substitute an upstream PDF application's artifact.

Release notes must explain that backend configuration is deferred by the user,
cloud AI is unavailable until configured, the local rule set is limited, and
native iOS/desktop/extensions plus the remaining full-stack roadmap are not
completed by this preview.
