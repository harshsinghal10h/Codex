// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.dirname(fileURLToPath(import.meta.url));
const tokens = JSON.parse(readFileSync(path.join(root, 'tokens.json'), 'utf8'));
const check = process.argv.includes('--check');
const header = '/* Generated from tokens.json. SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary */\n';
const kebab = text => text.replace(/[A-Z]/g, char => `-${char.toLowerCase()}`);
let css = header;
for (const [theme, values] of Object.entries(tokens.themes)) {
  css += `${theme === 'dark' ? ':root, ' : ''}[data-theme="${theme}"] {\n`;
  css += `  color-scheme: ${theme};\n`;
  for (const [name, value] of Object.entries(values)) css += `  --nw-${kebab(name)}: ${value};\n`;
  css += '}\n';
}
css += ':root {\n';
for (const [name, family] of Object.entries(tokens.fonts)) {
  if (!name.endsWith('Fallback') && name !== 'fallback') css += `  --nw-font-${name}: "${family}", ${name === 'mono' ? tokens.fonts.monoFallback : tokens.fonts.fallback};\n`;
}
for (const value of tokens.spacing) css += `  --nw-space-${value}: ${value}px;\n`;
for (const value of tokens.radii) css += `  --nw-radius-${value}: ${value}px;\n`;
for (const [name, value] of Object.entries(tokens.motion)) css += `  --nw-motion-${name}: ${value}ms;\n`;
css += '}\n';
for (const [name, category] of Object.entries(tokens.categories)) css += `[data-category="${name}"] { text-decoration-line: underline; text-decoration-style: ${category.underline}; text-decoration-color: var(--nw-${name}); }\n`;
css += '.nw-glass { background: var(--nw-surface); border: 1px solid var(--nw-text-muted); }\n';
css += `@supports (backdrop-filter: blur(1px)) { [data-glass="on"] .nw-glass { background: ${tokens.glass.fill}; border-color: ${tokens.glass.border}; backdrop-filter: blur(${tokens.glass.blur}px) saturate(${tokens.glass.saturation}%); box-shadow: ${tokens.glass.shadow}; } }\n`;
css += '@media (prefers-reduced-motion: reduce) { :root { --nw-motion-fast: 0ms; --nw-motion-normal: 0ms; --nw-motion-slow: 0ms; } }\n';
css += '@media (forced-colors: active) { .nw-glass { background: Canvas; border-color: CanvasText; backdrop-filter: none; } :root { --nw-text: CanvasText; --nw-background: Canvas; --nw-focus: Highlight; } }\n';

// Platform clients consume these values when their milestones begin.
const outputs = {
  'tokens.css': css,
  'tokens.platform.json': `${JSON.stringify({ themes: tokens.themes, fonts: tokens.fonts, spacing: tokens.spacing, radii: tokens.radii, motion: tokens.motion, categories: tokens.categories }, null, 2)}\n`,
  'tailwind-theme.mjs': `// Generated. SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary\nexport default ${JSON.stringify({ colors: Object.fromEntries(Object.keys(tokens.themes.dark).map(name => [name, `var(--nw-${kebab(name)})`])), spacing: Object.fromEntries(tokens.spacing.map(value => [value, `${value}px`])), borderRadius: Object.fromEntries(tokens.radii.map(value => [value, `${value}px`])) }, null, 2)};\n`
};
let compose = '// Generated. SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary\npackage com.nexwrite.design.generated\n\nimport androidx.compose.ui.graphics.Color\n\nobject NexWriteColors {\n';
for (const [theme, values] of Object.entries(tokens.themes)) {
  compose += `    object ${theme[0].toUpperCase() + theme.slice(1)} {\n`;
  for (const [name, value] of Object.entries(values)) compose += `        val ${name} = Color(0xFF${value.slice(1).toUpperCase()})\n`;
  compose += '    }\n';
}
outputs['compose/NexWriteColors.kt'] = `${compose}}\n`;
for (const name of Object.keys(tokens.themes.dark)) {
  const color = (theme, appearance) => ({ idiom: 'universal', ...(appearance ? { appearances: [{ appearance: 'luminosity', value: appearance }] } : {}), color: { 'color-space': 'srgb', components: { red: `0x${theme[name].slice(1, 3)}`, green: `0x${theme[name].slice(3, 5)}`, blue: `0x${theme[name].slice(5, 7)}`, alpha: '1.000' } } });
  outputs[`swift/NexWriteColors.xcassets/${name}.colorset/Contents.json`] = `${JSON.stringify({ info: { author: 'NexWrite token generator', version: 1 }, colors: [color(tokens.themes.light), color(tokens.themes.dark, 'dark')] }, null, 2)}\n`;
}
for (const [name, content] of Object.entries(outputs)) {
  const file = path.join(root, 'generated', name);
  if (check) {
    let actual;
    try { actual = readFileSync(file, 'utf8'); } catch { throw new Error(`Missing generated token file: ${name}`); }
    if (actual !== content) throw new Error(`Generated token drift: ${name}; run pnpm tokens`);
  } else {
    mkdirSync(path.dirname(file), { recursive: true });
    writeFileSync(file, content);
  }
}
console.log(`Design token outputs ${check ? 'match' : 'generated'}.`);
