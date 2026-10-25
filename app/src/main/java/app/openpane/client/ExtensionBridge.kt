package app.openpane.client

import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import uniffi.openpane_core.Engine
import uniffi.openpane_core.FilterRules
import uniffi.openpane_core.PostSummary
import uniffi.openpane_core.Verdict

/**
 * Process-wide bridge between the bundled WebExtension and the Rust core.
 *
 * It lives outside the Activity on purpose: the extension and its message
 * delegates belong to the GeckoRuntime, which outlives any Activity. Keeping
 * this code in the Activity leaked the old Activity (and its views) every time
 * the app was reopened in the same process, and the new Activity never received
 * the extension's port.
 */
object ExtensionBridge {
    private const val EXTENSION_ID = "openpane-extension@openpane.app"
    private const val EXTENSION_LOCATION = "resource://android/assets/extension/"
    private const val NATIVE_APP_ID = "openpane"

    // Current rules. Muted lists are empty for now; when they are filled the
    // extension starts sending posts to the Rust engine (see needsEvaluation).
    private val mutedWords: List<String> = emptyList()
    private val mutedAccounts: List<String> = emptyList()
    private const val HIDE_PROMOTED = true
    private const val HIDE_SUGGESTIONS = false

    private var installRequested = false
    private var activePort: WebExtension.Port? = null
    private var engine: Engine? = null

    private val needsEvaluation: Boolean
        get() = mutedWords.isNotEmpty() || mutedAccounts.isNotEmpty()

    /** Installs the bundled extension once per process. Safe to call repeatedly. */
    fun install(runtime: GeckoRuntime) {
        if (installRequested) return
        installRequested = true
        runtime.webExtensionController
            .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
            .accept({ extension ->
                if (extension != null) {
                    attach(extension)
                } else {
                    installRequested = false
                }
            }, { _ ->
                installRequested = false
            })
    }

    private fun attach(extension: WebExtension) {
        extension.setMessageDelegate(object : WebExtension.MessageDelegate {
            override fun onConnect(port: WebExtension.Port) {
                activePort = port
                port.setDelegate(object : WebExtension.PortDelegate {
                    override fun onPortMessage(message: Any, port: WebExtension.Port) {
                        handleMessage(message, port)
                    }

                    override fun onDisconnect(port: WebExtension.Port) {
                        if (activePort === port) activePort = null
                    }
                })
                sendRules()
            }
        }, NATIVE_APP_ID)
    }

    private fun sendRules() {
        activePort?.postMessage(
            JSONObject().put("type", "openpane.rules")
                .put("hideSuggestions", HIDE_SUGGESTIONS)
                .put("hidePromoted", HIDE_PROMOTED)
                .put("needsEvaluation", needsEvaluation)
        )
    }

    private fun handleMessage(message: Any, port: WebExtension.Port) {
        val request = message as? JSONObject ?: return
        if (request.optString("type") != "openpane.evaluate") return
        val postsJson = request.optJSONArray("posts") ?: return
        val posts = buildList {
            for (index in 0 until postsJson.length()) {
                val post = postsJson.optJSONObject(index) ?: continue
                add(
                    PostSummary(
                        id = post.optString("id"),
                        author = post.optString("author"),
                        text = post.optString("text"),
                        promoted = post.optBoolean("promoted")
                    )
                )
            }
        }
        if (posts.isEmpty()) return
        val current = engine ?: Engine(
            FilterRules(
                mutedWords = mutedWords,
                mutedAccounts = mutedAccounts,
                hidePromoted = HIDE_PROMOTED
            )
        ).also { engine = it }
        val verdicts = current.evaluateBatch(posts)
        val hiddenIds = JSONArray()
        posts.zip(verdicts).forEach { (post, verdict) ->
            if (verdict == Verdict.HIDE) hiddenIds.put(post.id)
        }
        port.postMessage(
            JSONObject().put("type", "openpane.verdicts").put("hiddenIds", hiddenIds)
        )
    }
}
