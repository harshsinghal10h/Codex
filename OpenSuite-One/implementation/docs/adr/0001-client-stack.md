# ADR 0001 — Client stack

**Decision:** Use a dependency-free, accessible HTML/CSS/JavaScript web shell as the **validated initial milestone**, with a TypeScript/React + React Native future target after integration infrastructure and native SDKs become available. Use a small platform-specific native surface for Android/iOS, not a WebView-only native app. Desktop evaluation will revisit Tauri versus native UI.

**Options reviewed:** React Native plus React Native Web (shared business rules, mobile bridging; ecosystem/version and bundle costs); Flutter (full cross-platform widgets; provider editor integration and Dart API duplication); Kotlin Multiplatform plus SwiftUI/Compose (strong OS fidelity; significant duplicated UI); web plus Tauri (fast desktop, mobile native behavior and store requirements still need assessment). No comparative device benchmark was conducted.

**Rationale:** tool environment has Node.js 22 and SQLite but no npm network, Docker/Android SDK/Xcode; choose a real working local vertical slice rather than fabricate native binaries. Preserving REST contracts leaves mobile options open. Do not claim that JavaScript web assets compile into an optimized Android/iOS application without native engineering.

**Consequences:** shared UI information architecture and responsive semantics are present; native OS permissions, accessibility validation, push, encrypted offline stores and release pipeline are unsolved.
