#!/usr/bin/env bash
# SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to the installed Android SDK}"
ndk_root="${ANDROID_NDK_HOME:-$ANDROID_SDK_ROOT/ndk/27.2.12479018}"
case "$(uname -s)" in
  Linux) host_tag=linux-x86_64 ;;
  Darwin) host_tag=darwin-x86_64 ;;
  *) echo 'Run this native build in Linux/macOS or CI.' >&2; exit 2 ;;
esac
llvm_bin="$ndk_root/toolchains/llvm/prebuilt/$host_tag/bin"
test -x "$llvm_bin/aarch64-linux-android26-clang"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$llvm_bin/aarch64-linux-android26-clang"
export CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER="$llvm_bin/armv7a-linux-androideabi26-clang"
export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$llvm_bin/x86_64-linux-android26-clang"
# Native libraries and the final APK must support Android's 16 KiB pages.
export RUSTFLAGS="${RUSTFLAGS:-} -C link-arg=-Wl,-z,max-page-size=16384"
for pair in aarch64-linux-android:arm64-v8a armv7-linux-androideabi:armeabi-v7a x86_64-linux-android:x86_64; do
  target="${pair%%:*}"
  abi="${pair#*:}"
  cargo build -p nw-bridge --release --locked --target "$target"
  mkdir -p "target/android-jniLibs/$abi"
  cp "target/$target/release/libnw_bridge.so" "target/android-jniLibs/$abi/libnw_bridge.so"
done
