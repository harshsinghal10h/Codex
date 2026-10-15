# VLC Glass

A VLC powered media player with responsive liquid glass controls, natural-language video search using SentrySearch, and local scene skipping.

- [Downloads and checksums](https://github.com/harshsinghal10h/Codex/releases/tag/vlc-glass-v0.3.1)
- [Application source and setup](VLC-Glass/README.md)
- [Verification scope](VLC-Glass/QA.md)
- [Build status](https://github.com/harshsinghal10h/Codex/actions/workflows/build-vlc-glass.yml)

Android packages cover ARM64, ARMv7 and x86-64, plus a universal APK. Desktop packages are built independently for Windows x64, macOS ARM64/Intel and Linux x64 and published after their checks pass. These are development builds, with no all-device or classifier-accuracy certification.

Scene guard scans local videos before playback and reuses a content-hashed cache. Search indexing requires your Gemini API key or a separately installed local Qwen model; Android uses the authenticated desktop companion. The supplied LAYA text SDK has an optional cached advisory adapter. No LAYA checkpoint weights were provided.
