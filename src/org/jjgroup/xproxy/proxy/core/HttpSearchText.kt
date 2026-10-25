package org.jjgroup.xproxy.proxy.core

import org.jjgroup.xproxy.fuzzer.request.decodeChunkedBody
import org.jjgroup.xproxy.fuzzer.request.decodeResponseBody
import org.jjgroup.xproxy.settings.core.CharsetPolicy

private val CONTENT_ENCODING_HEADER = Regex("(?im)^content-encoding\\s*:")

/** 将历史记录中按 ISO-8859-1 保存的正文字节还原为可搜索文本。 */
internal fun decodeHttpBodyForSearch(headersText: String, bodyIso88591: String, response: Boolean): String =
    runCatching {
        val contentDecoded = when {
            !response -> bodyIso88591
            CONTENT_ENCODING_HEADER.containsMatchIn(headersText) -> decodeResponseBody(headersText, bodyIso88591)
            headersText.contains("transfer-encoding: chunked", ignoreCase = true) -> decodeChunkedBody(bodyIso88591)
            // 不对普通正文逐个尝试压缩格式探测,避免全表搜索时引入额外解压开销。
            else -> bodyIso88591
        }
        CharsetPolicy.decodeBodyForDisplay(headersText, contentDecoded)
    }.getOrDefault(bodyIso88591)
