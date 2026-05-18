package com.cerea.chat

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
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

    private lateinit var webView: WebView
    private var token: String = ""
    private var attributesJson: String = "{}"
    private var userToken: String? = null

    /** Override if you self-host the widget on a custom domain. */
    var host: String = "https://app.cerea.ai"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        token = requireArguments().getString(ARG_TOKEN) ?: ""
        attributesJson = requireArguments().getString(ARG_ATTRIBUTES) ?: "{}"
        userToken = requireArguments().getString(ARG_USER_TOKEN)

        // JWTs are base64url segments joined by '.', so this regex is a strict
        // structural check that also blocks anything that would break out of
        // the JSON.stringify below. We refuse to inject malformed values.
        val safeUserToken = userToken?.takeIf { it.matches(USER_TOKEN_PATTERN) }
        val userTokenJson =
            if (safeUserToken != null) JSONObject().put("userToken", safeUserToken).toString()
            else null

        webView = WebView(requireContext()).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()

            // Inject cereaConfig + surface flag before any page script runs.
            val userTokenAssign =
                if (userTokenJson != null) "Object.assign(window.cereaConfig, $userTokenJson);"
                else ""
            evaluateJavascript(
                """
                (function() {
                  window.cereaConfig = $attributesJson;
                  $userTokenAssign
                  window.cereaSurface = 'android';
                })();
                """.trimIndent(),
                null
            )
        }

        val packageName = requireContext().packageName
        webView.loadUrl(
            "$host/w/$token",
            mapOf("X-Cerea-Package" to packageName)
        )

        return webView
    }

    /**
     * Merge additional context into the active conversation. The next user
     * message will see the merged context in the AI's system prompt.
     */
    fun updateContext(patch: Map<String, Any?>) {
        val json = JSONObject(patch).toString()
        webView.evaluateJavascript(
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
        webView.destroy()
        super.onDestroyView()
    }

    companion object {
        private const val ARG_TOKEN = "cerea_token"
        private const val ARG_ATTRIBUTES = "cerea_attributes"
        private const val ARG_USER_TOKEN = "cerea_user_token"

        // base64url segments joined by '.'
        private val USER_TOKEN_PATTERN = Regex("^[A-Za-z0-9_.-]+$")

        fun newInstance(
            token: String,
            userToken: String? = null,
            attributes: Map<String, Any?> = emptyMap()
        ): CereaChatFragment {
            return CereaChatFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TOKEN, token)
                    putString(ARG_ATTRIBUTES, JSONObject(attributes).toString())
                    if (userToken != null) putString(ARG_USER_TOKEN, userToken)
                }
            }
        }
    }
}
