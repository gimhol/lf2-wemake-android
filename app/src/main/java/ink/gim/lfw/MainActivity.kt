package ink.gim.lfw

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import ink.gim.lfw.ui.theme.LFWAppTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    // 全屏沉浸模式
    window.setFlags(
      WindowManager.LayoutParams.FLAG_FULLSCREEN,
      WindowManager.LayoutParams.FLAG_FULLSCREEN
    )
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    window.decorView.systemUiVisibility = (
      View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        or View.SYSTEM_UI_FLAG_FULLSCREEN
        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
      )
    setContent {
      LFWAppTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          FullScreenWebView(
            url = "https://lf.gim.ink/0.1.23",
            modifier = Modifier
              .padding(innerPadding)
              .fillMaxSize()
          )
        }
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FullScreenWebView(
  url: String,
  modifier: Modifier = Modifier
) {
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
      settings.mediaPlaybackRequiresUserGesture = false
      settings.cacheMode = WebSettings.LOAD_DEFAULT
      settings.userAgentString = settings.userAgentString + " lfw-mobile-container"
      webViewClient = WebViewClient()
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