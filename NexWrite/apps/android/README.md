<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# NexWrite Android preview

Native Kotlin/Compose document library, source editor and writing keyboard.
The interface uses original NexWrite surfaces, cards and navigation. No PDF
application, branding or signing identity is repackaged.

## Build

From the NexWrite root, install the pinned Rust/Node/Java 17/Android toolchains,
then run:

```sh
pnpm install --frozen-lockfile
pnpm --filter @nexwrite/protocol build
bash tools/build-android-core.sh
cd apps/android
./gradlew :app:runtimeComponentInventory
cd ../..
node tools/android-notices.mjs --fetch-license-inputs
cd apps/android
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:assembleDebugAndroidTest
```

The checked-in `gradlew` bootstraps Gradle 9.3.0 with its pinned SHA-256; no
third-party wrapper JAR is vendored. SDK 36, build-tools 36.1.0 and NDK
27.2.12479018 are required. Preserve your normal proxy and trusted CA settings;
Java may require its supported proxy system properties in a managed environment.
Generated native libraries and notices are read from the ignored `target`
directory. Packaging fails when required notice assets are absent.

The release build is unsigned until the coordinator runs
`bash tools/sign-android.sh` from the NexWrite root. Signing credentials stay
outside the repository. Never replace the app with a renamed upstream APK.

## Behavior and limits

- Documents start in memory. Save stores app-private Room records. Automatic
  saving is a separate setting, initially off. SAF import/export needs no broad
  storage permission. Replacing an unsaved draft requires confirmation.
- Local analysis uses the same Rust engine as native CLI/WASM through the
  versioned JNI adapter. This preview has one original spacing rule and labelled
  readability estimates; it is not the planned complete rule set.
- The editor bounds source at 64 KiB UTF-8; selected keyboard analysis is bounded
  at 16 KiB. The core's separate 1 MiB limit is not an app-size promise. Analysis
  runs off the main thread; synchronous native work is serialized per surface,
  and canceled/stale deliveries are discarded. Already-running native calls are
  not forcibly interrupted.
- AI starts unconfigured. Enter a self-hosted HTTPS backend and optional client
  access token in Settings. Tokens are encrypted using Android Keystore. Provider
  keys stay on the backend. Discover its real providers/models, preview the exact
  source/destination, then consent per request. Results are proposals with a
  document/version/selection guard and a bounded 20-edit undo history. Ordinary
  typing starts a new history boundary.
- The keyboard collects no history, learns nothing and makes no network call.
  Password, no-suggestions, incognito/private and nontext fields suppress checks
  and Assist. A selected correction is rechecked before replacement.
- `PROCESS_TEXT` respects read-only mode and returns a writable selection only
  after explicit Apply. Shared text is neither saved nor uploaded on arrival.
- Only the INTERNET permission is requested. There is no accessibility reader,
  overlay, clipboard history, analytics or cloud document sync in this preview.

## Validation status

JVM tests cover Unicode/size boundaries, stale replacement/undo, provenance,
private fields and HTTPS configuration. Instrumented tests exercise real Room
CRUD, packaged Rust analysis, editor save/library and a pending-autosave clear
race. Presence of test source is not execution evidence. See the root build
status and evidence records for actual local/CI/emulator/device outcomes.
Full native-platform parity, physical-device TalkBack/RTL/font-scale coverage,
store approval and live-provider configuration remain separate obligations.
