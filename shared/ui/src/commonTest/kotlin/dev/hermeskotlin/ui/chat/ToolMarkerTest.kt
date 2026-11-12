package dev.hermeskotlin.ui.chat

import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Wrench
import com.composables.icons.lucide.Zap
import kotlin.test.Test
import kotlin.test.assertEquals

class ToolMarkerTest {

    @Test
    fun theGenericToolIconIsABoltOnlyInOlympus() {
        assertEquals(Lucide.Zap, toolMarker(Lucide.Wrench, olympus = true))
        assertEquals(Lucide.Wrench, toolMarker(Lucide.Wrench, olympus = false))
    }

    @Test
    fun aToolKindsOwnIconStaysInOlympus() {
        assertEquals(Lucide.Globe, toolMarker(toolIcon("web_search"), olympus = true))
        assertEquals(Lucide.Zap, toolMarker(toolIcon("some_plugin_tool"), olympus = true))
    }
}
