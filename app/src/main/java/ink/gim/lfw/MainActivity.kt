package ink.gim.lfw

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
  private var uploadMessage: ValueCallback<Array<Uri>>? = null
  private val filePickerLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == RESULT_OK) {
      val uri = result.data?.data
      uploadMessage?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
    } else {
      uploadMessage?.onReceiveValue(null)
    }
    uploadMessage = null
  }

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
              .fillMaxSize(),
            handleWebView = {
              it.webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                  webView: WebView?,
                  callback: ValueCallback<Array<Uri>>?,
                  params: FileChooserParams?
                ): Boolean {
                  uploadMessage = callback
                  val intent = params?.createIntent()
                  intent?.let { filePickerLauncher.launch(it) }
                  return true
                }
              }
            }
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
  modifier: Modifier = Modifier,
  handleWebView: (it: WebView) -> Unit = {},
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