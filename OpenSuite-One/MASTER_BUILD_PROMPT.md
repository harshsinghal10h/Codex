# OPENSUITE ONE — ULTIMATE MASTER BUILD PROMPT

**Edition:** 1.0 | **Prepared:** 2026-10-18 | **Document type:** AI engineering execution specification

> **Working product name:** OpenSuite One. Verify name/domain/trademark availability before publication. This is an **independent** all-in-one platform inspired by the breadth of Zoho One and Google Workspace, not an authorized Zoho/Google product. Do not use their branding, copyrighted assets, proprietary code, nonpublic APIs, or misleading claims of affiliation.

## 0. EXECUTION DIRECTIVE — READ FIRST

You are a coordinated staff-plus product and engineering organization: principal web/mobile/macOS engineers, distributed-systems and identity architects, open-source integration leads, security/privacy architects, database/infra specialists, accessibility and UX designers, performance engineers, QA leads, developer-experience engineers, and release managers.

**Mission:** Build a *genuinely functional, coherent, self-hostable, open-source-first, multi-tenant business-and-productivity operating system* in **one unified application experience**. Combine the strongest legally compatible community software behind **one account, one organization/workspace, one navigation system, one search, one notification inbox, one design system, one audit trail, one automation fabric, and governed interoperable data**. Do not simply display a menu of links to unrelated products.

**Primary platform contracts:** Android phone/tablet/foldable; iPhone and iPad; macOS desktop; responsive browser/PWA. **Secondary:** Windows and Linux desktop. A shared core must support all clients. Mobile should be an installable application, not a website packaged into a barely-functional WebView. Host business/server services on infrastructure; iOS, iPadOS and Android cannot run the complete multi-service server stack like a desktop/server OS.

**Priority:** secure correctness and interoperable, operable vertical slices over fake breadth. More than 50 named app icons are NOT equivalent to 50 working apps. Implement in stages, document missing pieces honestly, and never declare an untested feature production-ready. Do not ask repetitive questions: choose conservative, reversible defaults, record assumptions and implement a verified first vertical slice.

**Build output requirements:** checked-in repository; deployable local development stack; complete source and scripts; documented APIs; production-style design system; tested apps and adapters; Android APK/AAB when an Android build environment is available; iOS/macOS Xcode projects and signed artifacts only with authorized Apple signing access; browser/PWA; automated tests; CI; deployment manifests; reproducible SBOM and third-party notices. Never claim an APK/IPA/DMG, App Store approval, live deployment, or CI pass unless observed.

## 1. PRODUCT PRINCIPLES AND NON-NEGOTIABLES

1. **Independent and original UX**: reproduce *capabilities and user journeys*, not identical Zoho One/Google brand identity, logos, proprietary source, protected screenshots, or trademarked app names. Benchmark flows with permission and perform clean-room implementation.
2. **One suite, modular backend**: unified launcher and cohesive application shell with specialized services behind domain boundaries. Avoid monolith-of-everything AND dozens of disconnected password prompts.
3. **Open-source-first**: use truly OSI-recognized licenses for essential features. Do not quietly rely on paid enterprise modules, SaaS trials, or source-available-only code.
4. **Local-first where technically useful**: offline drafts, tasks, notes, calendar, contacts and recent files with encrypted local stores, conflict resolution and transparent sync. Never promise offline collaborative editing for a provider that cannot support it.
5. **Privacy-first**: no hidden telemetry, adtech, data sale, AI training on customer content, plaintext backups, or cross-tenant data leakage.
6. **Interoperability-first**: use OIDC/OAuth 2.1 where appropriate, SAML when required, SCIM where supported, CalDAV/CardDAV, IMAP/JMAP/SMTP, WebDAV, WOPI, Matrix or supported chat APIs, OpenAPI, webhooks, iCalendar, ICS, CSV, ODF and OOXML.
7. **One high-quality cross-platform experience**: same information architecture and recognizable visuals, but native accessibility, system controls, gesture conventions, keyboard shortcuts, share sheets and permission flows per OS. Pixel-perfect *identity*, not platform-hostile uniformity.
8. **No placeholders presented as features**: a disabled or incomplete integration must be labeled `not configured`, `unsupported`, or `planned`, with reason and setup instructions. No decorative charts showing invented business data.
9. **Security and compliance built in**: multi-tenancy, auditability, encryption/key rotation, RBAC/ABAC as needed, retention, consent, safe defaults, tenant-scoped exports and deletion.
10. **Incremental shipping**: continuously working builds and measurable checkpoints. An MVP with functioning Docs/Files/Calendar/Tasks/Chat/auth is more valuable than a mock 50-app dashboard.

## 2. TARGET FEATURE MATRIX — ALL MODULES MUST HAVE ACCEPTANCE CRITERIA

The following are **planned capabilities**, not claims of existing functionality. Provide a maintained matrix with `planned | developing | tested | released`, native/mobile parity, OSS source, integration method, data owner, license and acceptance tests.

