# NewTube Glass 2

An independently installable Android frontend fork of NewTube **v1.15.0**. Replaces the generic nested glass overlay with a consistent browse shell, native floating dock, deliberate feed cards and a redesigned appearance page.

## Visual systems

| Component | Vaso | LastWave |
| --- | --- | --- |
| Canvas | Warm paper in light mode, charcoal in dark mode | Obsidian in dark mode, warm neutral in light mode |
| Typography | Clear platform sans | Bundled rounded Google Sans Flex |
| Navigation | Even icon/label segments and a travelling clear lens | Selected icon/label capsule with quiet unselected icons |
| Material | Fine rims, sampled refraction, restrained spectral edges | Warm tinted surface, subdued rims, rounded geometry |
| Cards | Thin border, clear padding, rounded artwork | Soft filled containers, larger continuous corners |

The two systems obey NewTube's Light / Dark / System setting. Appearance includes directly selectable preview cards, the original searchable settings controls, and Classic NewTube. Classic restores the original geometry, fonts, colours and backgrounds.

## What changed

- `GlassNavigationView` retains the original BottomNavigationView menu, presenters, selection/reselection callbacks and update badges. Long-press section actions work across tab changes.
- The dock is centred, limited to 390 dp, respects system insets and adapts to larger fonts. Every tab has a 48 dp minimum target and a full spoken label.
- Content scrolls behind the floating dock. End padding clears both the dock and a visible mini-player. Placeholders and the You panel share the same safe anchors.
- `GlassRuntime` styles **explicit component roles**. No wildcard styling of every settings container, nested thumbnail frame or player ImageButton.
- Header hierarchy, 48 dp circular toolbar controls, consistent card spacing and grouped settings rows replace the old overlapping backgrounds.
- Appearance has two visible profile previews. Controls remain in the standard settings tree and remain searchable.
- Background sampling is confined to the dock rectangle at a maximum 12.5 captures per second during redraws. A low-resolution sample supplies haze; a native bitmap mesh supplies refraction. Sampling skips the dock itself and never captures the live video surface. Reduced transparency disables sampling.
- Feed bitmap decoding permits the software canvas used by the dock. Playback, networking, downloads, casting and account presenters continue to use upstream implementations.

This is an Android Views implementation inspired by the references. It does not embed the React Vaso renderer or LastWave's Compose renderer. Native sampled refraction is approximate; it is not a pixel-for-pixel copy of either shader.

## Install identity

- App label: **NewTube Glass**.
- Release package: `io.github.harshsinghal10h.newtubeglass`.
- Debug package: `io.github.harshsinghal10h.newtubeglass.debug`.
- Version: `1.15.0-glass.2`, code `11502`.

The fork installs alongside upstream NewTube and the previous experiment. Their app data is separate. Upstream's in-app updater is disabled for this independent package.

## Build

```bash
git clone --recurse-submodules --branch v1.15.0 https://github.com/aleixrodriala/newtube.git
python apply_liquid_glass.py newtube
python verify_liquid_glass.py newtube
cd newtube
./gradlew :smarttubetv:testStmobileDebugUnitTest --tests com.newtube.mobile.ui.glass.GlassNavigationRegressionTest
./gradlew :smarttubetv:assembleStmobileRelease
```

Requires JDK 17 and Android SDK 37. Gradle selects its required build tools. The installer fails on missing integration points and can be run again to update the component payload. Use a clean checkout to return to upstream source; selecting Classic restores the interface at runtime.

GitHub Actions builds both release and debug variants, runs the navigation/layout tests, installs the actual app on an Android 15 emulator and captures Home, You and Appearance in both profiles. The release APK is unsigned until the owner's private key signs it. The signing key is never part of this repository.

## Validation

`GlassNavigationRegressionTest` covers selection, reselection, rejected selections, large-font five-tab geometry, repeated Classic restoration, palette text contrast and preservation of the populated RecyclerView's live ViewHolder metadata across restyling. `GlassDeviceTest` exercises the actual Browse and Settings Activities, switches profiles and captures Android screenshots. Build results and screenshots are available with each Actions run.

An emulator check is not a physical-device performance or full video-playback certification. Actual YouTube content depends on the network and sign-in state.

## Fonts and attribution

NewTube and Vaso are MIT licensed. LastWave is used as a visual reference; its GPL application source and Compose renderer are not copied.

Google Sans Flex is distributed under the SIL Open Font License 1.1, included as [GOOGLE-SANS-OFL.txt](GOOGLE-SANS-OFL.txt). The three bundled static Latin instances are generated from the font family used by LastWave. Android supplies fallback glyphs for other scripts. The font adds approximately 230 KB, rather than the 4 MB variable source. Font licence notices are also packaged with the APK.

References: [NewTube](https://github.com/aleixrodriala/newtube), [LastWave Native](https://github.com/Clash-Projects/LastWave-Native), [Google Sans Flex](https://github.com/google/fonts/tree/main/ofl/googlesansflex), [Android Canvas](https://developer.android.com/reference/android/graphics/Canvas).
