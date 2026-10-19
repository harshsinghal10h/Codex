<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# Security policy

This is a pre-release Task 0 bootstrap, with no production service or security support promise. Review docs/architecture/threat-models/bootstrap.md and docs/ledger/UNKNOWNS.md before deployment. There is no established monitored disclosure mailbox yet (UNK-BOOT-09).

Use the repository owner's available private security-reporting channel if enabled. If no private channel exists, contact the owner through an established private channel to arrange disclosure. Do not post secrets, real user text, working credentials or private documents in public issues. Maintainers must establish a monitored private channel before inviting production users.

The implemented analysis path is local. Text is input data, never executable instructions. Do not add cloud/LLM/network operations without explicit consent design, least privilege, retention rules and a surface threat model. Never log user document content. Keep locked dependencies, checksum/TLS verification and required secret scanning enabled. Generated schemas and JSON input limits require review when extending the protocol.

Untrusted files may be large or malformed; impose size/time limits before exposing the CLI/engine to a service or hostile host. Preserve all three offset representations and reject invalid byte boundaries. Keep developer compose services private to localhost; local development credentials are not production credentials. Unsigned builds are development artifacts only. Future update channels require signature and rollback protection.

Before any release, complete dependency/license/SBOM review, parser fuzzing, access-control and prompt-injection tests where applicable, least-privilege surface reviews, a secret scan, privacy review, accessibility checks, and reproducible artifact inspection. These are release requirements, not claims that they have already passed.
