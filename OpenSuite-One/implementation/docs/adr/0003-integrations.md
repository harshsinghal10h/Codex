# ADR 0003 — Provider integration model

Each upstream remains its own licensed, independently deployable service. The suite exposes original UI and tenant-aware connectors to supported provider APIs. A provider's data remains authoritative; first-party replicas must preserve external IDs and revisions and be governed by an explicit conflict/reconciliation strategy.

**Current adapter:** `connectors/nextcloud/dav.mjs` performs one read-only WebDAV depth-1 PROPFIND with an operator-configured, HTTPS-only Nextcloud URL, an app password retained server-side and a strict tenant ID match. Only display names are returned. This is not yet a full XML/DAV parser, upload client, editor or Nextcloud sync engine. Errors return `PROVIDER_UNAVAILABLE`, and unconfigured tenants receive a truthful state.

**Deferred:** Keycloak OIDC, CalDAV/CardDAV, JMAP/IMAP/SMTP, WOPI, Jitsi, Zulip, Twenty, ERPNext, Plane, Docmost, Formbricks and Activepieces integration. Do not enable cross-tenant shared upstream accounts in production. Validate exact versions/licenses before bundling any upstream images or source.
