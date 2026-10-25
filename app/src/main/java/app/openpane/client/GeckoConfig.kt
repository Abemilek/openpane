package app.openpane.client

import android.content.Context
import java.io.File

/**
 * Writes the GeckoView configuration file (YAML) read at engine start-up.
 *
 * Format and keys (env / args / prefs) are documented in the GeckoView
 * "Configuring GeckoView" guide. The file is private to the app and is passed
 * to GeckoView with GeckoRuntimeSettings.Builder#configFilePath, which works
 * on release builds too.
 */
internal object GeckoConfig {
    private const val FILE_NAME = "geckoview-config.yaml"

    fun write(context: Context, softwareRender: Boolean): String {
        val file = File(context.noBackupFilesDir, FILE_NAME)
        val yaml = buildString {
            appendLine("prefs:")

            // --- Rendering -------------------------------------------------
            // WebRender "partial present" is enabled by default on Android. On some
            // GPU drivers it leaves stale or never-painted 512 px tiles on screen
            // (half-black pages, flickering). 0 disables partial present, so every
            // frame is presented in full. Read once at start-up.
            appendLine("  gfx.webrender.max-partial-present-rects: 0")
            if (softwareRender) {
                // Last resort for drivers that still glitch: CPU rendering (slower).
                appendLine("  gfx.webrender.software: true")
            }

            // --- Motion ----------------------------------------------------
            // Ask sites for reduced motion and stop animated images.
            appendLine("  ui.prefersReducedMotion: 1")
            appendLine("  image.animation_mode: \"none\"")

            // --- Background network / telemetry ----------------------------
            appendLine("  toolkit.telemetry.enabled: false")
            appendLine("  datareporting.healthreport.uploadEnabled: false")
            appendLine("  network.captive-portal-service.enabled: false")
            appendLine("  network.connectivity-service.enabled: false")
            appendLine("  network.prefetch-next: false")
            appendLine("  network.dns.disablePrefetch: true")
        }
        try {
            if (!file.exists() || file.readText() != yaml) {
                file.writeText(yaml)
            }
        } catch (_: Exception) {
            // If the file cannot be written Gecko falls back to its defaults.
        }
        return file.absolutePath
    }
}
