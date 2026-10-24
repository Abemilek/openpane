package app.openpane.client

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ProgressBar
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension

/**
 * Minimal container: one screen with GeckoView loading the X website.
 * The prototype validates login, the For You feed, scrolling, and video playback.
 */
class MainActivity : ComponentActivity() {

    private lateinit var geckoView: GeckoView
    private lateinit var popupGeckoView: GeckoView
    private lateinit var progressBar: ProgressBar
    private var canGoBack = false
    private var popupSession: GeckoSession? = null
    private var builtInExtension: WebExtension? = null
    private var extensionPort: WebExtension.Port? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        geckoView = findViewById(R.id.gecko_view)
        popupGeckoView = findViewById(R.id.popup_gecko_view)
        progressBar = findViewById(R.id.progress)

        // GeckoRuntime is process-wide, following the GeckoView integration pattern.
        val rt = runtime ?: GeckoRuntime.create(applicationContext).also { runtime = it }
        installBuiltInExtension(rt)

        val isNewSession = session == null
        val s = session ?: GeckoSession().also {
            it.open(rt)
            session = it
        }

        attachDelegates(s)
        geckoView.setSession(s)
        if (isNewSession) {
            s.loadUri(START_URL)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (popupSession != null) {
                    closePopupSession()
                } else if (canGoBack) {
                    session?.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun attachDelegates(s: GeckoSession) {
        s.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                progressBar.visibility = View.GONE
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                progressBar.progress = progress
            }
        }

        s.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                this@MainActivity.canGoBack = canGoBack
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                val scheme = Uri.parse(request.uri).scheme?.lowercase()
                if (scheme != null && scheme in EXTERNAL_SCHEMES) {
                    openExternally(request.uri)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            // GeckoView requires a new session for window.open()/TARGET_WINDOW_NEW.
            // Show it in an overlay so OAuth can complete and communicate with the opener.
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                closePopupSession()
                val popup = GeckoSession()
                popup.open(runtime ?: return null)
                popupSession = popup
                attachDelegates(popup)
                popupGeckoView.visibility = View.VISIBLE
                popupGeckoView.setSession(popup)
                return GeckoResult.fromValue(popup)
            }
        }

        s.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                applyImmersiveMode(fullScreen)
            }

            override fun onCloseRequest(session: GeckoSession) {
                if (session === popupSession) {
                    closePopupSession()
                }
            }
        }
    }

    private fun installBuiltInExtension(rt: GeckoRuntime) {
        if (builtInExtension != null) return
        rt.webExtensionController
            .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
            .accept({ extension ->
                extension?.let { installed ->
                builtInExtension = installed
                installed.setMessageDelegate(object : WebExtension.MessageDelegate {
                    override fun onConnect(port: WebExtension.Port) {
                        extensionPort = port
                        port.setDelegate(object : WebExtension.PortDelegate {
                            override fun onDisconnect(port: WebExtension.Port) {
                                if (extensionPort === port) extensionPort = null
                            }
                        })
                        sendExtensionRules()
                    }
                }, NATIVE_APP_ID)
                }
            }, { _ -> })
    }

    private fun sendExtensionRules() {
        extensionPort?.postMessage(
            JSONObject().put("type", "openpane.rules").put("hideSuggestions", false)
        )
    }

    private fun closePopupSession() {
        val popup = popupSession ?: return
        popupGeckoView.releaseSession()
        popup.progressDelegate = null
        popup.navigationDelegate = null
        popup.contentDelegate = null
        popup.close()
        popupSession = null
        extensionPort = null
        popupGeckoView.visibility = View.GONE
    }

    private fun openExternally(uri: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // Ignore the URI when no installed application can handle it.
        }
    }

    private fun applyImmersiveMode(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 30) {
            val controller = window.insetsController ?: return
            if (on) {
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (on) {
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            } else {
                0
            }
        }
    }

    override fun onDestroy() {
        closePopupSession()
        geckoView.releaseSession()
        if (isFinishing) {
            // Close the session when leaving the app so Gecko does not retain process memory.
            // X cookies and session data remain in the app profile.
            session?.close()
            session = null
        } else {
            session?.progressDelegate = null
            session?.navigationDelegate = null
            session?.contentDelegate = null
        }
        super.onDestroy()
    }

    companion object {
        private const val START_URL = "https://x.com/home"
        private const val EXTENSION_ID = "openpane-extension@openpane.app"
        private const val EXTENSION_LOCATION = "resource://android/assets/extension/"
        private const val NATIVE_APP_ID = "openpane"
        private val EXTERNAL_SCHEMES = setOf("mailto", "tel", "sms", "geo", "market", "intent")

        private var runtime: GeckoRuntime? = null
        private var session: GeckoSession? = null
    }
}
