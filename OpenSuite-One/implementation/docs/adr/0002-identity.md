# ADR 0002 — Identity, tenant enforcement and storage

**Current verified slice:** Node 22 built-in SQLite database in local single-process mode. Sessions use random 256-bit cookie token, SHA-256 server-side token digests, HTTP-only cookies and client-delivered CSRF tokens. Password hashing uses scrypt with per-user random salt. Every data query is filtered by tenant ID derived from the authenticated session. Mutation is restricted to a creator/owner or workspace admin. Duplicate task creation can be avoided with an explicit idempotency key. Optimistic versioning rejects stale edits.

**Target architecture (not built):** Keycloak OIDC authorization code + PKCE and externally managed PostgreSQL tenant-isolated control plane, with signed audience-scoped tokens, SCIM-like user provisioning, revocation and source-system entitlement reconciliation. Use service-authenticated adapters, never direct writes into upstream tables.

**Trade-off:** SQLite keeps the proof-of-work fully runnable with no external dependencies, but it is not a tested multi-node deployment or a production identity system. Do not enable public development registration, or place this behind an Internet-facing proxy without replacing or hardening the authentication and operations model.
