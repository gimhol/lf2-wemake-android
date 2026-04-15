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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebView(url: String, modifier: Modifier = Modifier, handleWebView: (it: WebView) -> Unit = {}) {
  val context = LocalContext.current
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
      webViewClient = WebViewClient()
      handleWebView(this)
    }
  }
  AndroidView(
    factory = { webView },
    modifier = modifier
  ) { view ->
    if (view.url != url) {
      view.loadUrl(url)
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      webView.stopLoading()
      webView.destroy()
    }
  }

  // 返回键网页回退
  BackHandler(enabled = webView.canGoBack()) {
    webView.goBack()
  }
}