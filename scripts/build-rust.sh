#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CORE_DIR="$ROOT_DIR/rust/openpane_core"
OUT_DIR="$ROOT_DIR/app/src/main/jniLibs"
TARGETS="${RUST_TARGETS:-aarch64-linux-android,armv7-linux-androideabi}"
command -v cargo >/dev/null || { echo "cargo is required" >&2; exit 1; }
command -v cargo-ndk >/dev/null || { echo "cargo-ndk is required; install it with cargo install cargo-ndk" >&2; exit 1; }
mkdir -p "$OUT_DIR"
(cd "$CORE_DIR" && cargo ndk -t "$TARGETS" -o "$OUT_DIR" build --release)
mkdir -p "$ROOT_DIR/app/src/main/java/app/openpane/core"
(cd "$CORE_DIR" && cargo run --manifest-path "$ROOT_DIR/scripts/uniffi-bindgen/Cargo.toml" -- generate \
  --library "$ROOT_DIR/rust/target/aarch64-linux-android/release/libopenpane_core.so" \
  --language kotlin \
  --out-dir "$ROOT_DIR/app/src/main/java/app/openpane/core")