| Suite area | Google Workspace / Zoho-style capability | Required user-visible implementation |
|---|---|---|
| Workspaces and admin | Zoho One, Admin Console, Cloud Identity | Organizations, teams, accounts, RBAC, app entitlements, profiles, invites, SSO, provisioning, policy, admin audits |
| Mail | Gmail, Zoho Mail | Unified inbox, compose, threads, search, attachments, signatures, filters, spam reporting, account settings, offline drafts, notification badges |
| Calendar | Google Calendar, Zoho Calendar | Month/week/day/agenda, recurring series, attendees, RSVP, time zones, scheduling, invites, ICS/CalDAV sync |
| Contacts | Google Contacts, Zoho Contacts | People, organizations, groups, dedupe, custom fields, CardDAV, import/export, global directory privacy |
| Files and drive | Google Drive, WorkDrive | Folders, upload/download, revisions, share permissions, links, quota, offline pinning, previews, trash, restore |
| Documents | Google Docs, Writer | Rich editing, comments, track changes where provided, version history, templates, collaboration, DOCX/ODT/PDF |
| Spreadsheets | Google Sheets, Sheet | Formulas, formatting, sorting/filtering, charts, CSV/XLSX/ODS, collaboration where supported |
| Slides | Google Slides, Show | Deck editing, themes, comments, presentation mode, PPTX/ODP/PDF |
| Notes and wiki | Google Keep, Zoho Notebook, Notion-like | Notes, backlinks, tags, notebooks/spaces, markdown, knowledge base, attachments, history |
| Team chat | Google Chat, Cliq | Channels/spaces, DMs, threads, mentions, presence, uploads, reactions, moderation, search |
| Calls and meetings | Google Meet, Zoho Meeting | Calendar join, protected links, audio/video, screen share, device switching, host controls, captions only when implemented |
| Tasks and projects | Google Tasks, Zoho Projects/Sprints | Personal tasks, boards, lists, calendar, assignments, dependencies, comments, milestones, time tracking |
| Forms/surveys | Google Forms, Zoho Forms/Survey | Drag/drop questions, validation, logic, sharing, collect, analytics, exports, privacy controls |
| CRM | Zoho CRM, Contacts pipelines | Leads, accounts, deals, stage changes, activities, pipeline, dedupe, access rules, reports |
| HR | Zoho People/Recruit | Employee profiles, leave, attendance (consent), recruiting, approvals, roles, policy |
| Accounting | Zoho Books/Invoice/Expense | Ledger, chart of accounts, invoices, receipts, expenses, reports; local tax compliance is *region-specific* and must not be faked |
| Inventory/commerce | Zoho Inventory/Commerce | Catalog, stock, locations, purchase orders, fulfillment, customer orders, role control |
| Support | Zoho Desk | Tickets, queues, SLAs, statuses, assignment, email ingestion, macros, audit |
| Marketing | Zoho Campaigns/Social | Audience consent, segmentation, compliant campaign sending, unsubscribes, scheduling and metrics |
| Automation | Zoho Flow, Apps Script-like workflows | Triggers, actions, jobs, approvals, retries, webhooks, visual builder, audit and dead-letter queues |
| Low-code data | Zoho Creator, AppSheet-like | Schemas, forms, views, permissioned CRUD, workflow builder, runtime validation |
| Analytics | Looker Studio, Zoho Analytics | Data connectors, permissions, saved queries, dashboards with real data and lineage, export |
| Search and AI | Cross-workspace search and assistant | Federated search with permission trimming; optional local/remote opt-in RAG with citations and action confirmation |
| Passwords/identity adjunct | Workspace identity controls | Passwordless/passkeys, TOTP, recovery, managed devices where platform APIs permit |
| eSignature | Zoho Sign | Document workflow, identity proofing according to jurisdiction, signer audit trails; clarify legal validity and provider licenses |
| Scheduling/bookings | Zoho Bookings | Public booking pages, availability policy, reminders, conflict prevention, timezone logic |
| Whiteboard | Collaborative boards | Infinite canvas, shapes, connectors, cursors, exports; only with a compatible OSS component |
| Mobile sync | Drive/Calendar/Contacts/Chat | Background-capable sync within OS constraints, deep links, notifications, share/open-in, offline queue |

**Must-have cross-suite journeys:**
- Convert an email into a task, assign it, reference a document, and surface updates from the same account.
- Schedule a meeting from Calendar, join it from a reminder, share a permitted Drive document, then capture follow-up tasks.
- Turn a permitted form submission into a CRM lead, assign an owner, log activity and produce a real audit entry.
- Create an invoice from a CRM deal via **explicit mapped entities** with idempotent reconciliation; do not silently duplicate customers.
- Global search returns only records the caller may access, with citations/deep links and graceful service failure.
- Admin removes a user and session access, revokes effective permissions across integrated apps within documented propagation limits, and preserves lawful audit history.

## 3. OPEN-SOURCE FOUNDATION — EVALUATE, DO NOT BLINDLY BUNDLE

Build an **ADR and license matrix** before importing third-party code. Licenses below are a starting point to verify against specific tagged commits and every relevant edition/asset/SDK. Merely knowing that an upstream project is open-source does not establish that its enterprise-only features may be redistributed.

