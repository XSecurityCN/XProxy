package org.jjgroup.xproxy.core

import org.jjgroup.xproxy.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RequestTest {

    @Test
    fun `content length correction is case insensitive and preserves body`() {
        val request = Request("POST / HTTP/2\r\ncontent-length: 1\r\n\r\n{}")

        assertEquals(
            "POST / HTTP/2\r\ncontent-length: 2\r\n\r\n{}",
            request.getRequest()
        )
    }
}
