# Validation of the personal Brave + Proton build

## Passed

- Downloaded Brave's official stable ARM64 release from `brave/brave-browser` and verified the release asset's published SHA-256 digest.
- Downloaded the matching `brave/brave-core` source tag and inspected its native Android WireGuard implementation.
- Compiled the integration with the official Android API 35 platform library, Java 8 bytecode, and minimum Android API 29.
- Passed 33 configuration and status assertions, including both supplied configurations in the personal build (27 assertions without profiles in CI); no credentials were printed. Tests cover key format, full IPv4/IPv6 coverage, Proton DNS retention, endpoint ports, IPv6 endpoints, duplicate/oversize inputs, unsupported executable hooks, stale handshakes, and malformed counters.
- Verified that the added dex contains only the new integration classes and does not duplicate the original WireGuard class.
- Verified the five JNI method names and descriptors used by the integration against the original Brave dex.
- Verified all eight original VPN activity declarations point to the new controller and the paid VPN services are disabled.
- Verified the new VPN service is internal, protected by `BIND_VPN_SERVICE`, and declares the system-exempted foreground service type.
- The validator requires exactly one original Brave launcher. The internal controller is not exported and has no launcher intent. Brave Settings adds the Proton Free VPN action.
- The validator compares all original payloads except the manifest and classes5.dex byte for byte. It also compares every original method remaining in classes5.dex and every unchanged method in the moved Settings class. Only onCreateOptionsMenu adds the controller hook and forces menu visibility.
- Verified APK alignment and the personal v3 signing certificate.
- Successfully rebuilt and signed the APK with the supplied `build.py` workflow and the backed-up signing key.

## Not verified at runtime

An Android 15 x86_64 emulator was attempted with the matching x64 Brave release. This environment has no hardware virtualization device. Its software-emulated guest did not reach a usable ADB/boot state, so APK installation, browser startup, Android VPN consent, Android Keystore behavior, background operation, and live Proton connectivity could not be tested on that guest. No physical Android device was available.

Consequently, this APK is an **experimental personal build**, not a production-certified release. No successful server handshake, public-IP change, DNS leak test, throughput measurement or battery benchmark is claimed.

## Device verification

1. Back up important existing Brave data before replacing the officially signed installation.
2. Install the personal APK. Confirm one launcher icon. Open Brave → Settings → the lock icon or overflow item Proton Free VPN; confirm the controller opens inside the browser.
3. Select Japan, connect and accept Android's VPN prompt. Confirm a recent handshake and nonzero received traffic before relying on the connection.
4. Browse to Proton's official IP-check page to inspect the exit address. Repeat with the US profile.
5. Confirm disconnect stops the VPN notification. Test background browsing and a Wi-Fi/mobile-data change.
6. If using a system kill switch, enable Android's always-on and lockdown options and test their behavior. Use Protect all apps when you need other apps to retain internet access under lockdown.

An established interface alone is not a verified connection. If a profile is revoked, expired, or its server is unavailable, import a fresh configuration from the same Proton account. The app does not bypass account limits or provide premium entitlements.
