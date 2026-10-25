package org.jjgroup.xproxy.ui.http

import org.jjgroup.xproxy.i18n.I18n
import org.jjgroup.xproxy.i18n.I18nLocaleOption
import org.jjgroup.xproxy.kits.core.XappContextMenuHub
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

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
            areas.forEach { it.putClientProperty("xproxy.large-viewer-mode", true) }

            viewer.responseAutoWrapEnabled = true
            viewer.applyResponseAutoWrap()
            assertTrue(areas.all { it.lineWrap })
            assertTrue(areas.all { !it.highlightCurrentLine })

            viewer.responseAutoWrapEnabled = false
            viewer.applyResponseAutoWrap()
            assertFalse(areas.any { it.lineWrap })
        } finally {
            viewer.shutdownRenderers()
        }
    }
}
