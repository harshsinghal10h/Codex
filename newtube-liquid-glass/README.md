# NewTube Dual Liquid Glass Frontend — v1.15.0 source patch

This patch adds a centralized, reversible appearance engine to the **NewTube v1.15.0 touch/mobile UI**. It deliberately leaves NewTube's playback, feeds, downloads, account flow, presenters, networking and Media3 state contracts untouched.

## The two glass modes

**Vaso Liquid Lens** is based on the visual/optical behavior of the supplied `vaso-main.zip`: clearer material, stronger optical depth, spectral edge separation, bright specular light, lower frost and more pronounced press expansion.

**LastWave Obsidian Glass** is an independently implemented Android-Views interpretation of LastWave's visual language: vibrant tinted glass, dark translucent material, soft bloom, continuous-corner squircle geometry, elevated floating surfaces and expressive press motion.

A third **Classic NewTube** profile is the escape hatch. It restores the original NewTube backgrounds, elevation, motion, density, haptic eligibility, outline providers, clipping and text colors.

## Customization surface

Open **Settings → Appearance & glass**. Users can independently tune:

- visual system/profile: Vaso, LastWave, Classic
- Android 12+ dynamic system accent tint, with NewTube accent fallback
- AMOLED pure-black canvas
- frost / haze intensity
- surface opacity
- lens depth
- spectral dispersion
- specular strength
- accent tint strength
- rim / border strength
- vibrancy / saturation
- surface contrast
- reduce-transparency mode
- corner radius
- floating elevation
- compact / comfortable / spacious density
- rounded artwork, avatars and thumbnails
- motion intensity
- press expansion
- native haptic eligibility
- navigation glass
- optional floating navigation capsule
- top-bar/search glass
- cards/sheets glass
- action-button glass
- settings-surface glass
- mini-player glass
- high-contrast text
- one-tap reset to the selected profile's recommended values

All values are persisted in one `SharedPreferences` file and can be changed live. RecyclerView children are styled when attached, so recycled feed/settings rows follow the active profile without adapter forks.

## Rendering model and performance

NewTube's touch frontend is Android Views/XML. LastWave uses Compose/backdrop compositing, while Vaso uses a browser/SVG backdrop lens. Transplanting either renderer literally would add a second rendering architecture or require continuous scene capture around scrolling/video surfaces.

This patch therefore ports the **visual system and interaction behavior** rather than forcing framework-specific rendering code into NewTube. `GlassDrawable` uses graded translucent material, optical frost/haze, spectral rim separation, dynamic tint, specular light, bloom, depth and profile-specific geometry. It does **not** continuously capture and blur arbitrary pixels behind every View. That decision protects feed scrolling, Media3 playback and Android 7+ compatibility.

LastWave mode uses an independent superellipse implementation for the squircle silhouette. No GPL-licensed LastWave source file is included in this patch.

## Light/dark safety

NewTube can recolor some live screens in place to avoid restarting playback. The glass runtime explicitly restores the clean NewTube baseline before that recolor, lets NewTube install the new light/dark colors, captures the new baseline, then reapplies glass. This prevents the common failure where switching themes and later selecting Classic restores stale colors from the previous theme.

## Files added

- `com/newtube/mobile/ui/glass/GlassPreferences.java`
- `com/newtube/mobile/ui/glass/GlassGeometry.java`
- `com/newtube/mobile/ui/glass/GlassDrawable.java`
- `com/newtube/mobile/ui/glass/GlassRuntime.java`
- `com/newtube/mobile/ui/settings/GlassSettingsPage.java`
- `res/values/strings_glass.xml`
- `res/values/ids_glass.xml`
- `res/drawable/ic_settings_appearance.xml`

Only two existing Java files are patched:

1. `MobileActivity.java`
   - registers/unregisters the appearance runtime
   - applies glass after content inflation and on resume
   - restores/rebaselines glass safely around NewTube's in-place light/dark recoloring
   - keeps RecyclerView rows synchronized through child-attach hooks

