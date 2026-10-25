package org.jjgroup.xproxy.fuzzer.request

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.HexFormat

class FuzzerHttp2ResponseFormattingTest {

    @Test
    fun `formats pseudo status to http2 status line`() {
        val raw = ":status: 200\r\ncontent-type: application/json\r\n\r\n{\"ok\":true}"
        val formatted = formatHttp2ResponseForDisplay(raw)

        assertTrue(formatted.startsWith("HTTP/2 200 OK\r\n"))
        assertFalse(formatted.contains(":status:"))
        assertTrue(formatted.contains("\r\n\r\n{\"ok\":true}"))
    }

    @Test
    fun `keeps response unchanged when no pseudo status exists`() {
        val raw = "HTTP/1.1 200 OK\r\ncontent-type: text/plain\r\n\r\nhello"
        val formatted = formatHttp2ResponseForDisplay(raw)
        assertTrue(formatted == raw)
    }

    @Test
    fun `formats headers without changing brotli body bytes`() {
        val plainBody = "The quick brown fox jumps over the lazy dog. The quick brown fox jumps over the lazy dog."
        val compressed = HexFormat.of().parseHex(
            "1b5800882c0e78d3d0955d9710bb172ba9cad092cc8cad415ce6f236c8199e9e0a7b830d387048206f21bf41a715ce1c1e27aa2938c2a57d1a63"
        )
        val encodedBody = String(compressed, Charsets.ISO_8859_1)
        val raw = ":status: 200\r\ncontent-type: text/plain\r\ncontent-encoding: br\r\n\r\n$encodedBody"

        val formatted = formatHttp2ResponseForDisplay(raw)
        val parsed = splitMessage(formatted)

        assertTrue(encodedBody.contains('\n'))
        assertEquals(encodedBody, parsed.body)
        assertEquals(plainBody, decodeResponseBody(parsed.headers, parsed.body))
    }
}
