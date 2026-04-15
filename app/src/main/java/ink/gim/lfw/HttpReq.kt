package ink.gim.lfw

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class HttpReq(val url: String) {
  fun text(): Pair<String, HttpURLConnection> {
    val url = URL(this.url)
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    conn.instanceFollowRedirects = true
    conn.connect()
    // 读取结果
    val reader = BufferedReader(InputStreamReader(conn.inputStream))
    val response = reader.readText()
    reader.close()
    conn.disconnect()
    return response to conn
  }

  fun json(): Pair<JSONObject, HttpURLConnection> {
    val (str, conn) = this.text()
    return JSONObject(str) to conn
  }
}