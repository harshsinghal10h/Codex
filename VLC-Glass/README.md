# VLC Glass

An independent Android media player using LibVLC 3.7.6, a local SQLite library,
and two switchable glass appearances. This is a development build, not an official
VideoLAN release or a complete implementation of every VLC desktop feature.

Install `VLC-Glass-Android-0.2.0-arm64.apk` on a 64-bit Android 9 or newer device.
Open media files or choose a folder from Browse. Change the appearance through
**Settings → Visual & appearance → Liquid glass → Emulate mode**.

## Included behavior

- Local file and folder import through Android's document picker, including
  persistent access grants, thumbnails and saved playback positions.
- Video and audio libraries, search, sort, grid/list views, favorites, history,
  saved playlists, an editable playback queue and multi-file import.
- Native VLC decoding with selectable hardware/software decoding, manually
  opened network locations, a configurable stream cache and external-open intents.
- Native receiver discovery and a cast selector using LibVLC's renderer
  discoverers. A scan runs for twelve seconds; selecting a receiver explicitly
  connects playback. Casting and receiver compatibility require device testing.
- Playback speed, audio/subtitle tracks and delays, subtitle files, chapters,
  bookmarks, A–B looping, repeat, shuffle, volume, sleep timer and aspect ratios.
- YouTube-style control placement: title/back at the upper left; cast, quality
  and settings at the upper right; transport in the center; captions, speed,
  queue, fullscreen and lock near the bottom; a red timeline below them.
  Hold the video for 2× speed and release to restore the previous rate. Double
  tap the sides to seek. Pinch or use the zoom menu to enlarge video.
- Local-file seek thumbnails, chapter markers, mute, replay, caption appearance,
  adjustable control timeout and optional automatic picture in picture.
  Quality reports the actual encoded resolution and exposes genuine video
  track variants when available; it does not invent downloadable resolutions.
- Background audio service, audio-focus handling, headphone-unplug pause,
  media session and playback notification, screen-awake control and picture in picture.
- Apple clear and LastWave depth appearances, with VASO-inspired convex
  refraction, RGB edge dispersion, directional specular highlights and frosting.
  Four saved sliders adjust depth, dispersion, frosting and edge highlights.
  Android 13+ uses a native AGSL lens on the live video texture; earlier
  versions use translucent controls. Native subtitles remain on a separate
  surface and lift above visible lower controls. The browser material uses
  cached SVG displacement filters, without cloning the video or page backdrop.
- A 79-part Anatomy audio console with a continuously turning flywheel and
  three breathing lamps. Its faders follow saved equalizer gains, and its
  carriage follows actual playback time. Loop motion pauses offscreen or when
  hidden; the loop toggle and reduced-motion setting stop decorative animation.
- Responsive layouts for compact phones, landscape, tablets and desktop
  previews, with large touch targets and a high-contrast material option.

## Run the interface preview

```sh
npm run dev -- --host 0.0.0.0 --port 4173
```

No npm dependencies need to be installed. The preview uses actual browser media
playback and IndexedDB. It is useful for checking the interface, file import and
saved preferences; it does not validate Android native decoding. Native-only
features report their limits in the preview. Browser format support depends on
the browser. Open the preview through localhost rather than a `file:` URL.

## Build Android

Install JDK 17, Python 3, Android SDK platform 35 and Android build tools 35.
The source bundle includes the pinned, isolated native dependency and artwork kit.

```sh
python3 tools/build.py --sdk /absolute/path/to/android-sdk
python3 tests/verify_package.py
```

The build creates a local development signing key if no key is supplied. Use
`--keystore /path/to/signing.p12` with the supplied development key to produce
updates compatible with this build. The development key's alias is `vlcglass`
and its password is `android`. It is not suitable for a public production release.
The key is supplied in a separate private backup archive and is excluded from
the application and source bundle.

Regenerate the Anatomy asset with `node tools/build_deck.mjs`. APK assembly
uses the included Python ZIP aligner for 4-byte alignment of stored entries.
Native libraries are compressed and extracted on installation, rather than
being directly mapped from the APK. The original 0.1.0 signing certificate is
retained in the delivered 0.2.0 APK; source rebuilds need the separate private
key to preserve update compatibility.
Native dependency provenance, class inventory and hashes are in `vendor/`.
The dependency preparation script is retained for reproducibility; normal builds
use `vendor/libvlc.dex` and do not need to download the original build artifact.

## Verification and limits

See `QA.md` for the exact checks performed. Java compilation, native API
signature checks, DEX packaging and APK signature verification passed. Browser
checks include real file playback, captions, actual resolution reporting,
1.5× speed, hold-for-2× and restoration, lock/unlock, both glass modes,
320–1120 pixel layouts, looping SVG motion and reduced-motion behavior.

This environment has no running Android device or emulator. Installation,
native decoder initialization, hardware codec behavior, HDR output, subtitle
rendering, background playback, native refraction, casting, PiP and Android lifecycle
behavior remain unverified. Do not treat this build as production certified.

LAN share discovery, optical-disc navigation, capture-device
input, media conversion, transcoding, VLC extensions and the complete VLC
desktop preferences system are not implemented. Network playback depends on
the bundled engine, server and device. It does not bypass DRM or service restrictions.

## Attribution

VideoLAN LibVLC provides the media engine. Its pinned Android dependency is
isolated from an integrity-checked public Fermata build artifact. No Fermata
application components or user interface are included. See `NOTICE.md`.

Anatomy geometry and CSS are used under MIT. The supplied Liquid Glass material
guidance and VASO's convex profile inform the lens. Arc's calm
motion and accessible control patterns inform the interface. LastWave's native
glass source was inspected as a visual reference; its app code is not copied.
The supplied VASO repository declares MIT; attribution is retained under
`licenses/VASO-MIT.txt`. Third-party notices also ship inside the APK assets.
