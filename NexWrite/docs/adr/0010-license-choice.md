<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# ADR-0010: Provisional all-rights-reserved first-party license

Status: accepted for Task 0; future-surface choices remain provisional pending their stated evidence.
Date: 2026-10-19. Source: docs/inputs/operator-brief.md (E4).

## Context

The operator explicitly sets LICENSE_CHOICE=all-rights-reserved until a human decision. No permission to choose an open-source grant or relicense supplied third-party components was given.

## Options

1. MIT/Apache-2.0 first-party grant now: permissive distribution but contrary to the explicit default.
2. Copyleft first-party grant now: another substantive licensing choice not authorized here.
3. All rights reserved for first-party code pending operator decision, preserving third-party licenses.

## Decision

Choose the requested all-rights-reserved default with SPDX LicenseRef-NexWrite-Proprietary on first-party source. The root LICENSE records no general use/redistribution grant. Dependency licenses, font OFL notices and source-input licenses retain their own terms; no vendored code inherits first-party ownership. No code or asset from the uploaded archives is imported.

## Consequences

This is not an open-source release or legal clearance. A license metadata check only establishes declared inventory consistency. Operator and qualified review must settle rights holders, contributions, dependency compatibility and intended distribution before publishing a release. Repository visibility does not itself grant source reuse rights.

## Revisit trigger

An explicit operator license decision, contribution policy, commercial distribution plan or dependency incompatibility requires a reviewed license/header/notice update.
