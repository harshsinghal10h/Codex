# Brave + Proton Free in one Android APK

An unofficial experimental modification of Brave's official Android 1.97.56 ARM64 APK. The full Brave browser and the Proton WireGuard controller share one Android package and **one launcher icon**. A separate Proton app is not required.

Open **Brave → Settings → the lock icon**. If the icon is in the overflow menu, choose **Proton Free VPN**. The controller opens inside the browser app. Select a supplied profile in the private personal build, or import your own Proton WireGuard `.conf` file in the public build, tap Connect, and accept Android's VPN request. Wait for a recent handshake before relying on the connection.

## Download and install

Download an APK from [this repository's releases](https://github.com/harshsinghal10h/Codex/releases). It requires ARM64 Android 10 or later. The public build contains no account credentials. Import your own authorized Proton configuration in the integrated controller.

These builds use package `com.brave.browser` and a different signing certificate from official Brave. They cannot update official Brave in place. Back up bookmarks and other important browser data before uninstalling an existing installation; uninstalling normally deletes its data. CI generates a fresh experimental signing key per build, so different CI builds also cannot update one another in place. The separately delivered personal APK preserves its existing private signing key.

**Device testing is outstanding.** Browser startup, menu rendering, Android VPN consent, Keystore behavior, background operation, and live Proton connectivity have not been verified on a physical device or a booted emulator. This is an experimental build. See [VALIDATION.md](VALIDATION.md).

## Behavior

- Uses Brave's existing native WireGuard library with an internal Android VPN service.
- Routes this Brave browser by default; Protect all apps is optional.
- Preserves the imported Proton DNS, endpoint port, addresses, and keepalive.
- Blocks IPv6 for routed apps when a configuration has no IPv6 interface address.
- Displays transfer counters and recent server handshakes. A local tunnel alone is not labeled Connected.
- Imports standard numeric-endpoint WireGuard configurations, including `.conf.txt` filenames.
- Saves profiles in an Android Keystore encrypted, no-backup vault.
- Supports Android always-on VPN. Enable Android's Block connections without VPN if you need a system kill switch. With Brave-only routing, lockdown can also block other apps; use Protect all apps when device-wide access is needed.
- Disables Brave's previous paid VPN services and redirects all eight original VPN activity declarations to the internal controller.
- Removes the previous separate controller launcher entry and obsolete dynamic shortcut.

## Free service

Proton offers a free plan with unlimited data and no subscription fee. Account limits, server availability, and free-plan restrictions apply. WireGuard configurations contain private account credentials; they are not public API keys and do not unlock paid servers or features. This APK does not cancel any existing Brave VPN subscription.

Official information: [Proton Free](https://protonvpn.com/free-vpn), [WireGuard configurations](https://protonvpn.com/support/wireguard-configurations).

## Build

Requires Java 17+, Python 3.10+, and network access to the pinned official APK and toolchain downloads.

```sh
python -m pip install -r requirements.txt
python build.py --import-only --generate-signing-key
```

The script checks dependency and upstream SHA-256 digests, compiles the integration, runs configuration/status tests, patches the binary Android manifest, adds the native Settings menu hook, signs, and validates the result. Original Chromium resources are never rebuilt. Five original dex files, native libraries, and other resources remain byte-identical. The full `classes5.dex` method table requires moving only `BraveSettingsActivity` into `classes8.dex`; validation compares all remaining original methods and unchanged methods in the moved class.

For a private build, put authorized configs at `personal/jp.conf` and `personal/us.conf`, then run `python build.py --generate-signing-key`. Keep the resulting APK private because bundled profiles can be extracted. Reuse the private `signing/` directory for future upgrades. Never commit `personal/` or `signing/` or publish their contents. An existing signing directory can be used without `--generate-signing-key`. Use `--apk /path/to/official.apk` to reuse a verified official download.

The build is pinned to 1.97.56. Future browser security updates require adapting and validating this integration against a new upstream version and signing with the same private key. Unknown upstream APKs are rejected.

## Upstream and licensing

- [Official Brave release](https://github.com/brave/brave-browser/releases/tag/v1.97.56)
- [Matching Brave source](https://github.com/brave/brave-core/tree/v1.97.56), commit `b01cdf43be4b4d5559bf7e58229e666e24454f50`
- Original ARM64 APK SHA-256: `9b02840fb94dc172d3bf0073ce6877372bea0e311baba7952e4a0563df88ae89`
- The compile-only GoBackend stub is excluded from the added dex. The original APK supplies that class and its native engine.

Integration source and modifications in this branch are provided under Mozilla Public License 2.0; see [BRAVE-LICENSE.txt](BRAVE-LICENSE.txt). The upstream browser and bundled components retain their respective licenses. Original third-party notices and `brave://credits` are retained. This project is not affiliated with or endorsed by Brave Software or Proton AG.
