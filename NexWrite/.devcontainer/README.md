# Offline core container

Open the **NexWrite directory** as the VS Code workspace, then run
“Dev Containers: Reopen in Container”. The image installs the pinned Node/Rust
toolchains and checksum-verified Gitleaks; initialization installs the pinned
build utilities and frozen project dependencies. Initial creation requires access
to the declared registries and official tool download hosts.

Run `just check`, `just test`, `just build core`, and `just build wasm` after
initialization. This configuration prepares the offline core; optional backend
services can be managed with Docker Compose on the host. The container does not
mount the host Docker socket. In managed cloud environments, use that platform's
configured proxy and CA trust when building containers.

Local validation covered JSON and Dockerfile shell syntax only. The image has not
been built or started during this bootstrap; no container runtime compatibility
result is claimed.
