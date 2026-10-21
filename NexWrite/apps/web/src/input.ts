// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
export const MAX_BYTES = 1_048_576;

/** TextEncoder replaces lone surrogates; reject them before encoding. */
export function inputError(text: string): string | null {
  for (let i = 0; i < text.length; i++) {
    const unit = text.charCodeAt(i);
    if (unit >= 0xd800 && unit <= 0xdbff) {
      const next = text.charCodeAt(++i);
      if (!(next >= 0xdc00 && next <= 0xdfff)) return 'The source contains an unpaired Unicode surrogate. Remove it to analyze or export.';
    } else if (unit >= 0xdc00 && unit <= 0xdfff) {
      return 'The source contains an unpaired Unicode surrogate. Remove it to analyze or export.';
    }
  }
  if (new TextEncoder().encode(text).length > MAX_BYTES) return 'The source exceeds the 1 MiB UTF-8 limit. Shorten it to analyze or export.';
  return null;
}
