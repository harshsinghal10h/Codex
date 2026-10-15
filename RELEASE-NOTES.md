Unofficial experimental Brave 1.97.56 Android build with an internal Proton WireGuard controller and one browser launcher icon.

Open Brave → Settings → the lock icon or overflow item **Proton Free VPN**. Import your own Proton WireGuard configuration, select it, tap Connect, and accept Android's VPN request. No second app is needed.

ARM64, Android 10+. This public APK contains no account profiles or private signing keys. CI creates a fresh experimental signing key per build; it cannot update official Brave or APKs signed by a different build. Back up browser data before uninstalling any existing installation. Your separately delivered personal APK uses its existing signing key instead.

Static validation and configuration tests pass during the build. Browser startup, menu rendering, Android VPN consent, and live Proton connectivity have not been verified on a device. Confirm a recent handshake and the exit IP before relying on this experimental build.

Proton's free account/server restrictions apply. This modification does not cancel any existing Brave VPN subscription or grant Proton paid features.
