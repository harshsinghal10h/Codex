# Third party notices

## VideoLAN / LibVLC

LibVLC 3.7.6, copyright VideoLAN and contributors. The engine and Java bindings
retain their original binary implementation. LibVLC's licensing and the licenses
of its codec dependencies remain applicable; this repository's application
license does not replace them.

- Android bindings and source: https://code.videolan.org/videolan/libvlcjni
- VLC source: https://code.videolan.org/videolan/vlc
- Maven coordinate: `org.videolan.android:libvlc-all:3.7.6`
- Android app reference source: https://github.com/videolan/vlc-android
- Dependency carrier: https://github.com/AndreyPavlenko/Fermata/actions/runs/35517061248
- Carrier commit: `7d2bd42e0fea98a796ba069b56c48ef07d874549`
- Carrier archive SHA-256:
  `6ce7e21f3612357d7b4797305040386e708b8e124d640d195db188348506872d`

The carrier application's classes are excluded. `extract_engine.py` selects
only `org.videolan.libvlc` definitions and the two native engine libraries.
D8 re-encodes the selected definitions into a 126 KiB dependency. The provenance
file retains the original selected-class names and individual library hashes.
The carrier's declaration establishes the dependency version; the artifact
digest establishes byte identity, not independent authentication of its build.

## Anatomy, Liquid Glass, Arc

MIT licenses supplied with the uploaded repositories are retained under
`licenses/`. The Anatomy kit is included under `vendor/anatomy/`. The native
lens combines the supplied Liquid Glass material guidance with VASO-inspired
convex refraction, adapted to AGSL.
The React/WebGPU Liquid Glass package and Arc React components are not bundled
as runtime dependencies in this Android application.

## VASO

Source: https://github.com/huozhi/vaso
Author: huozhi. The supplied repository's package.json and README declare MIT;
it does not include a separate LICENSE file. `licenses/VASO-MIT.txt` records
that declaration, attribution and the standard MIT terms. The convex squircle
profile, Snell-law refraction and SVG displacement approach inform the new
dependency-free material renderer. No VASO React runtime is bundled.

## LastWave

Visual reference: https://github.com/Clash-Projects/LastWave-Native
Inspected commit: `15bafdbaed6ce447f6ca86314bf102a468ed8236`.
No LastWave source or binaries are included.

## Application source

New application code is provided under GPL-3.0-or-later. Dependencies retain
their respective original licenses. The product name identifies an independent
personal build and does not imply endorsement by VideoLAN or Apple.
