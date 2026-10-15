# VLC Glass 0.3.1

Responsive liquid glass controls, a video-moment prompt connected to pinned SentrySearch, and local automatic scene skipping using the bundled NudeNet ONNX detector.

Scene guard scans a local video before protected playback starts. It caches maps against the full media SHA-256 and model/policy version, checks every 500 ms during scanning, refines detected boundaries at 100 ms, and adds safety padding. Playback uses a 50 ms scheduler, pre-seek masking and muting. Detection can miss content or produce false positives. Sampling is not frame-exact recognition. Live streams and casting are not protected.

Desktop search indexes selected videos with SentrySearch. Gemini requires the user's API key and sends chunks to Google. The local Qwen backend needs separately installed weights and dependencies. Android search uses the authenticated desktop companion; automatic scene skipping runs locally on Android.

LAYA's supplied text-decision code is included with a preloaded, cached advisory adapter. No model weights were supplied, and no fine-tuned checkpoint or video-domain accuracy result is claimed. LAYA is not a visual detector and is not called from the playback loop.

Development builds. Package smoke tests and timeline tests do not establish physical-device playback, classifier accuracy, HDR, casting, or lifecycle reliability. Windows and macOS builds are not commercially signed/notarized. No native iOS or Windows ARM64 build is included. Use the architecture-specific download for your device.

## Verified downloads and source

The four desktop packages passed native playback, local scanning, guarded replay, protected Previous, actual screen composition, and prompt layout checks in CI. Android phone and tablet checks use a safe synthetic fixture; they do not evaluate recognition accuracy. Per-platform reports and SHA-256 checksums are attached.

Use the attached **VLC-Glass-0.3.1-source.zip** for matching current application source. The prerelease tag is retained; the automatic GitHub tag archive predates these fixes. `Desktop-build-0.3.1.json` and `Android-playback-checks-0.3.1.json` record the exact source and verification commits. The current source is also on the `vlc-glass-0.3.0` branch.
