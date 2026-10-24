# Openpane

Openpane is an unofficial FOSS Android client for X built around Mozilla GeckoView. It loads the official X web application inside a focused Android container, providing the authenticated **For You** feed without using undocumented X API endpoints or private API scraping.

The project keeps the Android layer small:

- GeckoView handles the browser engine, login, sessions, media, and the authenticated feed.
- A bundled WebExtension provides local UI adjustments and a native messaging bridge.
- A Rust core provides portable, testable filtering and URL-cleaning logic through UniFFI.

Openpane does not automate likes, follows, posts, or other account actions, and does not collect or redistribute X content.

## Requirements

- JDK 17 or newer
- Android SDK platform 36 and build tools
- Rust with Android targets
- Android NDK 28 or newer
- Network access for the first dependency download

Android Studio is optional.

## Build

Set the SDK location with `ANDROID_HOME` or `local.properties`:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
```

Build Rust libraries and UniFFI Kotlin bindings for both phone ABIs:

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi
cargo install cargo-ndk --locked
ANDROID_NDK_HOME="$ANDROID_HOME/ndk/<version>" ./scripts/build-rust.sh
```

Build the Android app:

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

Build one release ABI at a time:

```bash
./gradlew assembleRelease -Pabis=arm64-v8a
./gradlew assembleRelease -Pabis=armeabi-v7a
```

Release signing is optional and uses the ignored `keystore.properties` file. Never commit a keystore or signing properties.

## Testing

Use a secondary X account. Check login, Google OAuth and 2FA, both feeds, infinite scrolling, images, video, fullscreen, back navigation, session persistence, and memory use on a physical Android device.

Run Rust tests with:

```bash
cargo test --manifest-path rust/Cargo.toml --workspace
```

## Architecture

```text
Android Activity
  └─ GeckoRuntime / GeckoSession
       ├─ X web application
       ├─ bundled WebExtension
       └─ native messaging bridge
            └─ Rust core through UniFFI/JNA
```

The minimum Android API is 29. ARM64 is recommended; ARMv7 is available for older 32-bit ARM phones. GeckoView makes the APK large because it bundles the browser engine.

## Security and legal notes

Openpane is not affiliated with X Corp. Use the official website through the embedded browser and follow X's terms. GeckoView, UniFFI, and JNA retain their respective licenses; review third-party notices before redistribution.