| Layer / capability | Preferred candidate | OSS/operational considerations | Secondary/alternative |
|---|---|---|---|
| Files, groupware integration, collaboration home | **Nextcloud Hub / Nextcloud Server** | Strong open-source foundation; use its documented public APIs and app contracts; inventory each bundled app license | Seafile community, ownCloud community versions after license review |
| Office Docs, Sheets, Slides | **Collabora Online / LibreOffice** via WOPI | Open-source code; **CODE is for development/small teams, not recommended as general production deployment**. Evaluate supported server edition, performance and license | ONLYOFFICE Docs Community (AGPLv3 plus additional terms; community sizing limits) |
| Identity | **Keycloak** | Apache-2.0; central OIDC/SAML, MFA, federation; SCIM provisioning may need an adapter | Authentik (review current license/features) |
| Email backend | **Stalwart Mail Server** | AGPLv3/enterprise dual licensing; mail deliverability requires DNS/TLS/SPF/DKIM/DMARC, abuse protection and operations | Mailcow stack, Postfix/Dovecot |
| Mail client | **Nextcloud Mail** or compatible OSS web client | Mail UI is separate from reliable mail delivery; audit feature capabilities | Roundcube / SnappyMail after license check |
| Calendar/contacts | **Nextcloud Calendar / Contacts**, CalDAV/CardDAV | Reuse existing sync and server behavior; avoid parallel calendar truth | Radicale, Baïkal |
| Team messaging | **Zulip** | Apache-2.0; topic-based chat with documented integrations; visually adapt shell without misusing Zulip marks | Matrix/Synapse with Element; choose one primary chat model |
| Video meeting | **Jitsi Meet** | Apache-2.0; can embed or integrate with SDKs, real mobile device/network testing needed | Nextcloud Talk where capabilities meet requirements |
| Tasks/projects | **Plane** | Core AGPLv3, check enterprise-only features, editions and data model | OpenProject or Nextcloud Deck/Tasks |
| CRM | **Twenty CRM** | Core mainly AGPLv3 with some MIT/commercial areas; license audit essential | EspoCRM, SuiteCRM |
| ERP/accounting/inventory/HR | **ERPNext + Frappe** | GPLv3, robust ERP backend; accounting localization and fiscal compliance require region verification | Odoo Community (verify edition/license), Dolibarr |
| Surveys/forms | **Formbricks Core** | AGPLv3; proprietary enterprise/white-label restrictions must be reviewed before embedding/rebranding | Native simple forms module built from scratch |
| Knowledge base/wiki | **Docmost Core** | AGPLv3 core; enterprise extensions excluded | BookStack, Wiki.js |
| Automations | **Activepieces community core** | MIT core; proprietary enterprise/cloud code excluded | Node-RED (Apache-2.0) |
| Search | **OpenSearch** or Meilisearch | Isolated index per tenant/ACL permission filtering, secure ingestion | PostgreSQL full-text for MVP |
| Observability | **OpenTelemetry, Prometheus, Grafana** | Pin secure community versions and forbid secrets in telemetry | Open-source APM alternatives |
| Object storage | **S3-compatible provider** | Test exact server licensing and deployment model; do not assume current MinIO product editions satisfy open-source requirement | SeaweedFS/Ceph or cloud S3 adapter |
| Database/cache/queues | **PostgreSQL, Redis-compatible compatible-licensed cache, NATS or RabbitMQ** | Service-specific data ownership; tenant isolation; durable delivery | PostgreSQL outbox + workers for MVP |

**Selection rules:**
A. Use upstream applications as **independently deployable services** with documented APIs and original copyright notices. Do not promise combining GPL/AGPL source into a single proprietary binary. Independently assess derivative works, linking, SDK distribution, network-source-offer obligations, Section 7 terms, app-store rules and branding restrictions with legal counsel.
B. Prefer integrating over forking. A single coherent **frontend shell** can proxy/embed documented application views only where technically and legally allowed; use adapter layers to unify navigation and shared workflows. Native original modules are appropriate where upstream embeddability is insufficient.
C. For each app, record: upstream URL, chosen version/tag/SHA, SPDX license of *each relevant part*, support model, integration APIs, auth, sync, upgrade process, backend dependencies, hosted resource footprint, iOS/Android readiness, data portability, limitations, alternatives, exit plan.
D. Never say that entire Zoho One is “free” or that operating this suite is cost-free. Servers, domains, email delivery, push services, backups, Apple signing and support may cost money.
E. The goal is **one end-user client**; the backend can consist of multiple isolated services. Users should not install 15 apps individually for the standard experience, though optional standalone upstream apps can remain available.

## 4. REA SKILL — AUTHORIZED EVIDENCE-LED COMPETITIVE ANALYSIS

**Uploaded asset:** `rea-main.zip`. The bundled `.agents/skills/reverse-engineer-anything/SKILL.md` identifies REA as a shipped-artifact/runtime-evidence and reverse-engineering workflow. It explicitly says not to use REA for ordinary analysis of a full source repository. The zip is a skill/tool project, **not a supplied Zoho One binary, API credential, or reconstructed Zoho source**.

Workflow:
1. Read `rea-main/.agents/skills/reverse-engineer-anything/SKILL.md` and relevant reference files when REA is installed and relevant.
2. Begin with accessible official manuals, publicly documented user journeys, user-provided screenshots, and source-available alternative projects. Use normal source tools to understand those repositories.
3. For **legally acquired and authorized** APK/IPA/desktop artifacts or user-owned browser sessions, use REA's documented inventory/observation tools only after checking availability and approval. For a ZIP/APK/IPA use its artifact inspection pathway; for Android APK code use its Android inspection pathway; for user-owned browser session, use the authorized browser-target pathway. Do not hallucinate provider results.
4. Capture an **evidence dossier** for each examined feature: artifact identity, version, SHA-256, observation date, navigation path, user-visible behavior, performance trace, input/output, screenshots if licensed, confidence (`observed | inferred | unknown`), accessibility findings, and reconstruction spec.
5. Use isolated, clean-room feature requirements and original app code/design. Do not extract credentials, keys, secrets, unpublished algorithms, private user data or protected content. Avoid bypassing access controls, technical restrictions, rate limits, DRM or anti-bot systems.
6. If REA tools are unavailable, proceed with official docs/source/manual observation and mark reverse-engineering verification `not performed`. Do not claim REA analyzed a target merely because its skill ZIP was inspected.
7. Re-run competitive parity and behavior tests only against authorized targets and integrate findings into `docs/evidence/` and `docs/feature-parity.md` with provenance.

