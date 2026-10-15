# Verification record

Build: VLC Glass Android 0.2.0, arm64, Android 9 or newer, target SDK 35.
This development delivery does not claim complete VLC feature parity.

| Check | Result | Evidence and boundary |
|---|---|---|
| Java compilation | Passed | Android platform 35, JDK 17; deprecated API warnings retained |
| JavaScript syntax | Passed | app.js, player.js, glass.js, host.js, deck.js checked with Node |
| LibVLC API signatures | Passed | 31 typed signatures match the pinned dependency inventory |
| Dependency boundary | Passed | 136 LibVLC classes; donor application components excluded |
| APK resources | Passed | Shared UI and three arm64 native libraries present; signing key excluded |
| APK signature | Passed | apksigner verification, v3 signature; certificate matches 0.1.0 |
| ZIP/native alignment | Inspected | Stored entries aligned to 4 bytes; compressed .so files extract on install. Native ELF LOAD alignment is 0x4000; 16 KiB-device installation is not tested |
| Real local playback | Passed in browser | Three-minute 960×540 H.264/AAC fixture imported through the file chooser, persisted and played to completion |
| Actual quality reporting | Passed in browser | Displayed 960×540 original source dimensions; no fabricated quality ladder |
| Speed | Passed in browser | 1.5× selected and actual HTML video playbackRate read back |
| Hold for 2× | Passed in browser | Pointer held for 1.5 seconds; observer recorded 2×; video rate returned to 1.5× on release |
| Captions | Passed in browser | Real VTT imported through chooser, text track mode showing, caption toggle updated |
| Mute and control lock | Passed in browser | Mute/unmute and lock/unlock exercised at 320 px width |
| Glass modes | Passed in browser | Apple clear and LastWave depth selected through Liquid glass → Emulate mode; settings persisted |
| Responsive player | Inspected in browser | 320×740, 390×844, 844×390, 768×800 and 1120×800 iframe viewports; no page overflow in landscape, tablet and desktop measurements |
| SVG loop | Passed in browser | Flywheel deckTurn runs for 32 seconds per idle revolution; computed transform changes between observations |
| Reduced motion | Passed in browser | In-app switch changes flywheel animation to none; switch restored afterward |
| Seek previews | Implemented, limited verification | Native content-URI extractor and local browser decoder are coalesced and cached; native thumbnail accuracy requires device testing |
| Native lens | Source/build only | AGSL shader, coordinate mapping, RGB dispersion and caption surface separation compile; runtime shader execution not tested |
| Android installation and native playback | Not run | No attached Android device or running emulator |
| Casting, PiP, background audio, lifecycle | Not run | Native handlers/signatures present; receiver and platform behavior need device testing |

Earlier 0.1.0 browser checks covered audio FLAC, pause/rewind, bookmarks and
playlist creation; these were not all repeated in 0.2.0. Browser tests validate
the shared interface and browser adapter, not Android decoding, graphics,
storage providers or services. Before a production release, install and test
on Android 9, 12, 13 and 15 with real codecs, subtitles, a cast receiver,
background/lifecycle transitions and sustained playback.

The supplied REA material informed artifact/source boundaries. Its native
analysis integration was unavailable. Dependency evidence comes from ZIP,
DEX and ELF inspection and does not establish execution of the VLC app.
