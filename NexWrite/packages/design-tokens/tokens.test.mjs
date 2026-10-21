// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import assert from 'node:assert/strict';
const tokens = JSON.parse(readFileSync(new URL('./tokens.json', import.meta.url)));
function luminance(hex) {
  const rgb = hex.match(/[a-f0-9]{2}/gi).map(value => parseInt(value, 16) / 255).map(value => value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4);
  return rgb[0] * .2126 + rgb[1] * .7152 + rgb[2] * .0722;
}
function contrast(a, b) {
  const [low, high] = [luminance(a), luminance(b)].sort((x, y) => x - y);
  return (high + .05) / (low + .05);
}
for (const [name, theme] of Object.entries(tokens.themes)) {
  for (const background of ['background', 'surface', 'surfaceRaised']) {
    for (const foreground of ['text', 'textMuted', 'correctness', 'clarity', 'engagement', 'delivery']) test(`${name}: ${foreground} on ${background} meets normal-text AA`, () => assert.ok(contrast(theme[foreground], theme[background]) >= 4.5));
    test(`${name}: focus on ${background} meets non-text contrast`, () => assert.ok(contrast(theme.focus, theme[background]) >= 3));
  }
  test(`${name}: action label meets AA`, () => assert.ok(contrast(theme.actionText, theme.action) >= 4.5));
}
test('categories carry distinct non-color identities', () => {
  assert.equal(new Set(Object.values(tokens.categories).map(value => value.underline)).size, 4);
  assert.equal(new Set(Object.values(tokens.categories).map(value => value.symbol)).size, 4);
  for (const category of Object.values(tokens.categories)) assert.ok(category.label);
});
test('fonts are configured as pinned self-hosted licensed assets', () => {
  const fonts = JSON.parse(readFileSync(new URL('./fonts.json', import.meta.url)));
  assert.equal(fonts.packages.length, 3);
  for (const font of fonts.packages) { assert.equal(font.version, '5.3.0'); assert.equal(font.license, 'OFL-1.1'); assert.ok(!font.css.includes('://')); }
});
