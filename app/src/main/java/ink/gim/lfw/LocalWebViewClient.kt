package ink.gim.lfw

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * 本地资源拦截器（兜底）：正常情况下由 [LocalAssetServer]（本地 HTTP 服务器）提供资源，
 * 支持大文件（>16MB）与 Range 请求。
 *
 * 仅当本地服务器尚未就绪时，本拦截器才兜底拦截 [baseHost] 域下的请求，
 * 从 assets/[assetRoot] 返回本地文件，避免页面首屏在服务器启动前加载失败。
 */
class LocalWebViewClient(
  private val context: Context,
  private val baseHost: String = "localhost",
  private val basePort: Int = 3000,
  private val assetRoot: String = "www"
) : WebViewClient() {

  override fun shouldInterceptRequest(
    view: WebView,
    request: WebResourceRequest
  ): WebResourceResponse? {
    val url = request.url ?: return null
    // 只处理我们自己的本地地址，其余请求（外链等）走默认网络行为
    if (!url.host.equals(baseHost, ignoreCase = true)) return null
    // 端口判断：服务器运行时用其实际绑定端口，否则用配置的默认端口
    val effectivePort = LocalAssetServer.currentPort ?: basePort
    if (effectivePort > 0 && url.port in 1..65535 && url.port != effectivePort) return null
    // 本地 HTTP 服务器已就绪：交给真实网络栈处理（支持大文件与 Range），不再拦截
    if (LocalAssetServer.isAnyRunning) return null
    // 服务器未就绪：兜底从 assets 提供文件
    return loadFromAssets(url.path ?: "/")
  }

  private fun loadFromAssets(urlPath: String): WebResourceResponse? {
    val trimmed = urlPath.trimStart('/')
    val relative = if (trimmed.isEmpty()) "index.html" else trimmed
    val basePath = "$assetRoot/$relative"

    // 依次尝试：原路径 -> 若以 / 结尾补 index.html -> 若为目录风格补 index.html
    val candidates = buildList {
      add(basePath)
      if (basePath.endsWith("/")) add("${basePath}index.html") else add("$basePath/index.html")
    }

    for (candidate in candidates.distinct()) {
      try {
        val stream: InputStream = context.assets.open(candidate)
        return WebResourceResponse(guessMimeType(candidate), "utf-8", stream)
      } catch (e: FileNotFoundException) {
        // 尝试下一个候选路径
      }
    }
    // 未找到时返回 null，交由 WebView 默认处理（显示错误页）
    return null
  }

  private fun guessMimeType(fileName: String): String = MimeTypes.guess(fileName)
}
