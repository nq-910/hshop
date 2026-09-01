package me.erista.hshop.thor.download

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import me.erista.hshop.model.HShopTitleDetail
import kotlin.coroutines.resume

object AutoDownloadResolver {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; AYN Thor) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    /**
     * Headlessly loads the hShop title page, automatically completes Turnstile,
     * and extracts the direct .cia download URL.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolveDownloadUrl(context: Context, detail: HShopTitleDetail): String? {
        return withTimeoutOrNull(25000) {
            suspendCancellableCoroutine { continuation ->
                val mainHandler = Handler(Looper.getMainLooper())

                mainHandler.post {
                    var isCompleted = false
                    val webView = WebView(context)

                    fun finishWithUrl(url: String?) {
                        if (!isCompleted) {
                            isCompleted = true
                            mainHandler.post {
                                webView.stopLoading()
                                webView.destroy()
                            }
                            continuation.resume(url)
                        }
                    }

                    continuation.invokeOnCancellation {
                        mainHandler.post {
                            webView.stopLoading()
                            webView.destroy()
                        }
                    }

                    val settings = webView.settings
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.userAgentString = USER_AGENT
                    settings.cacheMode = WebSettings.LOAD_DEFAULT

                    webView.addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onDirectUrlFound(url: String) {
                            val fullUrl = if (url.startsWith("http")) url else "https://hshop.erista.me$url"
                            finishWithUrl(fullUrl)
                        }

                        @JavascriptInterface
                        fun onToken(token: String) {
                            // Fetch download-widget directly
                            mainHandler.post {
                                webView.evaluateJavascript(
                                    """
                                    fetch('/t/${detail.id}/download-widget?captcha_token=' + encodeURIComponent('$token'))
                                        .then(function(r) { return r.text(); })
                                        .then(function(html) {
                                            var m = html.match(/href="(\/d\/[^"]+)"/i) || html.match(/href="(https?:\/\/[^"]+\.cia[^"]*)"/i);
                                            if (m) {
                                                window.AutoSolver.onDirectUrlFound(m[1]);
                                            }
                                        });
                                    """.trimIndent(),
                                    null
                                )
                            }
                        }
                    }, "AutoSolver")

                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            // Inject auto-solver and interceptor scripts
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    // 1. Intercept fetch & XHR
                                    var origFetch = window.fetch;
                                    window.fetch = function() {
                                        var fetchUrl = arguments[0];
                                        return origFetch.apply(this, arguments).then(function(response) {
                                            if (typeof fetchUrl === 'string' && fetchUrl.includes('download-widget')) {
                                                response.clone().text().then(function(body) {
                                                    var match = body.match(/href="(\/d\/[^"]+)"/i) || body.match(/href="(https?:\/\/[^"]+\.cia[^"]*)"/i);
                                                    if (match) {
                                                        window.AutoSolver.onDirectUrlFound(match[1]);
                                                    }
                                                });
                                            }
                                            return response;
                                        });
                                    };

                                    // 2. Hook global submitCaptcha if present
                                    if (typeof window.submitCaptcha === 'function') {
                                        var origSubmit = window.submitCaptcha;
                                        window.submitCaptcha = function(t) {
                                            window.AutoSolver.onToken(t);
                                            return origSubmit.apply(this, arguments);
                                        };
                                    }

                                    // 3. Auto-trigger turnstile widget
                                    var attempts = 0;
                                    var timer = setInterval(function() {
                                        attempts++;
                                        if (attempts > 30) {
                                            clearInterval(timer);
                                            return;
                                        }

                                        // Check if turnstile API is loaded
                                        if (window.turnstile && typeof window.turnstile.getResponse === 'function') {
                                            var resp = window.turnstile.getResponse();
                                            if (resp && resp.length > 5) {
                                                window.AutoSolver.onToken(resp);
                                                clearInterval(timer);
                                                return;
                                            }
                                        }

                                        // Try clicking the turnstile box
                                        var widget = document.querySelector('.cf-turnstile') || document.querySelector('#landing-box');
                                        if (widget) {
                                            var iframe = widget.querySelector('iframe');
                                            if (iframe) {
                                                iframe.click();
                                            }
                                            widget.click();
                                        }
                                    }, 400);
                                })();
                                """.trimIndent(),
                                null
                            )
                        }
                    }

                    webView.loadUrl("https://hshop.erista.me/t/${detail.id}")
                }
            }
        }
    }
}
