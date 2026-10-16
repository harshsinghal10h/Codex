# VLC Glass 0.3.2

A LibVLC player for Android, Windows x64, macOS Intel / Apple Silicon, and Linux x64, with a shared responsive glass interface.

This update uses the supplied `sentrysearch-master.zip`. Every source file matches revision `ab688137e2a4a9d6f67cac5677be26db16dc406c`; `vendor/search-source.json` records the ZIP digest and per-file hashes. The prior link happened to identify the same revision. The actual search failure was the upstream Gemini Embedding 2 request format, plus a manual indexing flow and indexes that could not reopen after restarting. The adapter now uses the current model's query prefix without `task_type` and reopens content-addressed indexes.

Open a local video, enter a scene description in the glass prompt, and submit. If preparation is needed, open search settings, enter your Gemini key, and choose **Prepare & search**. Preparation sends selected video clips to Google and uses your API quota; subsequent queries reuse vectors. Results show start/end timestamps and a similarity score (not a probability). Tap a result to seek through the normal Scene Guard checks. No filenames, transcripts, or generated captions substitute for video embeddings.

Android supports direct Gemini search with the key encrypted in Android Keystore. It remuxes overlapping 8-second windows into MP4 clips without encoding on the playback thread, stores normalized 768-dimension vectors in SQLite, and streams cosine comparisons with a bounded candidate pool. Keyframe starts are preserved in the returned timestamps. Large frames, sparse keyframes, unsupported codecs, or clips over 12 MiB require the desktop companion, which uses the ZIP's 320p / 2 fps preprocessing. Direct Android and desktop indexes remain separate because their video preprocessing differs. Local Qwen is available through a desktop environment with its optional dependencies and model weights installed; those multi-gigabyte weights are not bundled.

The player has one compact rewind / play / forward row. Previous/next, queue, volume, tracks and other secondary actions are in Player settings. The library's mini-player and navigation bar are hidden while watching. Expensive SVG displacement and full-video GPU refraction are off by default; advanced refraction remains opt-in in Appearance. Hidden artwork stops updating, unchanged labels are not rebuilt on playback ticks, and desktop frames are dropped before copying when a UI paint is already pending.

Scene Guard retains its opt-in local detector and precomputed skip map. It prepares a scan before playback, masks and mutes protected seeks, and pauses on failed/incomplete scans. It cannot guarantee recognition of every explicit scene. LAYA remains an optional decision SDK integration; the provided archive had no trained visual weights.

## Development and verification

Use Python 3.12 and Node 20+:

```sh
python -m pip install -r desktop/requirements.txt
npm ci
python tools/prepare_dependencies.py --desktop
python -m unittest discover -s tests -p 'test_*.py' -v
python desktop/main.py
```

For Android, run `python tools/prepare_dependencies.py` and `python tools/build.py --keystore /private/path/signing.p12 --universal`. Reuse your existing signer. Android 9 / API 28 is the minimum; separate ARM64, ARMv7, x86_64 and universal APKs are built.

The companion runs with `python -m intelligence.server --token-file /private/path/token.txt`; LAN use requires `--bind`, `--cert`, and `--key`. Configure `GEMINI_API_KEY` on the companion, or install the ZIP's local Qwen extra. The authenticated server keeps selected uploaded videos in its data directory and restores registered media/indexes after restarting.

CI exercises actual packaged native playback and screen composition on Windows, both Mac architectures, and Linux, plus Android 9 phone and Android 15 tablet emulators. The additional UI contract checks compact controls, prompt/timeline/transport bounds, repeated-update DOM mutations, and result-to-timestamp seeking. Android also remuxes and decodes a real safe clip and tests vector math and request formatting. Synthetic embeddings and safe fixtures test integration, not semantic accuracy or adult-content recognition. Live paid API calls and physical-device performance are not claimed as evaluated.

Development packages are unsigned by OS vendors (Android uses the existing development signer). iOS, Windows ARM64, Linux ARM and every OS/device combination are not delivered or certified. See the release assets and their attached reports for the exact tested packages.