## 5. SYSTEM ARCHITECTURE

### 5.1 Three planes
**Experience plane:** branded, accessible application shell (`web`, `android`, `ios`, `macos`, optional `windows/linux`), unified navigation, deep links, rich universal search, account switcher, productivity surfaces, contextual commands, system share integration, app store and settings.

**Control plane:** tenants/orgs, tenant routing, SSO, SCIM-like provisioning, policies, app entitlements, RBAC, audit stream, secrets, configuration, billing/subscription optional, service catalog, notifications, workflow templates, cross-app identity and indexing.

**Data/service plane:** Nextcloud, office engine, email, calendar/contacts, chat, Jitsi, CRM, ERP, projects, forms, automation, search and storage, each with its own authoritative schema/API; event-driven integration and reconciliation.

### 5.2 Reference monorepo
```text
opensuite-one/
  apps/
    web/                    # React/Next.js or equivalent, SSR/PWA only where appropriate
    mobile/                 # React Native + native modules OR Flutter: document ADR
    desktop/                # Tauri or native macOS client; document build rationale
    admin-console/          # tenant, policy and service administration
    api-gateway/            # authz, tenant routing, rate limits, BFF endpoints
    worker/                 # durable jobs, sync, indexing, notification fanout
  packages/
    design-system/          # design tokens, icons, components and accessibility
    domain-contracts/      # schemas, API contracts and permission scopes
    auth-client/            # OIDC PKCE, secure storage, reauth and token renewal
    api-sdk/                # typed clients for first-party and adapter APIs
    sync-engine/            # offline queue, conflicts, idempotency, retry
    search-client/          # permission-aware federated search
    telemetry/              # redacted event/trace contracts
    test-fixtures/          # seeded non-sensitive tenant scenarios
  services/
    control-plane/          # tenant/app entitlements, identities and policies
    notifications/          # in-app + APNs/FCM + email preference flows
    integrations/           # adapters, mappers and webhook ingress
    indexer/                # outbox consumers, permission index and reindex
  connectors/
    nextcloud/
    keycloak/
    stalwart/
    collabora/
    zulip/
    jitsi/
    twenty/
    erpnext/
    plane/
    formbricks/
    docmost/
    activepieces/
  infra/
    compose/                # small-team dev/demo stack
    helm/                   # independently scalable production deployment
    terraform/              # optional reference deployment, no hardcoded cloud
    monitoring/             # metrics, audit, alert rules
  docs/
    adr/                    # architecture and licensing decisions
    api/                    # generated public OpenAPI + integration contracts
    evidence/               # REA/UX provenance when authorized
    operations/             # install/backup/restore/upgrade/runbooks
    feature-parity.md
    third-party-licenses.md
    threat-model.md
    security.md
    release-checklist.md
  tests/
    contract/ integration/ e2e/ security/ performance/ accessibility/
  .github/workflows/
  README.md
  AGENTS.md
  LICENSE
  THIRD_PARTY_NOTICES.md
  docker-compose.yml
  .env.example
```

### 5.3 Engineering contracts
- Choose **TypeScript** for shared schemas/UI/gateway tooling as appropriate; let upstream ERPNext remain Python/Frappe, Jitsi/Zulip/Nextcloud run in their supported stacks. Do not rewrite mature upstream internals into one language just for consistency.
- Primary database for first-party control plane: PostgreSQL. Isolation via explicit `tenant_id`, strict policy filters/RLS where appropriate, per-tenant authorization tests, tenancy keys and resource quotas. Use a separate database/schema/service-owned persistence when warranted, **never shared direct writes into upstream internals**.
- Define canonical identities `Tenant`, `User`, `Group`, `Project`, `ExternalIdentity`, `FileRef`, `DocumentRef`, `CalendarEventRef`, `TaskRef`, `ConversationRef`, `CRMAccountRef`, `DealRef`, `InvoiceRef` with provenance, source revision and stable mapping IDs; entities do **not** become replicas of whole upstream databases.
- Publish typed OpenAPI contracts and versioned events with `event_id`, `tenant_id`, `aggregate_id`, `schema_version`, `occurred_at`, causation and idempotency key. Use inbox/outbox patterns, retries with jitter, dead-letter queues and replay tooling.
- Document “single source of truth” per entity. Idempotent two-way sync must detect cycles and conflicts; return conflicting fields visibly rather than silently overwriting them.
- Gateway delegates authentication to Keycloak, but **authorization happens at each protected resource**. Cross-app tokens must be least-privilege, bound to tenant and audience; never share administrator tokens with clients.
- BFF only returns least-privilege views. Avoid unbounded fanout; search/index fetches have strict ACL trimming plus re-checks at click/read time.
- Each service remains upgradeable, isolated by network policies and pinned deploy artifacts. Deploy either self-managed Docker Compose profile (small deployments) or Kubernetes/Helm profile (larger). State which one is supported and tested.
- All admin mutations are audit logged with actor, target, effective permission, source IP/device as allowed, before/after summary, timestamp, correlation ID and integrity protection; sensitive values redacted.

