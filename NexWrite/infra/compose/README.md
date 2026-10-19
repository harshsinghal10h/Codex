# Optional development services

Core analysis and its tests work without these services or external credentials.
`just dev-env` creates an ignored `.env` using independent cryptographic random
passwords and preserves existing files. `just services` starts the backend profile;
`just services-down` stops it without deleting volumes. Add `--profile observability`
to the same Compose invocation for the local collector and Grafana.

`just compose-check` validates all seven service definitions and loopback port binds
without pulling images, printing credentials, or starting containers. Configuration
validation does not establish service health, image availability, connectivity,
or application integration. Runtime checks belong to the backend milestone.

These are local development settings. Mailpit and NATS intentionally run without
authentication on the development network. The collector discards incoming traces;
Grafana has no metrics data source or dashboards yet. Do not use this configuration
for deployment or send sensitive document contents to telemetry.
