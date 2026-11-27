package studio.virel.office

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * api.notion.com 전용 HTTP 클라이언트. 앱 안에서 직접 호출하므로 브라우저 CORS 제약이 없다.
 * 호스트는 고정이고, 경로는 허용 목록(/v1/blocks, /v1/pages, /v1/users)만 통과시킨다.
 */
class NotionClient(
    private val tokenProvider: () -> String?,
    private val baseUrl: String = "https://api.notion.com",
    private val http: OkHttpClient = defaultHttp(),
) {
    data class Result(val status: Int, val body: String)

    fun call(method: String, path: String, jsonBody: String?): Result {
        val m = method.uppercase()
        if (m !in ALLOWED_METHODS) return Result(400, err("invalid_method", "허용되지 않은 메서드: $m"))
        if (!PATH_RE.matches(path)) return Result(400, err("invalid_path", "허용되지 않은 경로: $path"))
        val token = tokenProvider()?.trim().orEmpty()
        if (token.isEmpty()) return Result(401, err("no_token", "노션 토큰이 없어요. 설정에서 입력해 주세요."))

        val body = when (m) {
            "GET", "DELETE" -> null
            else -> (jsonBody ?: "{}").toRequestBody(JSON)
        }
        val req = Request.Builder()
            .url(baseUrl + path)
            .method(m, body)
            .header("Authorization", "Bearer $token")
            .header("Notion-Version", NOTION_VERSION)
            .header("Accept", "application/json")
            .build()
        return try {
            http.newCall(req).execute().use { res ->
                Result(res.code, res.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            Result(0, err("network", e.message ?: "네트워크 오류"))
        }
    }

    companion object {
        const val NOTION_VERSION = "2022-06-28"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val ALLOWED_METHODS = setOf("GET", "POST", "PATCH", "DELETE")
        private val PATH_RE = Regex("^/v1/(blocks|pages|users)(/[A-Za-z0-9-]+)*(/children)?(\\?[A-Za-z0-9_=&%.-]*)?$")

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .build()

        private fun err(code: String, message: String): String =
            "{\"object\":\"error\",\"code\":${quote(code)},\"message\":${quote(message)}}"

        fun quote(s: String): String {
            val sb = StringBuilder("\"")
            for (c in s) {
                when (c) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    ' ' -> sb.append("\\u2028")
                    ' ' -> sb.append("\\u2029")
                    else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
                }
            }
            return sb.append('"').toString()
        }
    }
}
