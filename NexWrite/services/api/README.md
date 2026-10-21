<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# NexWrite personal backend preview

This stateless service implements real Rust analysis and opt-in writing requests
through independent OpenAI-compatible and Gemini HTTP adapters. It does not
implement accounts or cloud document storage; discovery reports both as false.
The Android APK works locally before this optional backend is configured.

From the NexWrite root, install the locked workspace, build `nw-cli` with Cargo,
build `@nexwrite/protocol`, then build and start `@nexwrite/api`. The default
listener is `127.0.0.1:8787`. `NEXWRITE_CLI` selects the actual executable; its
default is `target/release/nw`. Engine discovery runs the real bundled rule
examples and reports the observed rule count. Missing or invalid core returns
unavailable rather than fabricated analysis.

Server-only configuration:

| Variable | Purpose |
| --- | --- |
| `NEXWRITE_HOST`, `NEXWRITE_PORT` | Listener; a nonloopback host requires a token |
| `NEXWRITE_ACCESS_TOKEN` | Runtime client bearer token, at least 32 characters |
| `NEXWRITE_ALLOWED_ORIGINS` | Comma-separated exact HTTPS web origins; no wildcard |
| `NEXWRITE_OPENAI_KEY`, `NEXWRITE_GEMINI_KEY` | Independent upstream credentials |
| `NEXWRITE_OPENAI_MODELS`, `NEXWRITE_GEMINI_MODELS` | Comma-separated model allowlists |
| `NEXWRITE_OPENAI_URL` | Operator-reviewed full chat-completions URL |
| `NEXWRITE_GEMINI_URL` | Operator-reviewed models-directory URL ending in `/` |
| `NEXWRITE_ALLOW_PRIVATE_UPSTREAM` | `true` only for an explicitly reviewed self-host network |
| `NEXWRITE_RATE_LIMIT` | Requests per client IP per minute, default 60, maximum 600 |

Use a TLS reverse proxy and enter its HTTPS address in the clients. Never put
server keys or tokens in source, release binaries, command history or images.
The browser retains its entered client token in memory; Android uses Keystore.
Provider URLs, keys and model allowlists cannot be selected arbitrarily by a
remote request. HTTPS endpoints reject credentials, query strings and fragments;
redirects are disabled. Public upstream DNS is checked for private addresses
before each request. DNS and the configured endpoint remain operator trust
boundaries: control endpoint DNS, review outbound firewall rules, and permit
private self-host access only deliberately. No provider fallback is performed.

HTTP JSON bodies are limited to 2 MiB and decoded as strict UTF-8. Analysis text
is limited to 1 MiB; writing input/output to 32/64 KiB. Unicode scalars, versions,
closed schemas, consent, models and task/preset combinations are validated.
Analysis uses unique owner-only temporary files, removes them after every result
and cancellation, and bounds subprocess output to 8 MiB. Dense reports can hit
this delivery limit; this does not close the existing core memory unknown.
Analysis concurrency is two and provider concurrency four. A per-IP request
limit and a shared-token/global limit bound the personal deployment; there is
no multiuser quota or tenant claim. The IP registry is bounded to 10,000 entries.

Provider work has a 30-second total timeout and no schema-repair retry. A
disconnected client aborts downstream work. Request logging is disabled, error
messages omit documents/upstream payloads/secrets, and responses use `no-store`.
Prompts isolate the exact document as JSON data and expose no tools. These
boundaries do not establish universal resistance to prompt injection or provider
retention/legal clearance. Proposals still require user review and safe Apply.
The response model identifies the configured routing model; a provider may
resolve an alias to a versioned model internally.

`pnpm --filter @nexwrite/api test` runs the built service against the actual Rust
CLI (`NEXWRITE_CLI` may select `target/debug/nw`) and two local HTTP simulations
of the upstream protocols. These tests cover parity, malformed input, Unicode,
privacy boundaries, consent, hostile source prompts, invalid provider actions,
rate limits, timeouts and downstream cancellation. They are not live-provider,
public-hosting, tenant-isolation or physical-device evidence.

The Dockerfile builds the real Rust CLI and production Node service together.
From the NexWrite root, build with `docker build -f services/api/Dockerfile .`.
In the managed cloud, also pass the inherited proxy build arguments and
`--secret id=proxy_ca,src="$CODEX_PROXY_CERT"`; that CA is mounted only during
dependency installation and is not copied into the runtime image. Rootless
runtime user `node` listens on port 8787 and refuses startup until a runtime
access token is configured. Put the container behind your HTTPS proxy and
provide the operator-reviewed provider configuration at runtime. Building this
file is not a public deployment or live-provider result.
