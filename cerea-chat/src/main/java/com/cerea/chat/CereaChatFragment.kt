package com.cerea.chat

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import org.json.JSONObject

/**
 * Embeds the Cerea chat widget as a full-screen Android Fragment.
 *
 * Usage:
 * ```kotlin
 * val chat = CereaChatFragment.newInstance(
 *     token = "L7kQp3...nVx2",
 *     userToken = userTokenFromYourBackend,        // optional, enables history
 *     attributes = mapOf("name" to "Luka", "plan" to "pro")
 * )
 * supportFragmentManager
 *     .beginTransaction()
 *     .replace(R.id.container, chat)
 *     .addToBackStack(null)
 *     .commit()
 *
 * // Dynamic context updates
 * chat.updateContext(mapOf("current_screen" to "billing"))
 * ```
 *
 * `userToken` is an HS256 JWT signed by your backend with the agent's HMAC
 * secret (claims: `aud: "cerea-identity"`, `user_id`, `exp` ≤24h). When
 * supplied, conversations persist across devices/reinstalls and the widget's
 * history drawer is enabled.
 *
 * The web widget detects `surface=android` and skips its launcher chrome.
 *
 * AndroidManifest.xml needs `<uses-permission android:name="android.permission.INTERNET" />`
 * (plus CAMERA / READ_MEDIA_IMAGES if you allow attachments).
 */
class CereaChatFragment : Fragment() {

    private var webView: WebView? = null
    private var token: String = ""
    private var attributesJson: String = "{}"
    private var userToken: String? = null
    private var host: String = DEFAULT_HOST
    private var hostOrigin: Uri = Uri.parse(DEFAULT_HOST)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val args = requireArguments()
        token = args.getString(ARG_TOKEN) ?: ""
        attributesJson = args.getString(ARG_ATTRIBUTES) ?: "{}"
        userToken = args.getString(ARG_USER_TOKEN)
        host = args.getString(ARG_HOST) ?: DEFAULT_HOST
        hostOrigin = Uri.parse(host)

        val widget = WebView(requireContext()).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            // Security: lock down filesystem and content:// access. The widget
            // is loaded from app.cerea.ai over HTTPS — it has no business
            // reaching into the app's sandbox.
            settings.allowFileAccess = false
            settings.allowContentAccess = false

            webChromeClient = object : WebChromeClient() {
                /**
                 * Grant camera/mic access ONLY when the request originates from
                 * the configured Cerea host. The widget may request these for
                 * voice messages or video chat; everything else (third-party
                 * iframes etc.) is denied.
                 */
                override fun onPermissionRequest(request: PermissionRequest) {
                    val requestHost = request.origin?.host
                    if (requestHost != null && requestHost == hostOrigin.host) {
                        request.grant(request.resources)
                    } else {
                        request.deny()
                    }
                }

                /** Surface widget console.log/error to logcat for easier debugging. */
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.d(
                        TAG,
                        "[widget:${message.messageLevel()}] ${message.message()} " +
                            "(${message.sourceId()}:${message.lineNumber()})"
                    )
                    return true
                }
            }

            webViewClient = object : WebViewClient() {
                /**
                 * Inject window.cereaConfig + surface flag the moment the real
                 * page starts loading. Doing this in the WebView block (before
                 * loadUrl) lands on an empty document and is wiped when the
                 * target page loads — that was the v0.1.0 bug.
                 */
                override fun onPageStarted(
                    view: WebView,
                    url: String?,
                    favicon: android.graphics.Bitmap?
                ) {
                    super.onPageStarted(view, url, favicon)
                    if (url == null || url.startsWith("about:")) return
                    val safeAttrs = escapeJsLineSeparators(attributesJson)
                    val safeUserToken = userToken?.takeIf {
                        it.matches(USER_TOKEN_PATTERN)
                    }
                    val userTokenAssign = if (safeUserToken != null) {
                        val tokenJson = escapeJsLineSeparators(
                            JSONObject().put("userToken", safeUserToken).toString()
                        )
                        "Object.assign(window.cereaConfig, $tokenJson);"
                    } else ""
                    view.evaluateJavascript(
                        """
                        (function() {
                          window.cereaConfig = $safeAttrs;
                          $userTokenAssign
                          window.cereaSurface = 'android';
                        })();
                        """.trimIndent(),
                        null
                    )
                }

                /**
                 * External links (privacy policy, help center, etc.) open in the
                 * system browser instead of trapping the user inside the chat
                 * WebView with no way back. Same-host navigations stay in-app.
                 */
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val target = request.url
                    if (target.host == hostOrigin.host) return false
                    return try {
                        startActivity(Intent(Intent.ACTION_VIEW, target))
                        true
                    } catch (_: Exception) {
                        // No browser installed — fall back to in-WebView load.
                        false
                    }
                }
            }
        }

        webView = widget
        val packageName = requireContext().packageName
        widget.loadUrl("$host/w/$token", mapOf("X-Cerea-Package" to packageName))
        return widget
    }

    /**
     * Merge additional context into the active conversation. The next user
     * message will see the merged context in the AI's system prompt.
     */
    fun updateContext(patch: Map<String, Any?>) {
        val json = escapeJsLineSeparators(JSONObject(patch).toString())
        webView?.evaluateJavascript(
            """
            window.postMessage({
              ns: 'cerea.widget.v1',
              type: 'context-patch',
              payload: $json
            }, '*');
            """.trimIndent(),
            null
        )
    }

    override fun onDestroyView() {
        // Order matters: detach clients, navigate away, then destroy. Without
        // this, the WebView's renderer process can outlive the Fragment and
        // post messages to a destroyed view (crash on rotation in v0.1.0).
        webView?.let { wv ->
            wv.webChromeClient = null
            wv.webViewClient = WebViewClient()
            wv.loadUrl("about:blank")
            wv.removeAllViews()
            wv.destroy()
        }
        webView = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "CereaChat"
        private const val DEFAULT_HOST = "https://app.cerea.ai"
        private const val ARG_TOKEN = "cerea_token"
        private const val ARG_ATTRIBUTES = "cerea_attributes"
        private const val ARG_USER_TOKEN = "cerea_user_token"
        private const val ARG_HOST = "cerea_host"

        // base64url segments joined by '.'
        private val USER_TOKEN_PATTERN = Regex("^[A-Za-z0-9_.-]+$")

        /**
         * U+2028 (LINE SEPARATOR) and U+2029 (PARAGRAPH SEPARATOR) are valid
         * in JSON strings but terminate JS string literals on older engines.
         * Modern Chromium WebView tolerates them, but escape defensively so a
         * single backported WebView can't trigger an injection.
         */
        private fun escapeJsLineSeparators(json: String): String =
            json.replace(" ", "\\u2028").replace(" ", "\\u2029")

        fun newInstance(
            token: String,
            userToken: String? = null,
            attributes: Map<String, Any?> = emptyMap(),
            host: String = DEFAULT_HOST,
        ): CereaChatFragment {
            return CereaChatFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TOKEN, token)
                    putString(ARG_ATTRIBUTES, JSONObject(attributes).toString())
                    if (userToken != null) putString(ARG_USER_TOKEN, userToken)
                    putString(ARG_HOST, host)
                }
            }
        }
    }
}
