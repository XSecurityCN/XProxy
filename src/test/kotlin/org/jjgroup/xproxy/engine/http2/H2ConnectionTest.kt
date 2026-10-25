package org.jjgroup.xproxy.engine.http2

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class H2ConnectionTest {

    @Test
    fun `authority is derived from host case insensitively`() {
        for (name in listOf("host", "Host", "HOST", "hOsT")) {
            val headers = H2Connection.buildReqFromRaw(
                "GET / HTTP/2\r\n$name: sqt.gtimg.cn\r\n\r\n",
                false
            )

            assertEquals(
                "sqt.gtimg.cn",
                headers.single { it.first == ":authority" }.second,
                name
            )
            assertFalse(headers.any { it.first == "host" }, name)
        }
    }

    @Test
    fun `blank explicit authority falls back to host`() {
        val headers = H2Connection.buildReqFromRaw(
            "GET / HTTP/2\r\n:authority: \r\nhost: sqt.gtimg.cn\r\n\r\n",
            false
        )

        assertEquals("sqt.gtimg.cn", headers.single { it.first == ":authority" }.second)
        assertFalse(headers.any { it.first == "host" })
    }
}
