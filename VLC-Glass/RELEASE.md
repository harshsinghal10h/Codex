VLC Glass 0.3.3 development builds

- Restore direct access to five transport controls, speed, audio/subtitles, queue, volume and rotation/fullscreen. Labeled tools stack on phones and share a row on wide screens, with at least 44-pixel touch targets.
- Fix Android rotation returning to the same orientation, nested dialogs losing their focus target, and Space on focused buttons triggering playback as well as the button action.
- Stop volume dragging from repeatedly saving settings and reapplying the glass appearance. Keep active sliders and keyboard navigation from being hidden mid-interaction.
- Keep the supplied ZIP's video-embedding semantic search, protected search-result seeks and local Scene Guard scan/skip pipeline.
- Verify native playback, screen composition and real control actions on Android phone/tablet, Windows, macOS Intel/ARM and Linux. Package hashes and the precise test results are attached to the release.

Gemini requires your key and uses API quota. Local Qwen requires optional dependencies and weights. Tests use safe clips and deterministic embeddings; live semantic accuracy, explicit-scene recognition accuracy and physical-device latency remain unmeasured. These development packages do not certify every device or OS version. Android retains the existing development signing identity; desktop packages are not vendor-notarized.
