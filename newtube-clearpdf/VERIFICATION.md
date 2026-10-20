# NewTube Clear preview verification

Version: 1.15.0-clear.1 (11504), ARM64, Android 7.0+.

Local development validation passed 58 targeted tests: 36 timeline tests, 10 navigation/glass regression tests, 7 theme tests, and 5 ClearPDF frontend tests. Native Robolectric renders were reviewed for the browse header/dock and offline player controls. All 219 mobile XML resources parsed. The original local release APK also passed signature, package, ZIP and alignment checks.

The published artifact is rebuilt by GitHub Actions, which reruns the targeted tests and verifies its APK signature and package alignment before uploading it. Its exact SHA-256 is in the release's SHA256SUMS.txt.

The preview package is io.github.harshsinghal10h.newtubeclear.preview. It uses a dedicated Android test certificate and installs separately from NewTube Glass. It does not contain the owner's release key or signing passwords.

No physical Android device was connected during development. Live YouTube playback, account sign-in, casting, network downloads, and phone frame rates were not tested. The upstream extraction and playback engine remains in place. The interface adapts ClearPDF's design in Java/Views and does not embed ClearPDF's Compose refraction engine.
