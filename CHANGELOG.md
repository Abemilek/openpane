# Changelog

## 0.2.1

### Fixed
- Half-black page at launch, stale rectangles and flickering: WebRender partial present is now disabled through a GeckoView config file (`GeckoConfig.kt`). Fallbacks: `-PrenderBackend=texture`, `-PsoftwareRender=true`.
- The OAuth popup now uses a TextureView backend, as required by GeckoView when one GeckoView is drawn over another.
- Memory leak: the extension message delegates (owned by the process-wide GeckoRuntime) kept the old Activity alive. The extension/Rust bridge now lives in `ExtensionBridge` and holds no Activity reference.
- After reopening the app in the same process, or after closing an OAuth popup, the extension no longer lost its rules (the port was being cleared).
- Rules were not re-applied correctly after a rules update (previously hidden promoted posts reappeared).

### Performance
- `content.js`: no more layout-forcing reads, no full-text scan of every element, DOM changes handled once per frame, and no round trip to the app unless muted words/accounts exist.
- Animations removed: window animations, `prefers-reduced-motion`, CSS reset in the extension, animated images.
- Extension version bumped to 0.2.1 so installed copies are replaced.

### Size
- Optional `-PcompressNativeLibs=true` for a much smaller APK file.
- Rust release profile tuned for size (`opt-level = "s"`, LTO).
