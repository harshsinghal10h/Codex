# HTTP API v0.1 (documentation, not generated OpenAPI)

All endpoints send JSON except static assets. Authentication uses a secure-by-deployment `opensuite_session` HttpOnly cookie and `X-CSRF-Token` for mutations after sign-in. The CSRF value is returned by login/registration and retained by the web client in session storage. All authenticated resource operations are scoped to the current tenant. Standard error: `{ "error": {"code":"...","message":"...","requestId":"..."} }`.

| Method | Endpoint | Function |
| --- | --- | --- |
| GET | `/api/health`, `/api/meta` | Public service metadata |
| POST | `/api/register` | Local-only organization signup when enabled |
| POST | `/api/login`, `/api/logout` | Credential sign-in and revoke session |
| GET | `/api/me`, `/api/summary` | Identity/tenant and actual record counts |
| GET, POST | `/api/tasks`, `/api/notes`, `/api/events`, `/api/contacts` | List and create records |
| GET, PATCH, DELETE | `/api/{kind}/{uuid}` | Read, update by `version`, delete authorized record |
| GET | `/api/search?q=...` | Tenant-limited title/name search |
| GET | `/api/notifications` | Per-user notifications |
| POST | `/api/notifications/read` | Mark one per-user notification read |
| GET | `/api/files` | Optional Nextcloud WebDAV display-name listing |
| GET | `/api/integrations` | Honest configuration status |
| GET | `/api/admin/members`, `/api/admin/audit` | Owner/admin-only access |

On record edits, include `version` from GET to detect stale changes. For idempotent creation, use a unique `Idempotency-Key` on POST. Access to another tenant's record returns HTTP 404 without exposing its existence. This is an intentionally narrow contract: no implied federation, rich document editing, uploads or provisioning APIs.
