package ink.gim.lfw

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebView(url: String, modifier: Modifier = Modifier, handleWebView: (it: WebView) -> Unit = {}) {
  val context = LocalContext.current
  // 本地 HTTP 服务器：为 assets/www 提供大文件（>16MB）与 Range 支持
  val assetServer = remember { LocalAssetServer.acquire(context).also { it.start() } }
  // 服务器实际端口可能因占用而动态变化，将指向 localhost 的 URL 重写为实际端口
  val effectiveUrl = remember(url, assetServer.port) { rewriteLocalUrl(url, assetServer.port) }
  val webView = remember {
    WebView(context).apply {
      setLayerType(View.LAYER_TYPE_HARDWARE, null)
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      setBackgroundColor(0)
      background = null
      settings.javaScriptEnabled = true
      settings.domStorageEnabled = true
      settings.allowFileAccess = true
      settings.allowContentAccess = true
      settings.setSupportZoom(false)
      settings.displayZoomControls = false
      settings.builtInZoomControls = false
      settings.mediaPlaybackRequiresUserGesture = false
      settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
      settings.javaScriptCanOpenWindowsAutomatically = true
      settings.setSupportMultipleWindows(true)
      settings.userAgentString = settings.userAgentString + " lfw-mobile-container"
      // 拦截 http://localhost:3000/ 请求，从 assets/www 提供本地文件
      webViewClient = LocalWebViewClient(context)
      handleWebView(this)
    }
  }
  AndroidView(
    factory = { webView },
    modifier = modifier
  ) { view ->
    if (view.url != effectiveUrl) {
      view.loadUrl(effectiveUrl)
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      webView.stopLoading()
      webView.destroy()
      LocalAssetServer.release(assetServer)
    }
  }

  // 返回键网页回退
  BackHandler(enabled = webView.canGoBack()) {
    webView.goBack()
  }
}

/**
 * 将指向 localhost / 127.0.0.1 且使用默认端口的 URL 重写为服务器实际绑定的端口。
 * 服务器绑定失败（port <= 0）或 URL 非本地地址时原样返回。
 */
private fun rewriteLocalUrl(url: String, port: Int): String {
  if (port <= 0) return url
  return try {
    val uri = url.toUri()
    val isLocal = uri.host.equals("localhost", ignoreCase = true) ||
      uri.host.equals("127.0.0.1", ignoreCase = true)
    if (isLocal && (uri.port == -1 || uri.port == LocalAssetServer.DEFAULT_PORT)) {
      uri.buildUpon().port(port).build().toString()
    } else {
      url
    }
  } catch (e: Exception) {
    url
  }
}