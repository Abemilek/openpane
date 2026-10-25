# Minification (R8) is intentionally disabled: GeckoView calls into Java through JNI and
# UniFFI/JNA load Rust symbols by name, so shrinking needs hand-tested keep rules and the
# saving is small compared with the native engine. See README, "App size".
