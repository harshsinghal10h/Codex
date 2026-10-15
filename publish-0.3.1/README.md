These binary chunks preserve the locally signed 0.3.1 Android packages without exposing the private signing key. `manifest.json` maps content-addressed Git blobs to release files and their full SHA-256 checksums. The release workflow reassembles and verifies both bytes and the APK certificate before uploading.

Download the ready-to-install APKs from the GitHub release. No reassembly is needed by users. Rebuildable application sources are in `VLC-Glass/`; see its README and QA notes for setup and verification scope.
