<!-- SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary -->
# NexWrite tokens

`tokens.json` is the source for CSS variables, a Tailwind theme, Compose colors,
Swift color assets, and a neutral platform JSON file. Run `pnpm tokens` to
regenerate and `node packages/design-tokens/generate.mjs --check` to reject drift.
Native color artifacts are generated configuration; native clients are later
milestones and have not been compiled or tested here.

The default material is solid. Glass is an explicit `data-glass="on"` option
with a solid fallback. Reduced-motion and forced-colors rules are included.
Category labels, symbols, and underline styles convey meaning alongside color.
`decoration` is decorative, not a body-text color. Light primary actions use a
darker green to retain text contrast while preserving the brand accent.

`node --test packages/design-tokens/tokens.test.mjs` checks 46 contrast, identity,
and font-configuration cases. These mathematical checks do not certify a future
screen's accessibility. Screens still need keyboard, screen-reader, zoom,
layout, and glass-background testing.

`fonts.json` declares exact Fontsource packages and their OFL-1.1 licenses.
Consumers import the listed local package CSS so bundlers copy the relative font
assets into their own output. No runtime font CDN is used. The system fallback
works without font downloads, and no font binaries are committed. See
`docs/legal/THIRD_PARTY.md` for attribution and source records.
