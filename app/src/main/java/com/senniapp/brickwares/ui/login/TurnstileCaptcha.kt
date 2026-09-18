package com.senniapp.brickwares.ui.login

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senniapp.brickwares.R
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Bot protection for the email auth flows (handoff §0ar): Cloudflare **Turnstile** in *Managed* mode.
 * GoTrue requires a `captcha_token` on sign-up, password sign-in, resend and recover once captcha is
 * enabled on the project; Google ID-token sign-in and OTP verify are exempt, so only the email paths
 * go through here.
 *
 * Flow: the ViewModel calls [CaptchaGate.acquire] right before an auth call. That flips [CaptchaGate.state]
 * to active, and [TurnstileCaptchaHost] (rendered by the login screen) loads the widget page
 * `https://brickwares.app/captcha` in a **hidden** WebView. Turnstile scores the request invisibly and,
 * for a normal user, hands a token straight back through the [TurnstileBridge] — nothing is ever shown.
 * Only when Cloudflare wants interaction does the page fire `before-interactive-callback`, and the host
 * reveals the same WebView as a small centered card with the checkbox (never an image puzzle). Tokens
 * are single-use and expire after 5 minutes, so a fresh one is acquired per call.
 *
 * A blank site key (the dev flavor by default) disables the gate: [acquire] returns [CaptchaOutcome.Disabled]
 * and callers send no token — matching a local stack with `[auth.captcha] enabled = false`.
 */
class CaptchaGate(val siteKey: String) {

    /** What the host should render. [nonce] changes per acquisition so the WebView reloads fresh. */
    data class State(val active: Boolean = false, val interactive: Boolean = false, val nonce: Int = 0)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val enabled: Boolean get() = siteKey.isNotBlank()

    private var pending: CompletableDeferred<CaptchaOutcome>? = null

    /**
     * Obtain a fresh Turnstile token, showing the challenge only if Cloudflare asks for one. Suspends
     * until the widget answers, the user dismisses an interactive challenge, or [TIMEOUT_MS] passes.
     */
    suspend fun acquire(): CaptchaOutcome {
        if (!enabled) return CaptchaOutcome.Disabled
        pending?.complete(CaptchaOutcome.Failed) // a stale request can't win over the new one
        val deferred = CompletableDeferred<CaptchaOutcome>()
        pending = deferred
        _state.update { State(active = true, interactive = false, nonce = it.nonce + 1) }
        return try {
            withTimeoutOrNull(TIMEOUT_MS) { deferred.await() } ?: run {
                Timber.tag(TAG).w("captcha timed out")
                CaptchaOutcome.Failed
            }
        } finally {
            if (pending === deferred) pending = null
            _state.update { it.copy(active = false, interactive = false) }
        }
    }

    // Bridge callbacks arrive on the WebView's JavaBridge thread; the deferred + StateFlow are thread-safe.
    internal fun onToken(token: String) {
        if (token.isBlank()) { onError("empty-token"); return }
        Timber.tag(TAG).d("captcha token acquired")
        pending?.complete(CaptchaOutcome.Token(token))
    }

    internal fun onError(reason: String) {
        Timber.tag(TAG).w("captcha failed: %s", reason)
        pending?.complete(CaptchaOutcome.Failed)
    }

    internal fun onInteractive() {
        Timber.tag(TAG).i("captcha wants interaction")
        _state.update { if (it.active) it.copy(interactive = true) else it }
    }

    /** The user tapped outside an interactive challenge — treat as failed so the caller stops waiting. */
    fun dismiss() = onError("dismissed")

    private companion object {
        const val TAG = "Captcha"
        /** Generous: an invisible pass takes ~1–3 s, an interactive checkbox needs a human. */
        const val TIMEOUT_MS = 90_000L
    }
}

sealed interface CaptchaOutcome {
    /** No site key configured → send no token (the server isn't enforcing captcha in this environment). */
    data object Disabled : CaptchaOutcome
    data class Token(val value: String) : CaptchaOutcome
    /** Widget error, page unreachable, dismissed, or timed out → show `login_err_captcha`, don't call auth. */
    data object Failed : CaptchaOutcome
}

/**
 * Object the widget page calls (`BrickWaresCaptcha.onToken(...)` etc.). Kept by name in R8 via the
 * default `@JavascriptInterface` rule + an explicit one in rules.keep. Only these three methods are
 * exposed to the page, and the page is our own HTTPS document.
 */
