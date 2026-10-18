# OpenSuite One Android (v0.2.0 debug client)

This is a **native Android Views application** in Java/Gradle, not a WebView wrapper. It calls the existing OpenSuite One Node/SQLite JSON API and provides sign-in, overview, task/note/calendar/contact creation and editing, deletion, workspace search, notifications and optional read-only Nextcloud file listings.

**Important:** This is a development build of an early milestone. It is **not** a standalone Zoho One alternative, includes no backend server, has no offline synchronization, and is **not** a production-safe public-hosted suite. Do not use it for sensitive personal/business information without completing and auditing backend security. No real Android device test was run in the source-authoring environment.

## Build

Prerequisites: JDK 17 or 21, Android SDK Platform 35, Android SDK build tools and Gradle 8.9. The CI workflow uses GitHub-hosted Android SDK, `gradle/actions/setup-gradle@v4`, AGP 8.7.3 and `gradle :app:assembleDebug`.

```bash
cd apps/android
gradle --no-daemon :app:assembleDebug
```

APK generated at `app/build/outputs/apk/debug/app-debug.apk` and uploaded by GitHub Actions as a downloadable run artifact. **This debug APK is signed with an automatically created debug key** and is not a verified or signed production release. For release distribution, configure your own signing, server TLS, access policies, security review and release tests.

## Connect an Android emulator

1. On the computer, start the OpenSuite Node backend: `DEV_ALLOW_REGISTRATION=1 npm start` (requires Node 22.13+).
2. In a browser on the same computer, open `http://127.0.0.1:4173` and create the development workspace.
3. Install APK on Android emulator and set Server URL to `http://10.0.2.2:4173`, then log in with the account created in the browser. `10.0.2.2` is the emulator's alias for the host computer, not a public URL.

**Physical device:** Host the server securely behind HTTPS on a reachable domain, then enter that origin in the app. For *trusted local debugging only*, run the backend with `HOST=0.0.0.0` on a secure LAN and enter `http://<computer LAN IP>:4173`. Never expose the development backend directly to the public Internet. Organization sign-up is local-only; register on the server machine, not the phone.

## Platform constraints and security

- `INTERNET` and `ACCESS_NETWORK_STATE` permissions only. No microphone, contacts or file-storage device permissions required.
- Production manifest denies cleartext; debug build allows HTTP expressly for emulator/LAN development. HTTPS certificates are validated by the OS/OkHttp; redirects refused.
- Session cookie and CSRF token are stored in memory only (lost on process restart). Workspace server URL persists locally; passwords and tokens do not.
- All CRUD operations use the server's tenant-checked API and optimistic versions. Task creation uses idempotency keys. Mutations require active sessions and CSRF.
- No background push, encrypted offline cache, MFA/Keycloak, registration or cross-device sync queues are implemented here. The server must be continuously reachable for writes and reads.
- APK automated compilation and signing in CI is not a physical device/emulator QA pass. Verify login, CRUD, connectivity, lifecycle and accessibility on your own devices before use.
