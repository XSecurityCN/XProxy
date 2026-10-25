package org.jjgroup.xproxy.fuzzer.request

import org.jjgroup.xproxy.fuzzer.core.HttpService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FuzzerHttp2RequestNormalizationTest {

    @Test
    fun `normalization converts absolute URI to origin-form path`() {
        val request = "GET https://api.apifox.com/v1/test?q=1 HTTP/2\r\nHost: api.apifox.com\r\n\r\n"
        val normalized = normalizeHttp2RequestForEngine(HttpService("api.apifox.com", 443, "https"), request)

        assertTrue(normalized.startsWith("GET /v1/test?q=1 HTTP/2"))
    }

    @Test
    fun `normalization strips hop-by-hop headers for h2`() {
        val request = (
            "GET / HTTP/2\r\n" +
                "Host: api.apifox.com\r\n" +
                "Connection: keep-alive\r\n" +
                "Proxy-Connection: keep-alive\r\n" +
                "Upgrade: h2c\r\n\r\n"
            )
        val normalized = normalizeHttp2RequestForEngine(HttpService("api.apifox.com", 443, "https"), request)

        assertFalse(normalized.contains("\r\nConnection:", ignoreCase = true))
        assertFalse(normalized.contains("\r\nProxy-Connection:", ignoreCase = true))
        assertFalse(normalized.contains("\r\nUpgrade:", ignoreCase = true))
    }

    @Test
    fun `traffic 1230 h2 get request is preserved byte for byte`() {
        val request = (
            "GET /qt?app=tengxuncaijing&q=s_sh000001,s_sz399001,s_sz399006,s_sh000680,s_sh000300,s_hkHSI,s_usDJI,s_usIXIC,s_usINX HTTP/2\r\n" +
                "host: sqt.gtimg.cn\r\n" +
                "sec-ch-ua-platform: \"macOS\"\r\n" +
                "user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36\r\n" +
                "accept: application/json, text/plain, */*\r\n" +
                "sec-ch-ua: \"Brave\";v=\"155\", \"Chromium\";v=\"155\", \"Not(A:Brand\";v=\"24\"\r\n" +
                "sec-ch-ua-mobile: ?0\r\n" +
                "sec-gpc: 1\r\n" +
                "origin: https://www.qq.com\r\n" +
                "sec-fetch-site: cross-site\r\n" +
                "sec-fetch-mode: cors\r\n" +
                "sec-fetch-dest: empty\r\n" +
                "referer: https://www.qq.com/\r\n" +
                "accept-encoding: gzip, deflate, br, zstd\r\n" +
                "accept-language: zh-CN,zh;q=0.8\r\n" +
                "priority: u=1, i\r\n" +
                "\r\n"
            )

        val normalized = normalizeHttp2RequestForEngine(HttpService("sqt.gtimg.cn", 443, "https"), request)

        assertEquals(request, normalized)
        assertEquals("", splitMessage(normalized).body)
    }

    @Test
    fun `normalization converts line endings without appending to body`() {
        val request = "POST /submit HTTP/2\nHost: api.apifox.com\nContent-Length: 2\n\n{}"

        val normalized = normalizeHttp2RequestForEngine(HttpService("api.apifox.com", 443, "https"), request)

        assertEquals(
            "POST /submit HTTP/2\r\nHost: api.apifox.com\r\nContent-Length: 2\r\n\r\n{}",
            normalized
        )
    }

    @Test
    fun `normalization still terminates header only request`() {
        val normalized = normalizeHttp2RequestForEngine(
            HttpService("api.apifox.com", 443, "https"),
            "GET / HTTP/2\r\nHost: api.apifox.com"
        )

        assertEquals("GET / HTTP/2\r\nHost: api.apifox.com\r\n\r\n", normalized)
    }

    @Test
    fun `blank host falls back to target host`() {
        val normalized = normalizeHttp2RequestForEngine(
            HttpService("api.apifox.com", 443, "https"),
            "GET / HTTP/2\r\nhost:\r\n\r\n"
        )

        assertEquals("GET / HTTP/2\r\nHost: api.apifox.com\r\n\r\n", normalized)
    }
}
