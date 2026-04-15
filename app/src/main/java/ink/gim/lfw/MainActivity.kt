package ink.gim.lfw

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.View
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ink.gim.lfw.ui.theme.LFWAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

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
      var gameUrl by remember { mutableStateOf<String?>(null) }
      var text by remember { mutableStateOf<String>("") }
      var pageStatus by remember { mutableStateOf("loading") }
      val alpha = remember { Animatable(0f) }
      val context = LocalContext.current
      val coroutineScope = rememberCoroutineScope()
      var retryFlag by remember { mutableIntStateOf(0) }
      LaunchedEffect(retryFlag) {
        withContext(Dispatchers.IO) {
          try {
            pageStatus = "loading"
            val indexUrl = "https://gim.ink/api/lfwm/find?id=1"
            text = "loading: $indexUrl"
            val infoPath = HttpReq(indexUrl)
              .json()
              .first
              .getJSONObject("data")
              .getString("oss_name")
            val infoUrl = "https://lfwm.gim.ink/$infoPath"
            text += "\nloading: $infoUrl"
            gameUrl = HttpReq(infoUrl).json().first.getString("url")
            text += "\nloading: $gameUrl"
          } catch (e: Exception) {
            e.printStackTrace()
            text += "\n${e.message}\n${e.stackTraceToString()}"
            pageStatus = "failed"
            withContext(Dispatchers.Main) {
              Toast.makeText(context, "Failed!", Toast.LENGTH_SHORT).show()
            }
          }
        }
      }

      LFWAppTheme {
        Scaffold(
          modifier = Modifier
            .fillMaxSize()
        ) { innerPadding ->
          gameUrl?.let { gameUrl ->
            WebView(
              url = gameUrl,
              modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .alpha(alpha.value),
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

                  override fun onCreateWindow(
                    view: WebView,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message
                  ): Boolean {
                    val wv = WebView(view.context)
                    val transport = resultMsg.obj as WebView.WebViewTransport
                    transport.webView = wv
                    wv.webViewClient = object : WebViewClient() {
                      override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                        view.context.startActivity(intent)
                        return true
                      }
                    }
                    resultMsg.sendToTarget()
                    return true
                  }
                }
                it.webViewClient = object : WebViewClient() {
                  override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    view?.loadUrl(url.orEmpty())
                    return true
                  }

                  override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (pageStatus == "done") return
                    pageStatus = "done"
                    coroutineScope.launch {
                      alpha.animateTo(1.0f, tween(1000))
                    }
                    Toast.makeText(context, "Page Loaded!", Toast.LENGTH_SHORT).show()
                  }
                }
              }
            )
          }

          if (alpha.value < 1) {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .alpha(1 - alpha.value)
            ) {
              Box(
                modifier = Modifier
                  .weight(1f)
                  .fillMaxWidth()
                  .verticalScroll(rememberScrollState())
              ) {
                SelectionContainer {
                  Text(
                    text = text,
                    modifier = Modifier.padding(10.dp)
                  )
                }
              }
              if (pageStatus == "failed") {
                Row(
                  horizontalArrangement = Arrangement.SpaceAround,
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                ) {
                  Button(onClick = {
                    retryFlag += 1
                  }) {
                    Text(text = "Retry")
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

