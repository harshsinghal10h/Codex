<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# Clean-room record

Date: 2026-10-19. Evidence types: E4 operator requirements and observed local setup; no reference-product runtime evidence was used.

The project implements original behavior from the operator brief, official platform documentation and license-reviewed OSS. No reference application, extension, installer, web bundle or private endpoint was inspected or audited. No reference outputs are an oracle or training source. The supplied REA and liquid-glass archives were inspected as source/license references outside this project; no code, fonts, assets or third-party binaries from them were imported.

## Current terms check — blocked, not reviewed

Target terms reference: https://www.grammarly.com/terms . Before any terms request, the bounded public-source check attempted https://www.grammarly.com/robots.txt through the configured HTTPS proxy with TLS verification enabled on 2026-10-19 at 10:41 UTC. `curl --proto '=https' --tlsv1.2 --max-time 20 -sS -i` returned exit 56: `CONNECT tunnel failed, response 403`; the proxy response was `HTTP/1.1 403 Forbidden`. Current environment HTTP policy is restricted and does not list this host. No robots or terms content was retrieved; therefore **no current terms excerpt was re-verified**. The terms page was not requested after the robots access failure. UNK-BOOT-02 remains open.

Task 0 requested a current terms excerpt reference. Its exact remedy is an allowed read-only path to the bounded official robots/terms pages or an operator-supplied lawful dated excerpt for legal review. Respect the site's robots rules, no login, no paywall, no mass scraping, no more than one request per second. Record the actual URL, retrieval timestamp, minimal relevant excerpt and interpretation separately when available. Do not present the brief's legal assumptions as a current quotation or legal conclusion.

## Working restrictions

- Evidence classes are E1 public-doc, E2 platform-doc, E3 licensed OSS, E4 operator-note, E5 own-artifact. Every ledger row cites a permitted source.
- Never decompile, unpack, disassemble, deobfuscate or trace the reference product; never capture/replay private traffic; never copy strings, assets, CSS, code, identifiers, rules, dictionaries, models or prompts.
- Never train/distill from reference outputs or use them as expected test results.
- No audit of a third-party binary without a matching operator-signed AUTHORIZATION entry. A source archive supplied in chat does not automatically authorize auditing another binary.
- Analysts write original specs; builders implement from those specs, official docs and licensed OSS. Preserve the evidence wall for subsequent feature work.
- Mark inference and unknowns honestly. Static source review is not runtime verification. No privacy/security/a11y/legal certification is asserted.

## Narrow bootstrap and archival exceptions

The supplied brief both separates feature-spec and implementation PRs in S2 and explicitly requests one original rule through CLI/WASM in the single Task 0 PR (S15). ADR-0002 records the narrow Task 0 interpretation: an analyst writes the original minimal behavior spec before a separate builder implements it, within this one bootstrap deliverable. All later features retain separate spec/implementation PRs. This is a disclosed interpretation, not a claim that the literal instructions are contradiction-free.

S15 also requires saving the entire master prompt verbatim, which includes reference-product words otherwise restricted by S1. `docs/MASTER_PROMPT.md` is archival input, not product source, UI, package name or asset. Do not remove text from it to make a scanner pass. Product-source provenance checks retain the forbidden-term gate. Legal docs and ledger source references may name public sources. The source checker itself may contain the exact forbidden patterns it must detect.