## 6. CROSS-PLATFORM CLIENT STRATEGY

Before coding, create ADR `0001-client-stack.md` comparing **React Native + Expo development builds + React Native for Web**, **Flutter**, **Kotlin Multiplatform + native SwiftUI/Compose**, and **web + Tauri** against capability, bundle/RAM, native UI fidelity, offline sync, rich document/meeting embeds, maintainability, signing and cost. Select one coherent solution, not four unfinished experiments.

**Recommended default:** React/TypeScript web shell + React Native mobile clients with selectively shared business logic/design tokens; Tauri desktop shell where justified; native Kotlin/Swift modules for Android/iOS platform edges. Do not promise that identical frontend code can be compiled to all OSs without platform adaptation. If actual measurements favor Flutter/KMP, provide evidence and adjust.

**Android:** minSdk set by supported dependencies and security updates; API-level support matrix; responsive Compose/native interoperable surfaces where useful; scoped storage/file picker; push through FCM or supported provider with user consent; Android background work via WorkManager; biometric/passkeys APIs, IME, accessibility, split-screen, tablets, foldables. APK debug build for QA, signed AAB only when distribution credentials exist. Protect exported components and deep links.

**iOS/iPadOS:** Apple sign-in rules when relevant, native Keychain, APNs entitlements, background transfer limits, Universal Links, Share extensions, document picker, biometric auth, Dynamic Type, VoiceOver, Stage Manager/iPad multitasking, offline limits. Generate Xcode projects; IPA/TestFlight require legitimate signing team/profiles. Never imply an unsigned IPA is user-installable on all iPhones.

**macOS:** notarization/hardened runtime sandbox assessment, Apple Silicon + Intel support if feasible, native menus/keyboard shortcuts, drag/drop, window/tab management, system share service and files permissions; `.app`/DMG only from tested build. If shipping a Mac App Store flavor, review sandbox, external process limitations, dynamic code and third-party editor constraints.

**Windows/Linux:** use the same shell/contract clients where practicable, platform auth/keyring and file handling, installers/releases only after VM validation.

**Web/PWA:** secure origin, responsive SSR/caching design, semantic URLs and metadata, keyboard operation, service worker carefully scoped, IndexedDB offline cache encryption strategy, installability, browser support matrix. Avoid treating a PWA as indistinguishable from native background/push features on every platform.

## 7. USER EXPERIENCE / DESIGN SYSTEM — VISUALLY ONE PRODUCT

Build an original, mature enterprise productivity design system with clear hierarchy and minimal friction. Inspiration may be taken from modern collaboration suites, but no direct pixel clone of Zoho/Google UI. Optimize for information density, clarity and comfortable touch targeting.

**Shared shell anatomy:** top universal command/search bar; compact app switcher; contextual left rail; per-module tabs and breadcrumb; content canvas; inspector/right pane; shared omnibox; unified notification center; account/workspace switcher; quick-create; command palette; global settings; admin mode with visually distinct permissions; status and conflict indicators.

**Responsive grammar:** mobile bottom navigation for 4–5 primary destinations and searchable app drawer; tablets split-pane; desktop resizable navigation and multiwindow where platform supports. Keep task flow and actions consistent across screen sizes, not cluttered. Support high-density and comfortable modes, light/dark/system themes, reduced motion, RTL and internationalization (including English/Hindi locale groundwork).

**Interaction standards:** every tappable control must actually work; all loading, empty, denied, read-only, stale, unsynced, network failure, conflict and destructive-confirmation states exist. Keyboard shortcuts; undo for reversible actions; optimistic UI only where reconciliation and rollback are implemented; WCAG 2.2 AA target, focus traps audited, VoiceOver/TalkBack validation.

**Performance design:** avoid gratuitous motion and translucent surfaces that cause frame drops. Virtualize large lists, prioritize interaction responsiveness, lazy-load app modules, prefetch frequently used routes and limit background sync. Use native animations sparingly; never block typing for analytics or network calls.

**Screens to design and implement:** onboarding; login/SSO; organization setup; global home; app launcher; unified search; notifications; mail inbox/compose; calendar/booking; file browser and share dialog; document editing handoff; chat; meetings; tasks/boards; CRM pipeline; invoices; workflows; team directory; admin console; app marketplace; user preferences; offline/conflict center; help and diagnostics.

## 8. DEEP FEATURE IMPLEMENTATION REQUIREMENTS

### 8.1 Identity and administration
- Create user, invite, accept, reset, MFA/passkey, session revoke, device session list, org role/teams and app entitlements; audit login failures and high-risk actions without leaking credentials.
- **SCIM-like provisioning** connector where APIs permit; provisioning state, retry and reconciliation. No automatic broad account admin rights across upstream apps.
- Enforce least privilege at file share, calendar, chat, CRM, billing, ERP and search boundaries; test cross-org permutations systematically.

### 8.2 Files and collaborative documents
- Nextcloud Files native public APIs for directories, move/copy, uploads, chunked transfers, versions, shares, trash and restore; secure link-sharing defaults, quota and conflict behavior.
- Office integration via supported WOPI and upstream editor embedding/hand-off; validate user tokens, callback origin and document permission. Do not build a simplistic text area and call it collaborative Docs. Preserve concurrent edits, versions and export fidelity within documented limitations.
- Offline file support on mobile should use explicit download/pin selection and encryption; sync on reconnect without silent loss.

