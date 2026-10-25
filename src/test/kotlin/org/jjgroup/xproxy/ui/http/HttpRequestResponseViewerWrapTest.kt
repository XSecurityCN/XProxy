package org.jjgroup.xproxy.ui.http

import org.jjgroup.xproxy.i18n.I18n
import org.jjgroup.xproxy.i18n.I18nLocaleOption
import org.jjgroup.xproxy.kits.core.XappContextMenuHub
import org.jjgroup.xproxy.fuzzer.model.BodyKind
import org.jjgroup.xproxy.fuzzer.request.formatBody
import org.jjgroup.xproxy.fuzzer.request.splitMessage
import org.jjgroup.xproxy.ui.highlight.HttpHighlighter
import org.fife.ui.rsyntaxtextarea.TokenTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JViewport
import javax.swing.SwingUtilities

class HttpRequestResponseViewerWrapTest {
    @TempDir
    lateinit var temp: Path

    @BeforeEach
    fun setUp() {
        XappContextMenuHub.clearAllForTests()
        I18n.configureForTests(temp.resolve("bundled"), temp.resolve("user"), persistedLanguage = I18nLocaleOption.SYSTEM.key)
        I18n.registerSettings()
        I18n.syncUserBundles()
        I18n.setLocaleOption(I18nLocaleOption.EN)
    }

    @Test
    fun `large viewer mode preserves response auto wrap setting`() {
        val viewer = HttpRequestResponseViewer()
        try {
            val areas = listOf(viewer.responsePrettyArea, viewer.responseRawArea, viewer.responseRenderArea)
            val rawViewport = viewer.responseRawArea.parent as JViewport
            rawViewport.scrollMode = JViewport.BACKINGSTORE_SCROLL_MODE
            areas.forEach { it.putClientProperty("xproxy.large-viewer-mode", true) }

            viewer.responseAutoWrapEnabled = true
            viewer.applyResponseAutoWrap()
            assertTrue(areas.all { it.lineWrap })
            assertTrue(areas.all { !it.highlightCurrentLine })
            assertTrue(areas.all { it.syntaxEditingStyle == HttpHighlighter.HEADERS_ONLY_STYLE })
            assertEquals(JViewport.SIMPLE_SCROLL_MODE, rawViewport.scrollMode)

            viewer.responseAutoWrapEnabled = false
            viewer.applyResponseAutoWrap()
            assertFalse(areas.any { it.lineWrap })

            areas.forEach { it.putClientProperty("xproxy.large-viewer-mode", false) }
            viewer.applyResponseAutoWrap()
            assertEquals(JViewport.BACKINGSTORE_SCROLL_MODE, rawViewport.scrollMode)
        } finally {
            viewer.shutdownRenderers()
        }
    }

    @Test
    fun `deferred pretty installs complete content only once`() {
        val viewer = HttpRequestResponseViewer()
        try {
            val body = buildString {
                append("{\"items\":[")
                repeat(12_000) { index ->
                    if (index > 0) append(',')
                    append("{\"id\":").append(index).append(",\"name\":\"abcdef\",\"enabled\":true}")
                }
                append("]}")
            }
            val raw = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n$body"
            SwingUtilities.invokeAndWait { viewer.showResponse(raw) }
            awaitCondition { !viewer.responseRenderer.renderInProgress }

            SwingUtilities.invokeAndWait {
                viewer.responseTabs.selectedIndex = 0
                // Tab 切换已触发一次,再次调用不能重复格式化或替换文档。
                viewer.responseRenderer.materializeDeferredPrettyResponseIfNeeded()
                assertTrue(viewer.responseRenderer.prettyMaterializationInProgress)
            }
            awaitCondition {
                !viewer.responseRenderer.prettyMaterializationInProgress &&
                    viewer.responseRenderer.deferredPrettyResponse == null &&
                    viewer.responseRenderer.deferredPrettyRawResponse == null
            }

            val parsed = splitMessage(raw)
            val expected = parsed.headers + parsed.separator + parsed.separator +
                formatBody(body, BodyKind.JSON, parsed.separator)
            assertTrue(expected.length > 128 * 1024)
            var actual = ""
            SwingUtilities.invokeAndWait {
                actual = viewer.responsePrettyArea.text
                assertTrue(viewer.responsePrettyArea.isVisible)
                assertEquals(HttpHighlighter.HEADERS_ONLY_STYLE, viewer.responsePrettyArea.syntaxEditingStyle)
                assertEquals(TokenTypes.RESERVED_WORD, viewer.responsePrettyArea.getTokenListForLine(1).type)
                assertEquals(TokenTypes.IDENTIFIER, viewer.responsePrettyArea.getTokenListForLine(3).type)
            }
            assertEquals(expected, actual)
        } finally {
            viewer.shutdownRenderers()
        }
    }

