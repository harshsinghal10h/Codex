# Threat model (initial; NOT a completed security audit)

| Threat | Mitigation in current slice | Remaining risk |
| --- | --- | --- |
| Cross-tenant IDOR | Tenant filter on every CRUD/search query, isolation HTTP regression tests | No systemic ORM/RLS or independent pentest |
| Password theft | scrypt salted password hashes; no passwords returned | No MFA, reset/recovery, breach password screening |
| Session hijacking | Random session secret, hashed at rest, HttpOnly SameSite cookie, expiry | No device/session inventory; reverse proxy TLS required |
| Cross-site request forgery | Header CSRF token, same-origin guard, strict cookie | Origin assumptions require deployment proxy audit |
| XSS/content injection | Escaped user text, CSP, no dynamic eval | Must run automated DOM security scanner |
| Credential exposure | Nextcloud secret server-side via env; not logged or sent to client | Secret rotation, vault and at-rest encryption need implementation |
| Brute force/DoS | In-memory signup/login throttle, request size cap | Shared distributed rate limit, upstream quotas and WAF absent |
| Audit tampering | Tenant-scoped write event log | No signatures, append-only storage or retention enforcement |
| SSRF / upstream provider | Nextcloud URL set only by trusted operator, HTTPS required, no URL input | DNS/IP egress restrictions and safe redirect tests not implemented |
| Database loss | SQLite WAL persisted on disk | Encrypted backup, PITR, restore drill and migrations not verified |

Security release is **blocked** until Keycloak/identity integration, provider ACL enforcement, resource quotas, incident controls, backups, penetration testing and abuse controls are validated.
