# OpenSuite One

**Status: early, working web-first foundation (v0.1.0). Not the complete Zoho One or Google Workspace replacement.**

OpenSuite One is an independent, original, open-source-first business workspace project. The current milestone provides a responsive application, a secure-by-default local API, tenant-scoped task/note/event/contact records, workspace search, notifications, an audit log, and an **optional read-only Nextcloud WebDAV preview**. It is intentionally not a copy of Zoho or Google's products or visual identity.

## Run locally (Node 22.13+)

No dependency install required. From the project folder:

```bash
DEV_ALLOW_REGISTRATION=1 npm start
```

Open **http://127.0.0.1:4173**, choose **Create a workspace**, and supply a workspace name, your email and a password of at least 12 characters. The environment flag enables local-only registration outside production. Tasks/notes/contacts/events are real SQLite records persisted to `./data/opensuite.sqlite`. Data are not shipped prepopulated; your home metrics show actual workspace values.

For an existing environment, provision the first account from a terminal:

```bash
OPENSUITE_BOOTSTRAP_PASSWORD='replace-this-with-a-strong-unique-secret' npm run bootstrap -- 'Team Name' 'Owner Name' owner@example.com
npm start
```

The command above is an illustrative invocation; choose your own strong secret and avoid leaving it in shell history. Do not expose the development server directly to the public Internet. Production requires an HTTPS reverse proxy, hardening, infrastructure/security validation and identity system work.

To test:

```bash
npm run check
npm test
```

## What works today

| Feature | Current implementation | Limitations |
| --- | --- | --- |
| Account / workspaces | Owner registration in local-only development, password sign-in, HTTP-only sessions, sign-out | Keycloak OIDC, invites, MFA, account switching not implemented |
| Tasks | Create, list, edit, status change, delete, priority, due dates, optimistic version check, idempotent create | No assignment, dependencies, recurring tasks, mobile offline queue |
| Notes | Create/list/edit/delete, basic text and pin field | No rich editing or collaboration |
| Events | Create/list/edit/delete, ISO timestamps, chronological upcoming list | No recurrence, attendees, timezone-series processing, CalDAV |
| Contacts | Create/list/edit/delete | No CardDAV, dedupe or sharing policies |
| Search | Tenant-scoped title/name search across four modules | No remote federation or document text indexing |
| Notifications | Task creation events, read state | No push or cross-provider events |
| Audit | Tenant-scoped creation, update, deletion and creation of organization | No immutable tamper-proof store or export |
| Files | Server-scoped optional read-only Nextcloud WebDAV name listing | No upload, download, browsing hierarchy, share or revisions |
| Web | Responsive app shell, navigation, command-search, dialogs, dark mode, PWA metadata and static offline shell fallback | App data are **not** available offline; not a native application |

**Not yet built:** native Android/iOS/iPadOS/macOS apps, desktop packaging, Keycloak federation, Docs/Sheets/Slides, mail, chat, meetings, CRM, ERP, HR, accounting, projects, forms, AI, analytics, automation, billing, controlled sync and production infrastructure. Those cannot honestly be called working merely because named modules are listed in the planning specification.

## Security design (implemented but not externally audited)

- Node built-in SQLite database, parameterized statements and explicit `tenant_id` filters on resource reads/mutations.
- Passwords hashed with random-salt `scrypt`, hashed random session tokens and CSRF values; HTTP-only, SameSite=Strict session cookies.
- CSRF tokens required for authenticated mutations, origin comparison when Origin exists, standard browser security headers.
- Resource ownership enforced on modification; members are read-only to other members' content by design of the current slice; admins may update within their own tenant.
- No tokens or passwords in frontend bundles, and no external API keys required for core features.
- Operator-configured Nextcloud credentials stay server-side and are scoped to one explicit tenant ID.
- No analytics/advertising scripts or fabricated business statistics.

**Not fully secure for internet production:** local passwords do not replace Keycloak/MFA, login throttling is in-memory, user invitations/session administration are missing, audit log is mutable, robust backup/restore and multi-node coordination unverified, lack of upload malware scanning, security and accessibility audits pending. HTTPS termination and host allowlisting are operator responsibilities. See [threat model](docs/threat-model.md).

## Architecture

```text
apps/web/public/   dependency-free responsive web/PWA frontend
apps/api/          Node 22 HTTP JSON API, auth, authorization, SQLite
connectors/nextcloud/ read-only WebDAV adapter (optional)
scripts/           owner bootstrap
 tests/            HTTP integration/regression tests
 docs/             ADR, legal/release/operations and feature tracking
```

Unlike the intended service-scale production architecture (PostgreSQL/Keycloak/Nextcloud, etc.), this dependency-free SQLite foundation was chosen for **verified execution in the restricted build environment**. The production multi-service architecture remains a forward ADR, not a claim about deployed infrastructure.

## Platforms

| Target | State |
| --- | --- |
| Browser (Chromium-family/Safari/Firefox) | Web assets built, Node HTTP tests executed; browser/device matrix not completed |
| Web PWA | Manifest and static shell service worker implemented; installation not device-tested |
| Android APK/AAB | **Not built** (Android SDK/build/test environment unavailable) |
| iOS/iPadOS IPA | **Not built** (Xcode/Apple signing unavailable) |
| macOS app/DMG | **Not built** (macOS signing and device unavailable) |
| Windows/Linux | No desktop package; browser experience can be used if hosted |

## Roadmap and decisions

See [feature parity](docs/feature-parity.md), [client architecture ADR](docs/adr/0001-client-stack.md), [identity ADR](docs/adr/0002-identity.md), [license matrix](docs/third-party-licenses.md), [build status](BUILD_STATUS.md), and [operations](docs/operations/local-development.md). No third-party upstream code has been copied or repackaged.

## Legal

OpenSuite One is unaffiliated with Zoho, Google or the listed upstream projects. Zoho One and Google Workspace are reference categories only. All product marks belong to their respective owners. Current first-party code uses the MIT license. Future upstream components require exact version/edition/license review before distribution.
