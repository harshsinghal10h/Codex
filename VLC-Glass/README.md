# VLC Glass 0.3.1

An independent LibVLC player for Android and desktop with shared, responsive liquid glass controls. It is not an official VideoLAN release.

## Play and protect a video

Open a local video. Select the shield in the player or **More → Scene guard**, then enable **Automatic scene skipping**. The first scan runs locally before protected playback starts. Reopening an unchanged file reuses a map verified against its complete SHA-256, the detector hash, and the analysis policy.

The bundled NudeNet ONNX detector samples frames every 500 ms, checks near detected boundaries at 100 ms, and adds padding. Playback uses a 50 ms schedule lookup with a speed-adjusted lead, masking and muting during a protected seek. Failed scans keep protected playback paused. This can miss content or skip innocent scenes. It does not guarantee frame-exact recognition, detect every kind of sexual activity, or protect live streams and casting.

## Find a described moment

The glass prompt above the timeline searches the current video's SentrySearch index. Results show timestamp ranges and embedding similarity, not a calibrated probability. Choose a result to seek to that range. Weak matches are labelled.

On desktop, open video search setup, save your Gemini API key and select **Index this video**. Cloud indexing sends overlapping video chunks to Google. It is an explicit action and needs your own API access. No key is included. Local Qwen indexing is available from a source installation with the upstream optional dependencies and weights; these large dependencies are not included in the desktop executable.

On Android, scene skipping runs on the phone. Description search connects to the SentrySearch companion included in the desktop/source distribution. Start it with a token file:

```sh
python desktop/main.py --companion --token-file /private/path/companion-token.txt
```

Set `GEMINI_API_KEY` in the companion's environment. It listens on localhost by default. A LAN listener requires HTTPS:

```sh
python desktop/main.py --companion --bind 0.0.0.0 --cert certificate.pem --key private-key.pem --token-file /private/path/companion-token.txt
```

Use a certificate trusted by the phone. Enter its URL and token in the Android search setup, then explicitly index the selected video. The companion accepts uploaded media IDs, not arbitrary filesystem paths. Raw uploads and indexes are stored in the companion's selected data directory. API tokens and prompts are excluded from request logs. The first index can take substantial time; searching an existing index is a separate operation.

## LAYA

The supplied LAYA SDK is preserved under `upstream/laya`. It is a text decision model, not a visual nudity detector. `intelligence/laya_video.py` adds a single resident checkpoint, warm-up, batched questions, bounded evidence, inference mode and an LRU cache. Its output is advisory and cannot clear positive visual detections or unscanned video.

No trained weights were present in the supplied archive. No video-domain fine-tuning, calibration, recognition-accuracy improvement or device-wide latency figure is claimed. For a source installation with LAYA's dependencies and a local checkpoint, set `LAYA_MODEL_DIR` and optionally `LAYA_DEVICE`. The desktop executable does not bundle PyTorch or LAYA weights. Inference stays outside playback scheduling.

## Platforms and builds

| Target | Package | Minimum target / scope |
| --- | --- | --- |
| Android arm64 | APK | Android 9+, phones, tablets and compatible TVs |
| Android armv7 | APK | Android 9+, 32-bit ARM |
| Android x86_64 | APK | Android 9+, compatible x86 devices/emulators |
| Android universal | APK | Includes the three architectures above |
| Windows x64 | EXE | Windows 10/11 target; packaged runtime tested in CI |
| macOS arm64 / x64 | App ZIP | Separate native builds; see individual CI results |
| Linux x64 | TAR.GZ | Built on Ubuntu 22.04; other distributions require validation |

These are development builds. Native iOS, Windows ARM64, Android before 9, commercial signing/notarization, and an exhaustive OS/device certification matrix are not supplied. Browser preview supports ordinary media playback; native analysis features explain their limits.

```sh
python3 tools/prepare_dependencies.py
npm ci
python3 tools/build.py --keystore /private/path/signing.p12 --universal
```

The Android build needs JDK 17. It uses pinned official LibVLC and ONNX Runtime AARs. Runtime 1.23.2 supplies 16 KB-aligned 64-bit native libraries. Production signing keys must stay private. Rebuilding without the existing development key creates a different signer.

For desktop, install `desktop/requirements.txt`, prepare inputs with `python tools/prepare_dependencies.py --desktop`, and run `python tools/build_desktop.py`. Official platform VLC libraries are collected by the build script. Native binaries and detector bytes are fetched at build time and bundled for playback; they are not downloaded while watching.

```sh
python -m unittest discover -s tests -p 'test_*.py' -v
npm run check
npm run dev -- --host 0.0.0.0 --port 4173
```

## Included controls

Local library, folders, playlists, favorites, saved positions, queue, tracks and captions, playback speed, equalizer, bookmarks, A–B loop, gestures, picture in picture on Android, two saved glass finishes, reduced motion and animated Anatomy artwork. The desktop host uses native LibVLC; desktop receiver discovery is not implemented. Desktop bookmarks are currently session-local. This does not implement every VLC feature.

## Notices

Application source retains GPL-3.0. SentrySearch and LAYA retain Apache-2.0. NudeNet's bundled license text is AGPL-3.0, despite conflicting MIT package metadata; the supplied license is included rather than assuming a permissive grant. Full corresponding application/adapter source is provided. See `NOTICE.md` and `licenses/` for dependencies and attribution.

Android packages lower UI JavaScript/CSS syntax for the stock Android 9 WebView and include small API/layout fallbacks. Modern WebViews retain their supported glass effects. macOS uses a LibVLC frame-callback surface by default so video presentation works without an embedded legacy OpenGL context; it keeps only the latest frame and bounds software presentation to 3840×2160. Physical-device frame rate, HDR, and high-resolution energy use remain unmeasured. Source users can opt into native Cocoa output with `VLCGLASS_NATIVE_VIDEO=1`.