class TurnstileBridge(private val gate: CaptchaGate) {
    @JavascriptInterface fun onToken(token: String) = gate.onToken(token)
    @JavascriptInterface fun onError(code: String) = gate.onError(code)
    @JavascriptInterface fun onInteractive() = gate.onInteractive()
}

/**
 * Renders the Turnstile WebView while [gate] is active. Invisible (1 dp, alpha 0) during the silent
 * check; when the widget asks for interaction the SAME WebView node is shown in a centered card over a
 * scrim — only modifiers change, so the in-progress challenge isn't recreated. Place it last inside the
 * login card's Box so it overlays the form.
 */
@Composable
fun TurnstileCaptchaHost(gate: CaptchaGate, languageTag: String, modifier: Modifier = Modifier) {
    val state by gate.state.collectAsStateWithLifecycle()
    if (!state.active) return
    val colors = BwTheme.colors
    val interactive = state.interactive
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (interactive) {
                    Modifier
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(interactionSource = interaction, indication = null) { gate.dismiss() }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // ⚠️ The WebView must have its FINAL size from the first frame. Android WebView fixes the page's
        // layout width + scale when it loads, so loading at 1 dp and growing it later rendered the widget
        // ~4× too large (2026-09-18, Samsung S23 Ultra). Hence: identical layout in both phases — only
        // alpha (and the title) change — and the hidden phase is merely transparent. It briefly covers
        // part of the form, which is disabled while the request is in flight anyway.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (interactive) 1f else 0f)
                .background(colors.card, RoundedCornerShape(16.dp))
                // Swallow taps inside the panel so they don't reach the scrim's dismiss.
                .clickable(enabled = interactive, interactionSource = interaction, indication = null) {}
                // Only 4 dp at the sides: the login card is (screen − 48 dp) wide, so a 360 dp phone
                // leaves 312 − 8 = 304 dp — just enough for Turnstile's 300 px "normal" widget.
                .padding(horizontal = 4.dp, vertical = 12.dp),
        ) {
            if (interactive) {
                Text(stringResource(R.string.login_captcha_title), style = BwType.cardTitle, color = colors.text)
                Spacer(Modifier.height(12.dp))
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Narrower than the 300×65 "normal" widget (small phone, or a large display-size
                // setting) → ask the page for Turnstile's 150×140 "compact" widget instead of clipping.
                val compact = maxWidth < 300.dp
                key(state.nonce, compact) {
                    TurnstileWebView(
                        gate = gate,
                        url = captchaPageUrl(gate.siteKey, languageTag, colors.isDark, compact),
                        modifier = Modifier.fillMaxWidth().height(if (compact) 148.dp else 72.dp),
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TurnstileWebView(gate: CaptchaGate, url: String, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true // the widget is JavaScript; our page, our host
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                // Scale discipline: honour the page's viewport meta (width=device-width, scale 1 → one
                // CSS px per dp), never zoom, and ignore the system font scale (a Samsung "large font"
                // setting would otherwise inflate the widget text).
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = false
                settings.textZoom = 100
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                webViewClient = object : WebViewClient() {
                    // Keep the main frame on our page (+ Cloudflare's challenge host). Anything else → refuse.
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        request.isForMainFrame && !isAllowedHost(request.url)

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) gate.onError("load:${error.errorCode}")
                    }

                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                        if (request.isForMainFrame) gate.onError("http:${errorResponse.statusCode}")
                    }
                }
                addJavascriptInterface(TurnstileBridge(gate), BRIDGE_NAME)
                loadUrl(url)
            }
        },
        onRelease = { it.stopLoading(); it.destroy() },
    )
}

private const val BRIDGE_NAME = "BrickWaresCaptcha"
/** The widget page lives on the site (brickwares-site/captcha.html); Cloudflare Pages serves it at /captcha. */
private const val CAPTCHA_PAGE = "https://brickwares.app/captcha"
private val ALLOWED_HOSTS = setOf("brickwares.app", "www.brickwares.app", "challenges.cloudflare.com")

private fun isAllowedHost(uri: Uri): Boolean = uri.scheme == "https" && uri.host in ALLOWED_HOSTS

private fun captchaPageUrl(siteKey: String, languageTag: String, dark: Boolean, compact: Boolean): String =
    Uri.parse(CAPTCHA_PAGE).buildUpon()
        .appendQueryParameter("sitekey", siteKey)
        .appendQueryParameter("lang", languageTag)
        .appendQueryParameter("theme", if (dark) "dark" else "light")
        .appendQueryParameter("size", if (compact) "compact" else "normal")
        .build()
        .toString()
