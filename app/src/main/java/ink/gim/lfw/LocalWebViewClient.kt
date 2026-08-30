package ink.gim.lfw

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.Locale

/**
 * 本地资源拦截器：拦截 [baseHost] 域下的所有请求，从 assets/[assetRoot] 提供本地文件。
 *
 * WebView 加载 http://localhost:3000/ 后，页面内所有相对路径请求
 * （js/css/图片/fetch/XHR 等）都会被这里拦截并返回本地文件，
 * 因此无需在设备上运行 HTTP 服务器。
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
    if (basePort > 0 && url.port in 1..65535 && url.port != basePort) return null
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

  private fun guessMimeType(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return MIME_TYPES[ext] ?: "application/octet-stream"
  }

  companion object {
    private val MIME_TYPES = mapOf(
      "html" to "text/html",
      "htm" to "text/html",
      "js" to "application/javascript",
      "mjs" to "application/javascript",
      "css" to "text/css",
      "json" to "application/json",
      "map" to "application/json",
      "png" to "image/png",
      "jpg" to "image/jpeg",
      "jpeg" to "image/jpeg",
      "gif" to "image/gif",
      "webp" to "image/webp",
      "svg" to "image/svg+xml",
      "ico" to "image/x-icon",
      "bmp" to "image/bmp",
      "avif" to "image/avif",
      "wasm" to "application/wasm",
      "mp3" to "audio/mpeg",
      "ogg" to "audio/ogg",
      "oga" to "audio/ogg",
      "wav" to "audio/wav",
      "m4a" to "audio/mp4",
      "flac" to "audio/flac",
      "mp4" to "video/mp4",
      "webm" to "video/webm",
      "woff" to "font/woff",
      "woff2" to "font/woff2",
      "ttf" to "font/ttf",
      "otf" to "font/otf",
      "txt" to "text/plain",
      "xml" to "text/xml",
      "webmanifest" to "application/manifest+json",
      "pdf" to "application/pdf",
      "zip" to "application/zip"
    )
  }
}
