# SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
# Build from NexWrite/; provide the managed-cloud CA only as a BuildKit secret.
FROM rust:1.99.1-bookworm AS core
WORKDIR /build
COPY Cargo.toml Cargo.lock ./
COPY core ./core
COPY rules ./rules
COPY packages/protocol/schema ./packages/protocol/schema
RUN --mount=type=secret,id=proxy_ca \
    if [ -f /run/secrets/proxy_ca ]; then export CARGO_HTTP_CAINFO=/run/secrets/proxy_ca; fi; \
    cargo build --release --locked -p nw-cli

FROM node:24.19.0-bookworm-slim AS app
WORKDIR /build
RUN --mount=type=secret,id=proxy_ca \
    if [ -f /run/secrets/proxy_ca ]; then export NODE_EXTRA_CA_CERTS=/run/secrets/proxy_ca; fi; \
    npm install --global pnpm@12.14.0
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY packages/protocol ./packages/protocol
COPY services/api ./services/api
RUN --mount=type=secret,id=proxy_ca \
    if [ -f /run/secrets/proxy_ca ]; then export NODE_EXTRA_CA_CERTS=/run/secrets/proxy_ca; fi; \
    pnpm --filter @nexwrite/api... install --frozen-lockfile
RUN pnpm --filter @nexwrite/protocol build && pnpm --filter @nexwrite/api build
RUN pnpm --filter @nexwrite/api deploy --prod --legacy /opt/api

FROM node:24.19.0-bookworm-slim AS runtime
ENV NODE_ENV=production NEXWRITE_HOST=0.0.0.0 NEXWRITE_PORT=8787 NEXWRITE_CLI=/usr/local/bin/nw
WORKDIR /opt/api
COPY --from=core /build/target/release/nw /usr/local/bin/nw
COPY --from=app --chown=node:node /opt/api ./
USER node
EXPOSE 8787
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:8787/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["node", "dist/main.js"]
