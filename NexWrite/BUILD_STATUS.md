<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# Task 0 build status

The NexWrite bootstrap is implemented and tested locally on Linux x86_64.
It checks one original plain-text double-space rule through a real Rust engine,
JSON CLI and compiled WASM. The complete writing product and its platform
integrations remain the M1–M11 roadmap. No feature is marked CI-verified.

## Implemented

- Six Rust crates, Unicode UTF-8/UTF-16/scalar spans, grapheme helpers, strict
  input validation, a tested YAML rule, native CLI and Node WASM binding.
- Canonical Suggestion schema and reproducible Rust, TypeScript, Kotlin and
  Swift types. TypeScript compilation passed; Kotlin/Swift compilation was not run.
- Original dark/light tokens, CSS/Tailwind/Compose/Swift outputs, self-hosted
  font configuration, and 46 token checks within the full Node suite.
- All 149 requested ledger rows, 10 ADRs, legal/source inventories, threat model,
  handoffs, SHA-pinned CI, dependency/secrets gates and development configuration.
- REA 6.4.0 audit of the final own WASM JavaScript bridge. It retains 199
  semantic unknowns and does not prove native behavior or runtime privacy.

## Executed checks

The final coordinator run passed all 9 command groups with exit code 0:

```sh
just check
just test
just build core
just build wasm
pnpm build
pnpm --filter @nexwrite/protocol check
just compose-check
just secrets
just supply-chain
```

`just test` ran 63 Node tests and 14 native tests, with no failed or skipped
tests. WASM smoke separately checked 7 native-parity cases, rejected 13 invalid
versions and 7 invalid text values, rejected invalid locales, and preserved the
maximum safe document version. The standalone WASM command was tested with the
previous native CLI executable moved aside; it recreated its own prerequisite.

Actual logs and exit codes: `docs/ledger/evidence/2026-10-19-bootstrap-validation.txt`
and `2026-10-19-bootstrap-validation-summary.json` (EV-20261019-001).
The complete cloud setup script was also executed successfully after installation.
Initial failures are retained separately and superseded by this final run.

Cargo advisory, source, license and dependency gates pass; npm audit reports no
known vulnerabilities at this run. Gitleaks found no source leaks. The initial
unmaintained YAML dependency was replaced by maintained safe parsers, preserving
the rule tests. No security gate was disabled.

## Artifacts and measurements

- Native executable: `target/release/nw` (current Linux machine only).
- WASM/JavaScript: `target/wasm-node/`, generated locally and excluded from Git.
- Final WASM SHA-256:
  `48b4bddac19a7ef7d4b8986056b109b35cf8cab75718cfa40725cb5592d49300`.
- Node gzip: 541,253 bytes, below the bootstrap's 3 MiB budget.
- Single-rule full-check benchmark, 100 iterations: p95 0.395080 ms for 1k
  words and 7.385344 ms for 5k words. Linux x64, 5 available CPU threads,
  approximately 33 GiB host memory; concurrent release compilation affected
  timing. This excludes file I/O/compilation and is not an incremental, mobile
  or mature-engine performance result.

Kotlin/Swift clients, other operating systems, browsers and real devices were
not executed. CI has been configured but has not run. The devcontainer was
syntax-checked; its image was not built. Seven optional service configurations
were validated with loopback port bindings; no backend services were started.

## Repository, environment and delivery

Existing repository: `harshsinghal10h/Codex`; branch: `feat/nexwrite/bootstrap`.
Base commit: `4e359c14c2feee5a38239c885fa1bb9221137e14`.
Existing VLC-Glass/OpenSuite-One files are preserved.

The GitHub API read-only probe returned `Forbidden`; no branch was pushed and no
pull request was created. A local commit plus patch/bundle delivery is provided
under `/workspace/artifacts/`. See its delivery instructions for the resulting
commit ID and import commands. No remote artifact URLs are asserted.

The environment draft preserves VLC setup and adds tested NexWrite installation
and activation instructions. It also adds `api.github.com` and
`www.grammarly.com` to the saved network policy for the blocked GitHub API and
bounded legal-source checks. Draft persistence is confirmed; those changes have
not been applied or published. Review and save them in environment settings,
then publish to retain the prepared filesystem. A new-task restoration has not
been tested.

Current public terms were not retrieved because the proxy denied the request.
Name clearance, legal review, signing, SBOM/release attestations and all later
product milestones remain open in the ledger. Default first-party licensing is
all rights reserved, as requested in the brief.

## Next five tasks

1. Apply the saved narrow network additions; recheck GitHub API and the bounded
   public-source lookup using existing authentication.
2. Publish the branch/PR and run the three-OS CI; inspect outcomes before any
   ledger row is promoted to verified.
3. Resolve the recorded name, license and current-terms review requirements.
4. Execute a bounded M1 slice: licensed dictionary/rule expansion with original
   examples, false-positive evaluation and Unicode/performance regressions.
5. Continue M1 gates before backend/web and platform milestones; keep each
   unimplemented integration visibly planned.
