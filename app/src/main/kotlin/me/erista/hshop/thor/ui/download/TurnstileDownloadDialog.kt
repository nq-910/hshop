package me.erista.hshop.thor.ui.download

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import me.erista.hshop.model.HShopTitleDetail

private const val TAG = "TurnstileDialog"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TurnstileDownloadDialog(
    detail: HShopTitleDetail,
    onDismiss: () -> Unit,
    onDownloadUrlResolved: (String) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Download Verification",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Text(
                    text = "Complete the Cloudflare check below to download '${detail.name}'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF101216))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    javaScriptCanOpenWindowsAutomatically = true
                                    mediaPlaybackRequiresUserGesture = false
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    userAgentString = WebSettings.getDefaultUserAgent(ctx)
                                }

                                webChromeClient = WebChromeClient()

                                // Catch any direct download triggers from WebView
                                setDownloadListener { url, _, _, _, _ ->
                                    Log.d(TAG, "DownloadListener triggered: $url")
                                    onDownloadUrlResolved(url)
                                    onDismiss()
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isLoading = true
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val targetUrl = request?.url?.toString() ?: ""
                                        Log.d(TAG, "shouldOverrideUrlLoading: $targetUrl")
                                        if (targetUrl.contains("/d/") || targetUrl.contains(".cia") || (targetUrl.contains("download") && !targetUrl.contains("download-widget"))) {
                                            val fullUrl = if (targetUrl.startsWith("http")) targetUrl else "https://hshop.erista.me$targetUrl"
                                            onDownloadUrlResolved(fullUrl)
                                            onDismiss()
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                        Log.d(TAG, "Page finished: $url")

                                        // Inject MutationObserver & polling to automatically capture and click the direct download link
                                        view?.evaluateJavascript(
                                            """
                                            (function() {
                                                function checkLinks() {
                                                    var box = document.getElementById('landing-box') || document.body;
                                                    var links = box.querySelectorAll('a');
                                                    for (var i = 0; i < links.length; i++) {
                                                        var href = links[i].getAttribute('href') || links[i].href;
                                                        if (href && (href.indexOf('/d/') !== -1 || href.indexOf('.cia') !== -1 || href.indexOf('download') !== -1 && href.indexOf('download-widget') === -1)) {
                                                            window.AndroidBridge.onUrlFound(href);
                                                            return true;
                                                        }
                                                    }
                                                    return false;
                                                }

                                                // Check immediately
                                                checkLinks();

                                                // Mutation Observer on landing-box
                                                var observer = new MutationObserver(function(mutations) {
                                                    checkLinks();
                                                });
                                                var target = document.getElementById('landing-box') || document.body;
                                                observer.observe(target, { childList: true, subtree: true });

                                                // Polling fallback
                                                setInterval(checkLinks, 500);

                                                // Hook fetch
                                                var origFetch = window.fetch;
                                                window.fetch = function() {
                                                    var fetchUrl = arguments[0];
                                                    return origFetch.apply(this, arguments).then(function(response) {
                                                        if (typeof fetchUrl === 'string' && fetchUrl.includes('download-widget')) {
                                                            response.clone().text().then(function(body) {
                                                                var match = body.match(/href="([^"]*(\/d\/[^"]+|\.cia[^"]*))"/i) || body.match(/href="([^"]+)"/i);
                                                                if (match) {
                                                                    window.AndroidBridge.onUrlFound(match[1]);
                                                                }
                                                            });
                                                        }
                                                        return response;
                                                    });
                                                };
                                            })();
                                            """.trimIndent(),
                                            null
                                        )
                                    }
                                }

                                addJavascriptInterface(object {
                                    @JavascriptInterface
                                    fun onUrlFound(url: String) {
                                        Log.d(TAG, "JS Bridge found URL: $url")
                                        val fullUrl = if (url.startsWith("http")) url else "https://hshop.erista.me$url"
                                        post {
                                            onDownloadUrlResolved(fullUrl)
                                            onDismiss()
                                        }
                                    }
                                }, "AndroidBridge")

                                loadUrl("https://hshop.erista.me/t/${detail.id}")
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
