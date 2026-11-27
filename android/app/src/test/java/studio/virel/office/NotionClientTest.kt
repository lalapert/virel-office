package studio.virel.office

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class NotionClientTest {

    private val hub = "4256c803-31b7-81c6-991c-e92ec8b3e15a"

    @Test
    fun rejectsPathsOutsideAllowList() {
        val c = NotionClient({ "dummy" })
        assertEquals(400, c.call("GET", "/v1/search", null).status)
        assertEquals(400, c.call("GET", "/v1/blocks/../../etc", null).status)
        assertEquals(400, c.call("PUT", "/v1/blocks/$hub/children", null).status)
    }

    @Test
    fun missingTokenIs401WithoutNetwork() {
        val c = NotionClient({ null })
        val r = c.call("GET", "/v1/blocks/$hub/children", null)
        assertEquals(401, r.status)
        assertEquals("no_token", JSONObject(r.body).getString("code"))
    }

    @Test
    fun quoteEscapesForJavascript() {
        assertEquals("\"a\\\"b\\\\c\\nd\\u2028\"", NotionClient.quote("a\"b\\c\nd "))
    }

    /**
     * 실제 노션 API 읽기 테스트(쓰기 없음). -Dnotion.live=1 일 때만 돈다.
     * 토큰은 NOTION_TOKEN 환경변수에서 읽는다(프록시가 헤더를 주입하는 환경이면 아무 값이나 넣어도 됨).
     */
    @Test
    fun liveListsHubDatePages() {
        assumeTrue(System.getProperty("notion.live") == "1")
        val c = NotionClient({ System.getenv("NOTION_TOKEN") ?: "proxy-injected" })
        val r = c.call("GET", "/v1/blocks/$hub/children?page_size=100", null)
        assertEquals(r.body, 200, r.status)
        val results = JSONObject(r.body).getJSONArray("results")
        val titles = (0 until results.length())
            .map { results.getJSONObject(it) }
            .filter { it.getString("type") == "child_page" }
            .map { it.getJSONObject("child_page").getString("title") }
        println("hub date pages: $titles")
        assertTrue(titles.any { Regex("^\\d{4}-\\d{2}-\\d{2}\\s*회의실").containsMatchIn(it) })
    }
}