    @Test
    fun `deferred pretty folds long lines without dropping content`() {
        val viewer = HttpRequestResponseViewer()
        try {
            val body = "{\"data\":\"" + "x".repeat(256 * 1024) + "\"}"
            val raw = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n$body"
            SwingUtilities.invokeAndWait { viewer.showResponse(raw) }
            awaitCondition { !viewer.responseRenderer.renderInProgress }

            SwingUtilities.invokeAndWait {
                viewer.responseTabs.selectedIndex = 0
                viewer.responseRenderer.materializeDeferredPrettyResponseIfNeeded()
            }
            awaitCondition {
                !viewer.responseRenderer.prettyMaterializationInProgress &&
                    viewer.responseRenderer.deferredPrettyResponse == null &&
                    viewer.responseRenderer.deferredPrettyRawResponse == null
            }

            val parsed = splitMessage(raw)
            val unfolded = parsed.headers + parsed.separator + parsed.separator +
                formatBody(body, BodyKind.JSON, parsed.separator)
            val expected = foldLongLinesForViewer(unfolded)
            SwingUtilities.invokeAndWait {
                assertEquals(expected, viewer.responsePrettyArea.text)
                assertTrue(bodyDisplayStats(viewer.responsePrettyArea.text).maxLineLength <= 2048)
                assertTrue(viewer.responsePrettyArea.lineWrap)
            }
        } finally {
            viewer.shutdownRenderers()
        }
    }

    @Test
    fun `new response cancels prepared pretty document from old generation`() {
        val viewer = HttpRequestResponseViewer()
        try {
            val body = "{\"data\":\"" + "x".repeat(4 * 1024 * 1024 + 256 * 1024) + "\"}"
            val raw = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n$body"
            SwingUtilities.invokeAndWait { viewer.showResponse(raw) }
            awaitCondition { !viewer.responseRenderer.renderInProgress }

            val workerFinished = CountDownLatch(1)
            SwingUtilities.invokeAndWait {
                viewer.responseTabs.selectedIndex = 0
                viewer.responseRenderer.renderExecutor.execute { workerFinished.countDown() }
                // 阻塞 EDT 直到后台文档已预建并排入 EDT,确保测试覆盖旧 generation 的完成回调。
                assertTrue(workerFinished.await(15, TimeUnit.SECONDS))
                viewer.showResponse("")
            }
            SwingUtilities.invokeAndWait { }

            SwingUtilities.invokeAndWait {
                assertEquals("", viewer.responsePrettyArea.text)
                assertFalse(viewer.responseRenderer.prettyMaterializationInProgress)
                assertTrue(viewer.responsePrettyArea.isVisible)
            }
        } finally {
            viewer.shutdownRenderers()
        }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            var satisfied = false
            SwingUtilities.invokeAndWait { satisfied = condition() }
            if (satisfied) return
            Thread.sleep(10)
        }
        throw AssertionError("Condition was not satisfied before timeout")
    }
}