### 8.3 Communication and scheduling
- Authenticate mail client against intended server capabilities; support server policy, rate limits, secure attachments and anti-abuse; do not configure a public open relay. Mail receiving/sending is not operational until DNS+domain reputation checks pass.
- Calendar/contacts via CalDAV/CardDAV or upstream APIs with recurring exception correctness, exact timezone handling, attendee updates, de-dupe and ICS.
- Chat with durable message states, unread counts, server events, authz for private channels, user blocking/moderation as supported. Meetings use Jitsi (or documented Nextcloud Talk) SDK with actual permissions and real network resilience.

### 8.4 Business suite
- Integrate Twenty for CRM and ERPNext for accounting/HR/inventory; use adapters and mapped object identities. Build real end-to-end CRUD and business rule flows, not HTML shells.
- CRM → Invoice requires authorization, required field mapping, tax region and currency, idempotency, conflict reporting, rollback/compensation strategy. Do not claim regulated accounting, legal-compliant payroll, or GST/VAT compliance without localization certification.
- Formbricks/Plane/Docmost/Activepieces integration must use publicly supported interfaces, preserve license/enterprise boundaries and expose only enabled features.

### 8.5 Search, notifications, AI
- Aggregate indexed metadata/content **only with permission and opt-in**. Search results show title, entity type, source, timestamp, authorized preview and deeplink; revalidate on access, support stale-index behavior and deletion.
- Notification center unifies mentions, emails, tasks, meeting events and approvals with dedupe, importance, quiet hours, channel/device preferences and read state. Push payloads contain minimum content.
- Optional AI is OFF by default unless requested: provider abstraction (local models and consented remote models), retrieval cites accessible sources, no fabricated sources, no outbound customer data without admin/user permission, secrets held server-side; destructive actions require explicit confirmation. Instrument per-tenant budgets and opt-outs.

## 9. API, SYNC AND ERROR CONTRACTS

- Generate OpenAPI schemas, `openapi-typescript` or equivalent SDKs, run schema validation in CI.
- Standardize error family: `AUTH_REQUIRED`, `FORBIDDEN`, `TENANT_MISMATCH`, `RESOURCE_NOT_FOUND`, `CONFLICT`, `RATE_LIMITED`, `PROVIDER_UNAVAILABLE`, `SYNC_PENDING`, `VALIDATION_FAILED`, `INTEGRATION_NOT_CONFIGURED`, `UPSTREAM_UNSUPPORTED`, `OPERATION_TIMEOUT`.
- All mutations use request IDs or idempotency keys when relevant; implement ETags/version conflict detection for concurrency.
- Offline queue records tenant, account, device, operation, dependency, local entity id, server version, retries and user-visible failure. On account switch/delete, scrub unauthorized cached content and tokens.
- Uploads use resumable chunking/checksums with server quota and content-type scanning. Accept limits must be configured, surfaced and tested; reject executable/macros when tenant policy says so.
- Do not claim strong consistency across external applications. Specify eventual consistency, propagation SLOs, and service health feedback.

## 10. SECURITY, PRIVACY, LEGAL AND OPERATIONS

**Security baseline:** threat model using STRIDE or equivalent; OIDC authorization code+PKCE; MFA; secure cookie attributes on web; Keychain/Keystore on mobile; CSRF/CORS defense; SSRF protection for import/URL preview/webhooks; CSP; XSS-safe rendering; tenant-scoped uploads; input validation; rate limiting; secret scanning; least-privilege service accounts; rotating credentials; network segmentation; encrypted transport; encryption-at-rest and documented key management; secure backup/restore; verified dependency provenance; audit log.

**Compliance and policy:** clear data controllers/processors in hosted model, DPA options, retention schedule, region selection only if deployment supports it, consent and rights requests, account deletion/export, signed DPA/terms for production operator; avoid unverified GDPR/SOC 2/ISO 27001/HIPAA claims. For India, review applicable data-protection requirements and locally relevant accounting/GST requirements before representing compliance.

**OSS/IP:** record SPDX license and NOTICE files, run license/sca scanners, separate bundled sources from API-integrated services, identify AGPL network-source-offer requirements and any additional terms, preserve attribution, review trademarks and icons. App-store distribution may present license issues for copyleft components; get legal review before distribution. **No proprietary app cloning or protected code extraction.**

**Operations:** reproducible container builds, pinned image tags/digests and upstream changelog review; schema migration/rollback strategy; blue/green or rolling deployments where supported; default deny networks; first-run administrator bootstrap; TLS/ACME configuration; health/readiness checks; service circuit breakers; on-call alerts, backup schedule, PITR for PostgreSQL, encryption/key recovery and regular restore drills. Meter email queue health, file sync lag, meeting media quality and connector backlogs.

## 11. PERFORMANCE AND RESOURCE BUDGETS

The following are **initial engineering goals**, not assertions of achieved performance; baseline them on stated devices/load and adjust only with measured evidence.

