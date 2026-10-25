package org.jjgroup.xproxy.ui.table

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.GZIPOutputStream

/**
 * 锁定 [MimeFilterState] 的默认选中语义:默认选中的 MIME 是 [MimeFilterState.defaultTypes]
 * (text/json/xml/html/sse,5 个),而非 [MimeFilterState.types] 全部(10 个)。
 *
 * 此前"恢复默认"按钮误用 types()(全部)而非 defaultTypes(),导致点击后变成"显示全部"而非真正的默认。
 * 本测试固化默认集合,防止再次混淆。
 */
class MimeFilterStateTest {

    @Test
    fun `default selected types are the defaultTypes subset, not all types`() {
        val state = MimeFilterState()
        val expectedDefault = setOf("text", "json", "xml", "html", "sse")
        assertEquals(expectedDefault, state.defaultTypes())
        assertEquals(state.defaultTypes(), state.selectedTypes())
        // 默认是 allTypes 的真子集(script/css/image/bin/other 默认未选)。
        assertTrue(state.types().containsAll(state.defaultTypes()))
        assertFalse(state.selectedTypes().containsAll(state.types()))
    }

    @Test
    fun `fresh state has default mime selection, all status buckets, no keyword, scopes on, AND`() {
        val state = MimeFilterState()
        assertEquals(state.defaultTypes(), state.selectedTypes())
        assertEquals(setOf("1xx", "2xx", "3xx", "4xx", "5xx"), state.selectedStatusBuckets().toSet())
        assertEquals("", state.keyword())
        assertFalse(state.keywordRegex())
        assertFalse(state.keywordCaseSensitive())
        assertTrue(state.keywordScopeRequestHeader())
        assertTrue(state.keywordScopeRequestBody())
        assertTrue(state.keywordScopeResponseHeader())
        assertTrue(state.keywordScopeResponseBody())
        assertEquals(MimeFilterState.LogicMode.AND, state.logicMode())
    }

    @Test
    fun `resetDefault restores the defaultTypes selection`() {
        val state = MimeFilterState()
        state.showAll() // 选中全部(含 script/css/image/bin/other)
        assertEquals(state.types().toSet(), state.selectedTypes())
        state.resetDefault()
        assertEquals(state.defaultTypes(), state.selectedTypes())
    }

    @Test
    fun `showAll selects every type, distinct from default`() {
        val state = MimeFilterState()
        state.showAll()
        assertEquals(state.types().toSet(), state.selectedTypes())
        // showAll 与默认不同:默认不含 script/css/image/bin/other。
        assertFalse(state.defaultTypes().containsAll(state.types()))
    }

    @Test
    fun `keyword still matches English in a raw response body`() {
        val response = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{\"status\":\"Success\"}"
        val state = MimeFilterState()
        state.setKeyword("success")

        assertTrue(state.matchesHttp("json", 200, "", response))
    }

    @Test
    fun `keyword matches Chinese in an already decoded external response`() {
        val response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n你好"
        val state = MimeFilterState()
        state.setKeyword("你好")

        assertTrue(state.matchesHttp("text", 200, "", response))
    }

    @Test
    fun `keyword matches Chinese in a UTF-8 response body stored as wire bytes`() {
        val body = wireText("{\"message\":\"登录成功\"}", Charsets.UTF_8)
        val response = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$body"
        val state = MimeFilterState()
        state.setKeyword("登录")

        assertTrue(state.matchesHttp("json", 200, "", response))
    }

    @Test
    fun `keyword matches Chinese in a GBK request body stored as wire bytes`() {
        val body = wireText("username=管理员", Charset.forName("GBK"))
        val request = "POST /login HTTP/1.1\r\nContent-Type: application/x-www-form-urlencoded; charset=GBK\r\n\r\n$body"
        val state = MimeFilterState()
        state.setKeyword("管理员")

        assertTrue(state.matchesHttp("text", 200, request, ""))
    }

    @Test
    fun `Chinese keyword regex uses decoded body and honors selected scope`() {
        val body = wireText("登录成功", Charsets.UTF_8)
        val request = "POST /login HTTP/1.1\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n$body"
        val state = MimeFilterState()
        state.setKeyword("登录.*成功")
        state.setKeywordRegex(true)
        state.setKeywordScopeRequestBody(false)
        state.setKeywordScopeRequestHeader(false)
        state.setKeywordScopeResponseHeader(false)
        state.setKeywordScopeResponseBody(false)
        assertFalse(state.matchesHttp("text", 200, request, ""))

        state.setKeywordScopeRequestBody(true)
        assertTrue(state.matchesHttp("text", 200, request, ""))
    }

    @Test
    fun `keyword matches Chinese in a gzip UTF-8 response body`() {
        val body = gzip("登录成功")
        val response = "HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Encoding: gzip\r\n\r\n$body"
        val state = MimeFilterState()
        state.setKeyword("登录")

        assertTrue(state.matchesHttp("text", 200, "", response))
    }

    @Test
    fun `Chinese WebSocket text payload is matched without wire decoding`() {
        val state = MimeFilterState()
        state.setKeyword("你好")

        assertTrue(state.matchesWs("text", "{\"message\":\"你好\"}"))
    }

    private fun wireText(text: String, charset: Charset): String =
        String(text.toByteArray(charset), Charsets.ISO_8859_1)

    private fun gzip(text: String): String {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return String(output.toByteArray(), Charsets.ISO_8859_1)
    }
}
