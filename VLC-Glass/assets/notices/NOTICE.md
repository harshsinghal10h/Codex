# Third-party notices

VLC Glass is an independent application, not an official VideoLAN release.

The application source retains GPL-3.0. Android uses the official Maven `org.videolan.android:libvlc-all:3.7.6` artifact. Desktop uses the official platform VLC libraries. Their VideoLAN notices and license obligations apply. Build input URLs and hashes are recorded in `vendor/build-inputs.json`. No donor application code is included in new Android packages.

SentrySearch is pinned to ssrajadh/sentrysearch commit ab688137e2a4a9d6f67cac5677be26db16dc406c under Apache-2.0. Its complete package source and license are under `upstream/sentrysearch`.

LAYA's supplied SDK and Apache-2.0 license are under `upstream/laya`. No trained LAYA weights were supplied or fine-tuned in this work.

NudeNet 320n is bundled from the verified nudenet 3.4.2 wheel. Its included license text is AGPL-3.0 and is retained in `licenses/NudeNet-AGPL-3.0.txt`; its package metadata's conflicting MIT label is not used to assume a permissive grant. This distribution provides application and integration source. Model SHA-256: c15d8273adad2d0a92f014cc69ab2d6c311a06777a55545f2c4eb46f51911f0f.

ONNX Runtime 1.23.2 Android and 1.20.1 desktop are from Microsoft under MIT. Qt/PySide6 is distributed under its applicable LGPL/GPL/commercial terms; this open-source distribution uses the open-source components. Desktop bundles FFmpeg through imageio-ffmpeg; its FFmpeg licensing and source obligations apply.

Anatomy, Arc, Liquid Glass and VASO materials retain the MIT notices in `licenses/`. LastWave is a visual reference; its app code is not copied. Browser and native material paths can differ where platform backdrop APIs differ.
