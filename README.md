# VLC Glass 0.2.0

Independent VLC-powered Android media player with two switchable liquid-glass appearances, responsive controls and looping Anatomy SVG artwork.

The release workflow publishes the signed ARM64 APK and complete source ZIP as normal downloadable release assets under the tag `vlc-glass-v0.2.0`.

## Reconstruct locally

```sh
python3 publish/reassemble.py
```

The complete files appear in `dist/` only after every transfer part, size and final SHA-256 has been checked. Binary parts keep individual ChatGPT GitHub connector requests small. They do not modify, recompress or re-sign the APK.

See `publish/RELEASE.md` for features and testing limits. Extract the source ZIP for native Android and interface source, dependency provenance, licenses, build tools and the detailed verification record. No private signing key is committed.
