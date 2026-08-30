package ink.gim.lfw

import java.util.Locale

/**
 * 按文件扩展名推断 MIME 类型，供本地资源服务（拦截器 / 本地 HTTP 服务器）复用。
 */
object MimeTypes {
  fun guess(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return MAP[ext] ?: "application/octet-stream"
  }

  private val MAP = mapOf(
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
