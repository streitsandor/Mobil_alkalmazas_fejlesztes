package hu.omegacode.meetingnotes

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

class ApiException(val code: Int, override val message: String) : Exception(message)

data class ApiResponse(val status: Int, val body: String) {
    fun obj(): JSONObject = JSONObject(body)
    fun arr(): JSONArray = JSONArray(body)
}

/**
 * Native Android REST client; DOES NOT know any OmegaAI or X-Api-Key secrets.
 * Only ASP.NET Identity cookie + X-CSRF-TOKEN are used.
 * Cookies remain in process memory; email/password are never stored on disk.
 */
class ApiClient(baseUrl: String) {
    private var base = ServerAddress.normalize(baseUrl)
    private var csrf: String? = null
    private var cookies = CookieManager(null, CookiePolicy.ACCEPT_ALL)

    fun configure(newBase: String) {
        val normalized = ServerAddress.normalize(newBase)
        if (normalized != base) {
            base = normalized
            clearSession()
        }
    }
    fun clearCsrf() { csrf = null }
    fun clearSession() {
        csrf = null
        cookies = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    }
    fun get(path: String) = send("GET", path)
    fun delete(path: String) = send("DELETE", path)
    fun post(path: String) = send("POST", path)
    fun postJson(path: String, json: JSONObject) = send("POST", path, json.toString().toByteArray(StandardCharsets.UTF_8), "application/json; charset=utf-8")
    fun putJson(path: String, json: JSONObject) = send("PUT", path, json.toString().toByteArray(StandardCharsets.UTF_8), "application/json; charset=utf-8")
    fun getText(path: String) = get(path).body

    private fun uri(path: String): URI = URI(base + "/api" + path)

    private fun connection(path: String, method: String): HttpURLConnection {
        require(path.startsWith("/") && !path.startsWith("//") && !path.contains("..")) { "Érvénytelen útvonal" }
        val address = uri(path)
        val connection = (URL(address.toString()).openConnection() as HttpURLConnection)
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 120000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json, text/plain")
        connection.setRequestProperty("Cache-Control", "no-store")
        val cookieHeader = cookies.get(address, emptyMap())["Cookie"]?.joinToString("; ")
        if (!cookieHeader.isNullOrEmpty()) connection.setRequestProperty("Cookie", cookieHeader)
        return connection
    }

    private fun process(path: String, connection: HttpURLConnection): ApiResponse {
        try {
            val code = connection.responseCode
            val address = uri(path)
            cookies.put(address, connection.headerFields)
            val input = if (code >= 400) connection.errorStream else connection.inputStream
            val text = input?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val message = try {
                    val json = JSONObject(text)
                    json.optString("error").ifBlank { json.optString("detail") }.ifBlank { json.optString("message") }.ifBlank { "HTTP $code" }
                } catch (_: Exception) { if (text.isBlank()) "HTTP $code" else text.take(350) }
                throw ApiException(code, message)
            }
            return ApiResponse(code, text)
        } finally { connection.disconnect() }
    }

    private fun csrfIfNeeded(method: String) {
        if (method == "GET" || method == "HEAD" || csrf != null) return
        csrf = get("/auth/csrf").obj().getString("csrfToken")
    }

    private fun send(method: String, path: String, body: ByteArray? = null, contentType: String? = null): ApiResponse {
        csrfIfNeeded(method)
        val connection = connection(path, method)
        if (method != "GET" && method != "HEAD") {
            connection.setRequestProperty("X-CSRF-TOKEN", csrf ?: "")
            if (body == null && method == "POST") {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(0)
                connection.outputStream.close()
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType ?: "application/octet-stream")
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
        }
        return process(path, connection)
    }

    fun upload(path: String, filename: String, mime: String, fields: Map<String, String>, opener: () -> InputStream): ApiResponse {
        csrfIfNeeded("POST")
        val boundary = "----GDEAndroid" + UUID.randomUUID().toString().replace("-", "")
        val connection = connection(path, "POST")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.setRequestProperty("X-CSRF-TOKEN", csrf ?: "")
        connection.setChunkedStreamingMode(65536)
        val cleanName = filename.replace(Regex("[\\r\\n\"]"), "_")
        connection.outputStream.use { out ->
            for ((name, value) in fields) {
                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray(StandardCharsets.UTF_8))
            }
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"audio\"; filename=\"$cleanName\"\r\nContent-Type: $mime\r\n\r\n".toByteArray(StandardCharsets.UTF_8))
            opener().use { input -> input.copyTo(out, 65536) }
            out.write("\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
        }
        return process(path, connection)
    }
}
