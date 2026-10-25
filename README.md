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

## Rendering problems

Symptoms such as a half-black page at launch, stale rectangles or flickering are almost always a graphics-driver issue, not an app bug. Openpane already disables WebRender "partial present" (a known cause of this on Android GPUs, see Mozilla bug 1575765) through the GeckoView config file written by `GeckoConfig.kt`.

If a particular phone still glitches, try these in order and rebuild:

```bash
./gradlew assembleDebug -PrenderBackend=texture   # GeckoView on a TextureView (a bit slower)
./gradlew assembleDebug -PsoftwareRender=true     # CPU rendering, last resort
```

## App size

GeckoView bundles the whole browser engine (`libxul.so` plus resources), which is most of the APK. Mozilla's own answer to "can the library be smaller?" is that it cannot, because it has to keep all of Gecko's capabilities. What can be done:

- **One ABI per APK.** Build `-Pabis=arm64-v8a` for current phones and `-Pabis=armeabi-v7a` for old ones (never both in one APK).
- **Release build.** Use `assembleRelease` with your own signing key for daily use.
- **Compressed native libraries (optional).** `-PcompressNativeLibs=true` stores the libraries compressed: the APK file is much smaller, but the installed app takes more storage because the libraries are extracted at install time. The default keeps them uncompressed (largest APK file, smallest installed size).
- **Smaller Rust library.** The Rust release profile uses `opt-level = "s"` and LTO. Re-run `./scripts/build-rust.sh` to rebuild with it.

R8/minification is intentionally off: GeckoView calls Java through JNI and UniFFI/JNA load Rust symbols by name, so shrinking needs hand-tested keep rules and the saving is small next to the engine.

## Performance notes

- Animations are removed at three levels: Android window animations (theme), the page (`prefers-reduced-motion` preference plus a CSS reset in the extension) and animated images (`image.animation_mode`).
- If videos stop autoplaying, it is the `ui.prefersReducedMotion` preference (sites may honor it); remove that line in `GeckoConfig.kt` to restore autoplay.
- The Rust bridge is only used when muted words or accounts exist; promoted posts are hidden locally, with no round trip to the app.
- The extension processes DOM changes once per frame and never forces layout.
- When you change anything under `app/src/main/assets/extension/`, **bump `version` in its `manifest.json`**. GeckoView only replaces an installed built-in extension when the version differs.

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