| Dimension | Initial measurable target |
|---|---|
| Browser shell load | LCP <= 2.5s at p75 on defined midrange device and network |
| UI interactivity | INP <= 200ms at p75 on representative routes |
| Mobile animations | 60fps target on midrange tested phones in primary interactions; document dropped frames |
| Search | p95 <= 500ms for properly indexed permitted metadata in reference test deployment, excluding slow remote failover |
| Standard first-party API read | p95 <= 300ms under stated concurrent tenants/load and warm DB |
| App switching | Visually responsive within 200ms for cached shell; upstream content may take longer with honest loading state |
| Background synchronization | Adaptive/OS-compliant; no blanket always-on wake lock; record battery/network use |
| Reliability | Define, measure and test availability, RPO/RTO and connector error budgets per deployment tier |
| Offline behavior | Draft/task/note creation succeeds without network, sync queue replays after reconnection with conflict visibility |

Collect a before/after trace on Android 13/14+ representative midrange hardware, recent iPhone and iPad, Apple Silicon Mac, lower-memory desktop and throttled web. Measure bundle size, peak RAM, CPU, battery/network, cold/warm startup, accessibility and slow API behavior. No unmeasured “10x faster” claims.

## 12. TEST AND RELEASE MATRIX — NO FAKE PASSES

**Unit:** domain rules, role enforcement, normalization, dates/timezones/DST, merge algorithms, idempotency, validation, billing/accounting mappings and UI state machines.

**Contract:** all upstream adapter API versions, token expiration/refresh, rate limiting, pagination, compatibility test using recorded sanitized fixtures. Test service boundaries without asserting all upstream features work.

**Integration:** local Compose service stack, Keycloak login, tenant creation, files/office, calendar/contacts, mail sandbox (not public send), chat, tasks, CRM→ERP, form→CRM and search indexing.

**End-to-end:** Playwright for browser; Android emulator plus real device when available (Maestro/Detox/Appium as selected); iOS simulator and real device when signing available; macOS smoke; accessible keyboard/VoiceOver/TalkBack. Test airplane mode, reconnect, partial upstream outage, CPU throttling, app kill/restart, revoked permissions, background push behavior and memory pressure.

**Security testing:** OWASP ASVS/MASVS assessment, dependency/SBOM and secret scans, automated tenant-isolation adversarial tests, webhook signature/SSRF, object-level authorization, concurrent edit conflict, abuse/rate limits, uploads and request payload limits. Static checks are not substitutes for targeted adversarial testing.

**Release gates:**
- Formatting, lint, typecheck, unit/contract/integration tests pass in CI.
- Documented end-to-end critical journeys pass against configured actual services.
- Every release lists features that are *fully verified*, *supported but not verified on device*, or *not yet implemented*.
- No credentials or personal data in repository/build artifacts; license and attribution checks pass.
- Version/tag, release notes, upgrade/rollback and secure installation documentation present.
- Android signed release requires own signing keys; iOS IPA/App Store and macOS notarization require authorized Apple credentials and their own release gates.

## 13. MILESTONES — BUILD REAL SOFTWARE IN VERTICAL SLICES

**Phase 0 — Evidence, planning and legal:** inspect REA skill; build OSS license/edition matrix; competitive journey specs; architectural decisions; threat model; feature registry; target platform ADR; UX flows and acceptance tests. Create repository skeleton and CI. **Gate:** documented choices, traceable source and build script run.

**Phase 1 — Unified core + first genuinely working apps:** Keycloak OIDC/PKCE login, org creation, roles, one shell/design system, app switcher, Nextcloud file browser with real upload/download/share, Calendar/Contacts with real backend, personal task CRUD, federated basic search, notification store, encrypted offline draft queue and settings. Mobile Android/iOS project can authenticate and complete at least Files/Tasks; macOS and web shell run. **Gate:** critical end-to-end journeys pass with no fake data and reproducible startup.

**Phase 2 — Office and communication:** WOPI-based document editing, spreadsheet/presentation handoff, mail client/backend with safe test domain, real chat via chosen provider, Jitsi meeting join/screen sharing where supported; responsive client UX parity. **Gate:** independently verified document editing, permissions and meeting flows on supported targets.

**Phase 3 — CRM/ERP/business integration:** Twenty + ERPNext adapters; account/customer/deal/invoice lifecycles; Plane projects; Formbricks forms; approval workflows and admins. **Gate:** form → permitted lead → opportunity → invoice cross-app flow is real, auditable and idempotent.

**Phase 4 — Knowledge, analytics, automation and AI:** Docmost, Activepieces community core, permission-aware analytics dashboards, bulk import/export, optional consented assistant, tenant usage analytics. **Gate:** test with real data and sound permission enforcement, no fabricated metrics.

**Phase 5 — Hardening and broad release:** 2–3 tenant load tests, penetration test resolution, accessible audits, backups and restore drills, production Compose/Helm validation, localization, Android/iOS/macOS release assets where signing infrastructure exists, software bills of materials and docs. **Gate:** security/performance/operational release checklist signed off.

**Non-negotiable:** if all of Phase 5 cannot be delivered in one execution, complete the strongest verified earlier phase and leave the repository in a buildable, accurately documented state. Do not pretend all stages are complete.

## 14. DELIVERABLES AND FILES TO CREATE

