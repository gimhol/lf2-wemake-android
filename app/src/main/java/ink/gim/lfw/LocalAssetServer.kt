package ink.gim.lfw

import android.content.Context
import android.content.res.AssetFileDescriptor
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 本地静态资源 HTTP 服务器：监听 127.0.0.1 上由系统动态分配的空闲端口。
 *
 * 资源查找顺序：热更新目录优先 -> assets/www 兜底。
 * 热更新只需把新资源写入更新目录（或切换版本目录），无需重启服务器。
 *
 * 为什么需要它：
 *  - `shouldInterceptRequest` 返回的 InputStream 有约 16MB 的平台级上限，
 *    100MB 级的资源（zip / wasm / 视频等）经它加载会失败；
 *  - 本地 HTTP 走真实网络栈，无大小限制，且支持 Range 请求与 Content-Length，
 *    音视频可拖动进度，fetch 下载大文件可正常流入 IndexedDB。
 *
 * 进程级单例，引用计数管理生命周期，应用内任意位置可获取同一实例。
 */
class LocalAssetServer private constructor(
  context: Context,
  updateRoot: File?
) {

  private val assetRoot: String = "www"
  private val assets = context.applicationContext.assets

  /** 热更新目录：优先于 assets 提供文件；可运行时切换版本目录。 */
  @Volatile
  private var updateRoot: File? = updateRoot

  private val running = AtomicBoolean(false)
  private val executor: ExecutorService = Executors.newCachedThreadPool { r ->
    Thread(r, "lfw-asset-io").apply { isDaemon = true }
  }
  private val clients = Collections.synchronizedSet(mutableSetOf<Socket>())
  private var serverSocket: ServerSocket? = null

  val isRunning: Boolean get() = running.get()

  /** 实际绑定的端口；尚未绑定返回 -1。 */
  val port: Int get() = serverSocket?.localPort ?: -1

  /** 当前生效的热更新目录；null 表示仅使用 assets。 */
  val currentUpdateRoot: File? get() = updateRoot

  /** 切换热更新资源目录（null 表示仅使用 assets）。 */
  fun setUpdateRoot(dir: File?) {
    updateRoot = dir
  }

  /** 启动服务器（幂等）。阻塞等待端口就绪，最多 3 秒。 */
  fun start() {
    if (running.getAndSet(true)) return
    val ready = CountDownLatch(1)
    thread(isDaemon = true, name = "lfw-asset-server") {
      try {
        val server = bindServer()
        serverSocket = server
        ready.countDown()
        while (running.get()) {
          val client = server.accept()
          clients.add(client)
          executor.execute { handle(client) }
        }
      } catch (e: IOException) {
        // 端口被占用或启动失败：降级，页面回退到 shouldInterceptRequest 拦截
        running.set(false)
        ready.countDown()
      }
    }
    ready.await(3, TimeUnit.SECONDS)
  }

  /** 停止服务器并关闭所有连接。 */
  fun stop() {
    running.set(false)
    runCatching { serverSocket?.close() }
    synchronized(clients) {
      clients.forEach { runCatching { it.close() } }
      clients.clear()
    }
    executor.shutdownNow()
  }

  /**
   * 绑定监听端口：使用 ServerSocket(0) 由系统动态分配空闲端口，保证不冲突。
   */
  private fun bindServer(): ServerSocket =
    ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))

  // ===== 进程级单例管理 =====

  companion object {
    private val lock = Any()
    private var instance: LocalAssetServer? = null
    private var refCount = 0

    /** 获取进程级单例（引用计数 +1）。 */
    fun acquire(context: Context): LocalAssetServer = synchronized(lock) {
      val appCtx = context.applicationContext
      val s = instance ?: LocalAssetServer(
        appCtx,
        defaultUpdateRoot(appCtx)
      ).also { instance = it }
      refCount++
      s
    }

    /** 默认热更新目录：<外部专属存储>/www，供下载解压后的新资源使用。 */
    private fun defaultUpdateRoot(context: Context): File? {
      return runCatching {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        File(base, "www")
      }.getOrNull()
    }

    /** 释放引用（计数归零时停止服务器）。 */
    fun release(server: LocalAssetServer) = synchronized(lock) {
      if (server !== instance) return
      refCount--
      if (refCount <= 0) {
        instance?.stop()
        instance = null
        refCount = 0
      }
    }

    /** 是否有实例正在运行（供拦截器判断走真实网络还是拦截兜底）。 */
    val isAnyRunning: Boolean
      get() = synchronized(lock) { instance?.isRunning == true }

    /** 当前实例实际绑定的端口；无实例或未启动返回 null。 */
    val currentPort: Int?
      get() = synchronized(lock) { instance?.port?.takeIf { it > 0 } }

    /** URL 中的默认端口占位值；加载时会重写为服务器实际分配的端口。 */
    const val DEFAULT_PORT = 3000
  }

  // ===== HTTP 处理 =====

  private fun handle(socket: Socket) {
    try {
      socket.soTimeout = 60_000
      val input = socket.getInputStream()
      val output = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)

      val requestLine = readLine(input) ?: return
      val parts = requestLine.split(' ')
      if (parts.size < 2) return
      val method = parts[0]
      val rawPath = parts[1]

      val headers = HashMap<String, String>()
      while (true) {
        val line = readLine(input) ?: break
        if (line.isEmpty()) break
        val idx = line.indexOf(':')
        if (idx > 0) {
          headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
      }

      if (method != "GET" && method != "HEAD") {
        writeStatus(output, 405, "Method Not Allowed")
        return
      }

      val decoded = runCatching { URLDecoder.decode(rawPath.substringBefore('?'), "UTF-8") }
        .getOrDefault(rawPath.substringBefore('?'))
      serveFile(output, decoded, method == "HEAD", headers)
    } catch (e: SocketException) {
      // 客户端断开，忽略
    } catch (e: IOException) {
      // 单个请求错误，不影响服务器
    } finally {
      runCatching { socket.close() }
      clients.remove(socket)
    }
  }

  private fun serveFile(
    output: OutputStream,
    urlPath: String,
    headOnly: Boolean,
    headers: Map<String, String>
  ) {
    val trimmed = urlPath.trimStart('/')
    val relative = if (trimmed.isEmpty()) "index.html" else trimmed
    // 防目录穿越：拒绝包含 ".." 的路径
    if (relative.contains("..")) {
      writeStatus(output, 400, "Bad Request")
      return
    }
    val candidates = buildList {
      add(relative)
      add(if (relative.endsWith("/")) "${relative}index.html" else "$relative/index.html")
    }.distinct()

    var fd: AssetFileDescriptor? = null
    var stream: InputStream? = null

    try {
      // 1) 热更新目录优先：命中即返回更新后的文件
      updateRoot?.let { root ->
        for (candidate in candidates) {
          val file = File(root, candidate)
          if (file.isFile) {
            val total = file.length()
            serveFileSource(
              output = output,
              total = total,
              mime = MimeTypes.guess(candidate),
              headOnly = headOnly,
              range = parseRange(headers["range"], total),
              open = { off -> FileInputStream(file).apply { if (off > 0) skipFully(this, off) } }
            )
            return
          }
        }
      }

      // 2) 回退到 assets/www
      for (candidate in candidates) {
        val assetPath = "$assetRoot/$candidate"
        try {
          // 优先 openFd：未压缩存储的资源（noCompress）支持 seek / Range
          fd = assets.openFd(assetPath)
          val total = fd.length
          val f = fd
          serveFileSource(
            output = output,
            total = total,
            mime = MimeTypes.guess(assetPath),
            headOnly = headOnly,
            range = parseRange(headers["range"], total),
            open = { off -> f.seekTo(off); FileInputStream(f.fileDescriptor) }
          )
          return
        } catch (e: FileNotFoundException) {
          // 尝试下一个候选
        } catch (e: IOException) {
          // 压缩存储的资源不能 openFd，退化为流式读取
          try {
            val s = assets.open(assetPath)
            stream = s
            val total = s.available().toLong()
            serveFileSource(
              output = output,
              total = total,
              mime = MimeTypes.guess(assetPath),
              headOnly = headOnly,
              range = parseRange(headers["range"], total),
              open = { off -> s.apply { skipFully(this, off) } }
            )
            return
          } catch (e2: FileNotFoundException) {
            // 尝试下一个候选
          }
        }
      }

      writeStatus(output, 404, "Not Found")
    } finally {
      fd?.close()
      stream?.close()
    }
  }

  /**
   * 根据 Range 输出 200/206 响应，并从 [open] 打开的流复制内容。
   * [open] 接收起始偏移，返回从该偏移起可读的流。
   */
  private fun serveFileSource(
    output: OutputStream,
    total: Long,
    mime: String,
    headOnly: Boolean,
    range: Pair<Long, Long>?,
    open: (offset: Long) -> InputStream
  ) {
    if (range != null) {
      val (start, end) = range
      val len = end - start + 1
      writeHeaders(output, 206, "Partial Content", mime, len, start, end, total)
      if (!headOnly) {
        open(start).use { copy(it, output, len) }
      }
    } else {
      writeHeaders(output, 200, "OK", mime, total, null, null, total)
      if (!headOnly) {
        open(0).use { copy(it, output, total) }
      }
    }
    output.flush()
  }

  private fun writeHeaders(
    output: OutputStream,
    code: Int,
    reason: String,
    mime: String,
    length: Long,
    start: Long?,
    end: Long?,
    total: Long
  ) {
    val sb = StringBuilder()
    sb.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
    sb.append("Content-Type: ").append(mime).append("\r\n")
    sb.append("Content-Length: ").append(length).append("\r\n")
    sb.append("Accept-Ranges: bytes\r\n")
    if (start != null && end != null) {
      sb.append("Content-Range: bytes ").append(start).append('-').append(end)
        .append('/').append(total).append("\r\n")
    }
    sb.append("Cache-Control: no-store\r\n")
    sb.append("Connection: close\r\n\r\n")
    output.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
  }

  private fun writeStatus(output: OutputStream, code: Int, reason: String) {
    val sb = StringBuilder()
    sb.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
    sb.append("Content-Length: 0\r\n")
    sb.append("Connection: close\r\n\r\n")
    output.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
    output.flush()
  }

  /** 解析 `Range: bytes=start-end / start- / -suffix`，返回 [start, end]；非法或未提供返回 null。 */
  private fun parseRange(header: String?, total: Long): Pair<Long, Long>? {
    if (header == null || total <= 0) return null
    val m = RANGE_PATTERN.find(header) ?: return null
    val startStr = m.groupValues[1]
    val endStr = m.groupValues[2]
    return when {
      startStr.isEmpty() && endStr.isEmpty() -> null
      startStr.isEmpty() -> {
        val suffix = endStr.toLongOrNull() ?: return null
        if (suffix <= 0) return null
        maxOf(0, total - suffix) to (total - 1)
      }
      else -> {
        val start = startStr.toLongOrNull() ?: return null
        if (start >= total) return null
        val end = endStr.toLongOrNull()?.coerceAtMost(total - 1) ?: (total - 1)
        if (end < start) return null
        start to end
      }
    }
  }

  private fun readLine(input: InputStream): String? {
    val sb = StringBuilder()
    var prev = -1
    while (true) {
      val b = input.read()
      if (b == -1) return if (sb.isEmpty()) null else sb.toString()
      if (prev == '\r'.code && b == '\n'.code) {
        sb.setLength(sb.length - 1)
        return sb.toString()
      }
      sb.append(b.toChar())
      prev = b
    }
  }

  private fun skipFully(stream: InputStream, n: Long) {
    var remaining = n
    while (remaining > 0) {
      val skipped = stream.skip(remaining)
      if (skipped <= 0) {
        if (stream.read() == -1) return
        remaining--
      } else {
        remaining -= skipped
      }
    }
  }

  private fun copy(input: InputStream, output: OutputStream, length: Long) {
    val buffer = ByteArray(64 * 1024)
    var remaining = length
    while (remaining > 0) {
      val toRead = minOf(remaining, buffer.size.toLong()).toInt()
      val read = input.read(buffer, 0, toRead)
      if (read < 0) break
      output.write(buffer, 0, read)
      remaining -= read
    }
  }

  private companion object {
    val RANGE_PATTERN = Regex("bytes=(\\d*)-(\\d*)")
  }
}
