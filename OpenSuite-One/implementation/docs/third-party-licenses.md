# Third-party source and licensing review (preliminary, not legal advice)

**Bundled runtime libraries:** none. Node.js platform features (`node:http`, `node:crypto`, `node:sqlite`) provide the server; the application does not redistribute the Node runtime. Original first-party source is MIT-licensed. The REA tool archive provided for planning was inspected but **not bundled**.

| Planned independent provider | Repository | Preliminary licensing/edition issue | State |
| --- | --- | --- | --- |
| Nextcloud Server | github.com/nextcloud/server | AGPL-3.0-or-later (check app-level notices) | Not bundled |
| Keycloak | github.com/keycloak/keycloak | Apache-2.0 | Not bundled |
| Collabora Online CODE | github.com/CollaboraOnline/online | Confirm exact components; CODE not recommended for commercial production | Not bundled |
| Stalwart | github.com/stalwartlabs/stalwart | AGPL-3.0 and proprietary SELv2 dual edition | Not bundled |
| Twenty CRM | github.com/twentyhq/twenty | Mostly AGPLv3 with MIT SDK areas and enterprise-licensed files; additional permissions | Not bundled |
| ERPNext | github.com/frappe/erpnext | Review GPLv3 and localization modules | Not bundled |
| Plane | github.com/makeplane/plane | Validate edition and core/community terms | Not bundled |
| Zulip | github.com/zulip/zulip | Validate Apache-2.0 package/dependencies | Not bundled |
| Jitsi | github.com/jitsi/jitsi-meet | Validate Apache-2.0 and dependencies | Not bundled |
| Formbricks | github.com/formbricks/formbricks | Check AGPL community/enterprise boundaries | Not bundled |
| Docmost | github.com/Docmost/docmost | Check AGPL community/enterprise boundaries | Not bundled |
| Activepieces | github.com/activepieces/activepieces | Core vs enterprise licensing must be verified | Not bundled |

**Current public validation (2026-10-18):** Nextcloud license note https://github.com/nextcloud/server/blob/master/contribute/HowToApplyALicense.md ; Keycloak repository https://github.com/keycloak/keycloak ; Collabora warning https://www.collaboraonline.com/faqs/ ; Twenty main license https://github.com/twentyhq/twenty/blob/main/LICENSE ; Stalwart license overview https://github.com/stalwartlabs/stalwart . Others remain unverified. This matrix does **not** assert an audited tagged commit license, brand permission, trademark grant or production use approval. Do not incorporate any upstream source before creating version-specific license and attribution records.
