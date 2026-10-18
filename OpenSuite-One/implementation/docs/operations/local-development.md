# Local development and operational boundaries

1. Use Node 22.13+ (`node:sqlite` is experimental in Node 22), with no npm install required for the supplied first-party code.
2. Set `DEV_ALLOW_REGISTRATION=1` **only for local testing**, run `npm start` and create workspace at `http://127.0.0.1:4173`.
3. For a non-development environment, create an administrator with `npm run bootstrap -- 'Workspace' 'Name' owner@example.com` and `OPENSUITE_BOOTSTRAP_PASSWORD` exported in the process environment. Keep registration disabled.
4. Data are in `data/opensuite.sqlite` by default. Before backup, stop all writers or use SQLite online backup mechanisms; copying an open WAL database file alone may lose committed writes.
5. To configure the optional Nextcloud **read-only names listing**, provision a dedicated Nextcloud app password and set `NEXTCLOUD_BASE_URL`, `NEXTCLOUD_USERNAME`, `NEXTCLOUD_APP_PASSWORD`, and `NEXTCLOUD_TENANT_ID` to the exact authorized workspace ID. The tenant ID appears in the `/api/me` response. HTTPS mandatory. This integration is not validated against an actual Nextcloud service in this release.
6. No production reverse proxy templates, mail DNS/anti-abuse controls, TLS certificate provisioning, mobile signing, restore drills, logging retention or SLA are provided. All are release blockers.

**Incident response in this release:** stop server and isolate database/secret environment; rotate the Nextcloud app password; revoke local sessions by deleting `sessions` rows via a SQLite client while server is stopped; preserve logs and database for investigation; restore a consistent tested backup when available. This is a development runbook, not a validated enterprise disaster-recovery plan.
