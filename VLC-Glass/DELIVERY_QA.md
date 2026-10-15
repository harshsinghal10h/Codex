# VLC Glass delivery verification

The 0.2.0 application bytes are unchanged from the recovered build. The outstanding delivery step was publishing the complete APK and source to the primary GitHub account. The upload transport requires small parts; the GitHub job checks every part, reconstructs the original artifacts, verifies their SHA-256 and Git blob identities, repeats package checks, and publishes ordinary complete downloads.

| Current check | Result |
| --- | --- |
| APK SHA-256 against recorded build evidence | Passed |
| JavaScript syntax for all five application scripts | Passed |
| 31 typed LibVLC API signatures | Passed |
| Only application and 136 isolated LibVLC classes in DEX | Passed |
| Signing key and donor application components excluded | Passed |
| All 15 UI and notice assets match APK byte for byte | Passed |
| All three native libraries match APK byte for byte | Passed |
| Fresh browser automation | Unavailable: no browser executable installed in this runtime |
| Android installation and native playback | Not run: no connected device or emulator |

The prior browser test record is retained unchanged in `source/QA.md`. It covers local playback, speed, captions, lock, glass modes, responsive widths, SVG animation and reduced motion. It is not a fresh run and does not establish native Android behavior. Native lens rendering, hardware decoding, HDR, background playback, storage-provider grants, casting, PiP and lifecycle behavior require device testing.

The build does not include complete VLC desktop feature parity. Optical-disc navigation, conversion/transcoding, capture devices, LAN share discovery and VLC extensions are not implemented. This is a development delivery.
