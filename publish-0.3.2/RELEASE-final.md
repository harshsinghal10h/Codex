VLC Glass 0.3.2 development builds

- Compact, responsive player with one transport row and secondary actions in settings.
- Efficient glass by default; optional advanced refraction, fewer DOM updates, paused hidden artwork and coalesced desktop video copies.
- Supplied SentrySearch ZIP verified file-for-file; Gemini Embedding 2 request compatibility corrected, persistent desktop/companion indexes, and prepare-and-search flow.
- Direct Android video embedding search with encrypted key storage, resumable SQLite vector indexes, bounded clip/candidate sizes and accurate keyframe timestamps. The optional companion preprocesses unsupported/high-bitrate videos.
- Scene Guard's protected seek paths remain active for search results.

Gemini requires your key and uses API quota. Local Qwen requires optional dependencies and weights. CI uses safe clips and deterministic embeddings; live semantic accuracy, real explicit-scene detection accuracy and physical-device latency are not evaluated. These development packages do not certify every device or OS version. Android APKs share the previous development signing identity; desktop packages are not vendor-notarized.

Product source: `10a7584eda82e7d304bf9fccfda4014338e86ede`. Use the attached `VLC-Glass-0.3.2-source.zip` for the exact product source. `Build-checks-0.3.2.json` records the six successful native platform jobs, screen checks, actual-clock search seeking, and exact package hashes.

On Android, open a local video, describe a scene, open search setup if prompted, add your Gemini key and choose **Prepare & search**. On desktop, use the same prompt and preparation flow. No matching scene is invented when the API or index is unavailable.
