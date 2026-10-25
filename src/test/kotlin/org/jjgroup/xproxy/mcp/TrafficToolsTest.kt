package org.jjgroup.xproxy.mcp

import org.jjgroup.xproxy.mcp.attack.McpAttackRunner
import org.jjgroup.xproxy.mcp.server.McpJson
import org.jjgroup.xproxy.mcp.tools.McpToolContext
import org.jjgroup.xproxy.mcp.tools.trafficTools
import org.jjgroup.xproxy.project.core.ProjectDataStore
import org.jjgroup.xproxy.project.core.ProjectRecord
import org.jjgroup.xproxy.proxy.model.ProxyHistoryEntry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class TrafficToolsTest {
    private val ctx = McpToolContext(McpAttackRunner())

    @BeforeEach
    fun resetAppContext() = XproxyAppContext.clear()

    @AfterEach
    fun clearAppContext() = XproxyAppContext.clear()

    @Test
    fun `search_proxy_history matches Chinese in a UTF-8 response body stored as wire bytes`() {
        val store = newStore()
        XproxyAppContext.bind(null, store, null, null, null, null)
        val body = String("{\"message\":\"登录成功\"}".toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)
        store.saveHistory(
            ProxyHistoryEntry(
                id = 1L, timeMillis = 1L, method = "GET", host = "example.com", path = "/api",
                statusCode = 200, length = body.length, mimeType = "json", title = "", tls = true, modified = false,
                requestRaw = "GET /api HTTP/1.1\r\nHost: example.com\r\n\r\n",
                responseRaw = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$body"
            )
        )
        val tool = trafficTools().first { it.name == "search_proxy_history" }

        val result = tool.invoke(McpJson.obj().put("keyword", "登录"), ctx)

        assertFalse(result.isError)
        val node = McpJson.parseOrNull(result.content.first().text)!!
        assertEquals(1, node.get("total").asInt())
        assertEquals(1L, node.get("entries").first().get("id").asLong())
    }

    private fun newStore(): ProjectDataStore {
        val dbPath = Files.createTempFile("xproxy-traffic-tools", ".db")
        val record = ProjectRecord(
            id = "test",
            displayName = "Test",
            baseName = "test",
            createdDate = "2026-10-25",
            projectDir = dbPath.parent.toString(),
            dbPath = dbPath.toAbsolutePath().toString(),
            createdAtMillis = 0L,
            lastOpenedMillis = 0L
        )
        return ProjectDataStore(record)
    }
}
