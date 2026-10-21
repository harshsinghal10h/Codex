# SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
FROM node:24.19.0-bookworm
ARG RUST_VERSION=1.99.1
ENV RUSTUP_HOME=/usr/local/rustup CARGO_HOME=/usr/local/cargo
ENV PATH=/usr/local/cargo/bin:$PATH
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl git build-essential pkg-config python3 jq ripgrep \
    && rm -rf /var/lib/apt/lists/* \
    && curl --fail --show-error --silent --proto '=https' --tlsv1.2 https://sh.rustup.rs -o /tmp/rustup-init.sh \
    && sh /tmp/rustup-init.sh -y --no-modify-path --default-toolchain "$RUST_VERSION" --profile minimal --component rustfmt,clippy --target wasm32-unknown-unknown \
    && rm /tmp/rustup-init.sh \
    && case "$(dpkg --print-architecture)" in \
       amd64) GITLEAKS_ARCH=x64; GITLEAKS_SHA=551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb ;; \
       arm64) GITLEAKS_ARCH=arm64; GITLEAKS_SHA=e4a487ee7ccd7d3a7f7ec08657610aa3606637dab924210b3aee62570fb4b080 ;; \
       *) exit 1 ;; esac \
    && curl --fail --show-error --silent --location "https://github.com/gitleaks/gitleaks/releases/download/v8.30.1/gitleaks_8.30.1_linux_${GITLEAKS_ARCH}.tar.gz" -o /tmp/gitleaks.tar.gz \
    && printf '%s  /tmp/gitleaks.tar.gz\n' "$GITLEAKS_SHA" | sha256sum --check \
    && tar -xzf /tmp/gitleaks.tar.gz -C /usr/local/bin gitleaks && rm /tmp/gitleaks.tar.gz \
    && corepack enable && corepack prepare pnpm@12.14.0 --activate \
    && chown -R node:node /usr/local/rustup /usr/local/cargo
USER node
