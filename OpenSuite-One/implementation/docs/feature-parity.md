# Capability and feature-parity ledger

Statuses reflect **implemented current code**, not the aspirational master prompt. `tested` refers only to local Node HTTP automation. Web screen existence is not equivalent to feature completion.

| Domain | Intended open source provider | Current status | Actual evidence / gap |
| --- | --- | --- | --- |
| Auth / tenant | Keycloak + OpenSuite control plane | **Partial** | Local password sessions, tenant CRUD control, no SSO/SCIM/MFA |
| Tasks | Native + Plane/Deck | **Tested basic CRUD** | `tests/core.test.mjs`, HTTP API, optimistic versioning |
| Notes | Native + Docmost | **Tested basic CRUD** | `tests/core.test.mjs`, no rich documents |
| Calendar | Nextcloud Calendar/CalDAV | **Tested native event CRUD** | Native records only; no remote sync, recurrence or RSVP |
| Contacts | Nextcloud Contacts/CardDAV | **Tested native contact CRUD** | No remote sync, dedupe or group directory |
| Files / Drive | Nextcloud | **Unverified read-only adapter** | Name-only PROPFIND; no live provider test, file transfers, sharing |
| Docs, Sheets, Slides | Collabora WOPI | **Planned** | No editor server or WOPI implementation |
| Mail | Stalwart + Nextcloud Mail | **Planned** | No delivery/client protocol integration |
| Chat | Zulip | **Planned** | No API/WebSocket bridge |
| Meetings | Jitsi | **Planned** | No SDK/runtime validation |
| CRM | Twenty | **Planned** | No account/deal adapter |
| ERP, HR, finance, inventory | ERPNext | **Planned** | No financial/tax claims |
| Forms | Formbricks or native | **Planned** | No submit/collection flow |
| Automation | Activepieces | **Planned** | No trigger/workflow fabric |
| Projects | Plane | **Planned** | No project/boards API |
| Wiki | Docmost | **Planned** | No knowledge provider |
| Search | Native SQLite search -> later federated | **Tested basic title search** | Tenant-filtered, no remote/content federation |
| Notifications | Native | **Tested task notification** | No push, quiet hours or cross-provider events |
| Audit | Native | **Tested basic audit entries** | No integrity/tamper-proof storage |
| Browser/PWA | Native web implementation | **Built, not device-verified** | Static files, dark mode, responsive CSS, manifest |
| Android/iOS/iPadOS/macOS | React Native/Tauri future ADR | **Not built** | No APK/IPA/DMG or native runtime |

No source of Zoho or Google was reverse-engineered or included. No proprietary product screenshots, brand marks or icons were reused.