1. `README.md`: mission, one-command demo, screenshots of implemented UI only, current status, feature flags, platform matrix, demo and production distinctions.
2. `docs/feature-parity.md`: Zoho/Google-style feature categories → selected OSS implementation → native/web/desktop coverage → implemented/tested/unsupported status → evidence links.
3. `docs/third-party-licenses.md` and `THIRD_PARTY_NOTICES.md`: exact dependencies/editions/commit IDs, licenses, source offer obligations and trademark notes.
4. `docs/adr/` for client stack, multi-tenancy, auth, office engine, mail, search and integration strategies.
5. `docs/threat-model.md`, `docs/operations/`: deployment, secrets, backups/restores, mail DNS setup, data import/export, tenant provisioning, incident response, upgrades.
6. `apps/`, `packages/`, `services/`, `connectors/`, `infra/` containing **working** implementation, not empty scaffolding passed off as a product.
7. `.github/workflows/ci.yml` and appropriate `release-android`, `release-ios`, `release-macos` workflows with explicit secret prerequisites and safe no-secret behavior.
8. OpenAPI specs, sample SDK usage, contract fixtures, seeded sample tenant scripts and executable automated tests.
9. `BUILD_STATUS.md`: exact commands run, successful/failed checks, artifact URLs (only verified), operating systems actually tested, blockers and next priorities.
10. `SECURITY.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `.env.example` (no secrets), issue templates, MIT/Apache/AGPL-derived licensing strategy approved after review.

## 15. GITHUB CONNECTOR EXECUTION PROTOCOL

Use connected GitHub **only with user-authorized scope**:
- Preferred account: **`harshsinghal10h` (Primary)**. If the session offers several accounts and this label differs, resolve identity safely.
- For a **new, explicitly requested repository**, create it only if connector exposes repository creation; otherwise don't pretend it exists and ask for an empty repository URL **after completing local work**. Never overwrite unrelated projects.
- Preferred future repository name: `opensuite-one` if available and selected by user; alternative `opensuite-one-platform`. Until then, place this master prompt in a clearly separated folder in an authorized existing project (for example, `Codex/OpenSuite-One/`) without editing its app code.
- Before writes: inspect repo permissions, default branch, relevant files and commit base. Use new paths instead of destructive overwrites. Use feature branch + pull request when changing application source; documentation-only additions may commit directly when expressly authorized.
- Commit small coherent increments; conventional commits. Do not commit `.env`, tokens, caches, dependencies, personal data, APK signing keys, Apple certificates or upstream binaries without separate rights review.
- Verify each returned commit SHA and GitHub file URL using connector read-back; report exact repo/path and what was **actually pushed**.
- GitHub storage **does not compile, sign or publish app binaries by itself**. Set up and run CI and device release steps, then label status honestly.

## 16. STRICT OUTPUT FORMAT FOR THE AI BUILDER

At start, publish **(A)** source/REA and capability audit; **(B)** feature/license/edition matrix; **(C)** critical ADRs with reasoning; **(D)** vertical-slice milestone plan with acceptance tests; **(E)** repository and connected-account assessment.

Then implement real code immediately in this priority order: auth/tenant security → shared shell/design system → Nextcloud Files/Calendar/Tasks adapters → typed API and sync → Android/iOS/macOS/web clients → integration tests → CI → additional suite modules. Run commands and show pass/fail evidence. After each milestone report:

```text
RELEASE / MILESTONE:
Implemented (working and tested):
Partially implemented (exact limits):
Not implemented:
Third-party license/edition constraints:
OS/build environments actually verified:
Commands and tests executed (with results):
Security/privacy checks (with results):
Performance measurements and device specifications:
GitHub repository / branch / commit SHA:
Artifacts actually built / download URLs:
Blocking issues and prioritized next actions:
```

**Do not stop with just a plan or polished mockup if authorized toolchain exists and actual implementation is requested. Do not claim successful deployment, app store distribution, exact clone fidelity, OS parity, full legal compliance or compatibility without tests and evidence.**

## 17. SPECIFICATION SOURCES AND VALIDATION ANCHORS

Check latest upstream docs, version, terms and license for each at execution time (links are starting points, not legal approval):

- Zoho One product/app categories: https://www.zoho.com/one/ and https://www.zoho.com/one/apps.html
- Nextcloud integrated collaboration: https://nextcloud.com/ and https://github.com/nextcloud/server
- Collabora Online/CODE operational guidance: https://www.collaboraonline.com/code/ and https://github.com/CollaboraOnline/online
- ONLYOFFICE Community licensing/limits: https://github.com/ONLYOFFICE/DocumentServer
- Keycloak: https://github.com/keycloak/keycloak
- Stalwart: https://github.com/stalwartlabs/stalwart
- Zulip: https://github.com/zulip/zulip
- Jitsi Meet: https://github.com/jitsi/jitsi-meet
- Twenty: https://github.com/twentyhq/twenty
- ERPNext: https://erpnext.com/license-trademark and https://github.com/frappe/erpnext
- Plane: https://github.com/makeplane/plane
- Formbricks: https://github.com/formbricks/formbricks
- Docmost: https://github.com/Docmost/docmost
- Activepieces: https://www.activepieces.com/docs/about/license
- Uploaded REA skill: inspect user-provided `rea-main.zip`, especially `.agents/skills/reverse-engineer-anything/SKILL.md`, rather than assuming an REA provider is already installed.

---

### FINAL OVERRIDING INSTRUCTION

Deliver **one usable, secure, open-source-first suite across Android, iOS, iPadOS, macOS, web, and subsequently Windows/Linux**, assembled from well-understood legal components behind a thoughtfully designed cohesive experience. Prefer functional integration over icon count, original design over proprietary UI copying, inspectable evidence over marketing claims, and a real tested milestone over an imaginary “complete” product.
