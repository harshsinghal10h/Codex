// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
import path from 'node:path';
import { files, isMain, read, report, root } from './lint-common.mjs';

const source = /\.(?:mjs|cjs|js|jsx|ts|tsx|rs|py|sh|kt|kts|swift|css|scss|html|svg)$/i;
const asset = /\.(?:png|jpe?g|webp|gif|ico|avif|woff2?|ttf|otf|mp[34]|wav|pdf|zip|wasm|bin|svg)$/i;
// Keep forbidden strings out of executable product source, including this tool.
const banned = new RegExp(['gram' + 'marly', 'data-' + 'gramm', 'gramm' + 'arly-extension', 'gramm' + 'arly-desktop-integration'].join('|'), 'i');
const spdx = 'SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary';
function registered(document, file) {
  return document.split('\n').some(line => line.startsWith('|') && line.split('|').map(c => c.trim().replaceAll('`', '')).includes(file)
    && line.split('|').filter(c => c.trim()).length >= 4
    && !/\b(?:unknown|TODO|TBD|unreviewed)\b/i.test(line));
}
export function lintProvenance(base) {
  const errors = [];
  const thirdParty = read(base, 'docs/legal/THIRD_PARTY.md');
  const assets = read(base, 'docs/legal/ASSETS.md');
  for (const file of files(base)) {
    if (/^(?:docs|artifacts|ci\/reports)\//.test(file)) continue;
    if (/(?:^|\/)(?:vendor|vendored|third_party|third-party)\//.test(file) && !registered(thirdParty, file)) errors.push(`${file}: vendored path lacks reviewed third-party provenance row`);
    if (asset.test(file) && !registered(assets, file)) errors.push(`${file}: asset lacks author, license and source provenance row`);
    if (!source.test(file)) continue;
    const content = read(base, file);
    if (!content.split('\n').slice(0, 5).some(line => line.includes(spdx))) errors.push(`${file}: missing required SPDX header`);
    if (banned.test(content) || banned.test(path.basename(file))) errors.push(`${file}: forbidden reference identifier in source`);
  }
  return errors;
}
if (isMain(import.meta.url)) {
  try { report('provenance-lint', lintProvenance(root)); } catch (error) { report('provenance-lint', [error.message]); }
}
