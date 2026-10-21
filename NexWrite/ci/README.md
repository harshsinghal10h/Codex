# Bootstrap checks

The runnable workflow is at the repository root in
`.github/workflows/nexwrite-ci.yml`; GitHub does not load nested workflows.
Every third-party action is pinned to a full commit SHA. Native core builds run
on Linux, Windows and macOS. Linux additionally builds WASM, runs its Node smoke
test, audits only our own generated artifacts with REA, and checks dependencies,
licenses, secrets, ledger invariants, generation drift and Compose configuration.

These jobs do not build browser, mobile, desktop UI, Office or Google integrations:
those product surfaces are planned ledger rows. No platform stub is presented as
a working app. CI results exist only after the workflow runs in GitHub; local
results must remain `kind: local` evidence.

Tool versions used here: Node 24.19.0, pnpm 12.14.0, Rust 1.99.1,
wasm-bindgen-cli 0.2.129, cargo-deny 0.20.2, Gitleaks 8.30.1 and REA 6.4.0.
Gitleaks installation checks the pinned official Linux archive SHA-256.