2. `SettingsPages.java`
   - adds the **Appearance & glass** page to NewTube's data-driven Settings tree
   - because NewTube's settings search walks the page tree, the new controls become searchable automatically

## Apply

From this patch directory:

```bash
python apply_liquid_glass.py /path/to/newtube
python verify_liquid_glass.py /path/to/newtube
```

The installer targets the **v1.15.0 mobile source layout** and stops instead of guessing if expected integration points are missing. Before editing the two integration files, it stores their originals under:

```text
.newtube-liquid-glass-backup/
```

It is idempotent. Re-running it updates the isolated glass files without duplicating hooks or settings rows.

## Roll back

```bash
python remove_liquid_glass.py /path/to/newtube
```

Rollback refuses to guess if the original backup is unavailable.

## Build NewTube

NewTube v1.15.0 requires JDK 17 and Android SDK 37. Clone NewTube with its submodules, apply this patch, then build the debug APK:

```bash
git clone --recurse-submodules https://github.com/aleixrodriala/newtube.git
cd newtube
git checkout v1.15.0
# copy this patch directory somewhere convenient, then:
python /path/to/newtube-liquid-glass-patch/apply_liquid_glass.py .
python /path/to/newtube-liquid-glass-patch/verify_liquid_glass.py .
./gradlew :smarttubetv:assembleStmobileDebug
```

Expected debug output is under:

```text
smarttubetv/build/outputs/renamed_apks/stmobileDebug/
```

A signed release build needs NewTube's private signing configuration; this patch does not contain or replace signing keys.

## Recommended defaults

| Setting | Vaso | LastWave |
|---|---:|---:|
| Frost | 28% | 58% |
| Opacity | 58% | 72% |
| Radius | 24 dp | 22 dp |
| Depth | 82% | 55% |
| Dispersion | 52% | 18% |
| Specular | 86% | 64% |
| Saturation | 118% | 145% |
| Contrast | 108% | 104% |
| Elevation | 6 dp | 10 dp |
| Press expansion | 106% | 108% |
| Accent tint strength | 48% | 76% |
| Rim strength | 92% | 68% |
| Floating navigation | On | On |

## Validation performed on this patch bundle

- installer Python syntax validation
- installer applied twice to confirm idempotence
- migration path for an earlier patch hook
- verifier passes after install
- rollback restores integration files and removes added source/resources
- reapply after rollback passes verification again
- all introduced XML is well-formed
- every glass Settings string referenced by `GlassSettingsPage` exists (real reference scan)
- every private runtime ID referenced by `GlassRuntime` exists
- floating-navigation margins restore cleanly when the feature/Profile is disabled
- no Laya/JEV dependency or reference exists
- Java parser pass shows no syntax-level errors; full Android compilation still requires the NewTube source tree + Android SDK/toolchain

## Device validation checklist after building

- cold launch and warm launch
- Home feed scroll/recycling
- Home ↔ Subscriptions ↔ History ↔ You
- Search and search results
- Settings navigation and Settings search
- Vaso ↔ LastWave ↔ Classic switching while Settings is open
- System ↔ Light ↔ Dark transitions
- Classic after a light/dark switch, to confirm baseline restoration
- video playback in portrait and landscape
- player controls and option sheets
- minimize/restore mini-player
- opening/closing player without playback restart when appearance values change
- low-end device scroll performance
- TalkBack focus and contrast with High-contrast text off/on

## Licensing note

- NewTube is MIT licensed.
- The supplied Vaso project is MIT licensed.
- LastWave is GPLv3. This patch uses LastWave as a **visual reference only** and does not bundle its source or Compose liquid-glass implementation. The squircle implementation here is independent superellipse math.

If you later copy LastWave source code directly into the app, review GPLv3 obligations before distributing the resulting build.
