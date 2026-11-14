#!/usr/bin/env bash
# Cross-compile the shared Rust core (UniFFI cdylib) for Android ABIs and place
# the resulting libsuras_core.so files where Gradle picks them up.
#
# Requirements:
#   * Rust toolchain (rustup) with the Android targets:
#       rustup target add aarch64-linux-android x86_64-linux-android
#   * cargo-ndk:            cargo install cargo-ndk
#   * Android NDK, exposed as ANDROID_NDK_HOME (or ANDROID_NDK_ROOT).
#
# Usage:
#   scripts/build_android_core.sh            # arm64-v8a + x86_64
#   ABIS="arm64-v8a" scripts/build_android_core.sh
#
# The generated .so files are build output and are git-ignored. They are NOT
# committed; CI (and the release workflow) regenerate them.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CORE_DIR="$REPO_ROOT/core/rust"
JNI_DIR="$REPO_ROOT/apps/android/app/src/main/jniLibs"
ABIS="${ABIS:-arm64-v8a x86_64}"

if ! command -v cargo-ndk >/dev/null 2>&1; then
  echo "error: cargo-ndk is not installed (cargo install cargo-ndk)" >&2
  exit 1
fi

if [[ -z "${ANDROID_NDK_HOME:-}${ANDROID_NDK_ROOT:-}" ]]; then
  echo "error: set ANDROID_NDK_HOME or ANDROID_NDK_ROOT to an Android NDK" >&2
  exit 1
fi

targets=()
for abi in $ABIS; do
  case "$abi" in
    arm64-v8a) targets+=(-t arm64-v8a) ;;
    x86_64)    targets+=(-t x86_64) ;;
    armeabi-v7a) targets+=(-t armeabi-v7a) ;;
    x86)       targets+=(-t x86) ;;
    *) echo "error: unknown ABI '$abi'" >&2; exit 1 ;;
  esac
done

mkdir -p "$JNI_DIR"
rm -rf "${JNI_DIR:?}/"*/libsuras_core.so 2>/dev/null || true

cd "$CORE_DIR"
# UniFFI generates the Kotlin bindings from the compiled cdylib; the library
# crate-type already includes "cdylib" so no extra features are needed.
cargo ndk "${targets[@]}" -o "$JNI_DIR" build --release

echo "Built Android native core:"
find "$JNI_DIR" -name 'libsuras_core.so' -print
