package eu.kanade.tachiyomi.util.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ThemesTest {

    @Test
    fun `monet has no tinted controls overlay because it uses dynamic colors`() {
        assertNull(Themes.MONET.tintedControlsOverlay())
    }

    @Test
    fun `every other theme has a tinted controls overlay`() {
        Themes.entries.filter { it != Themes.MONET }.forEach {
            assertNotNull(it.tintedControlsOverlay(), "${it.name} is missing an overlay")
        }
    }

    @Test
    fun `default and doki share the base overlay`() {
        assertEquals(Themes.DEFAULT.tintedControlsOverlay(), Themes.DOKI.tintedControlsOverlay())
    }

    @Test
    fun `every theme with its own palette gets its own overlay`() {
        val own = Themes.entries.filter { it != Themes.MONET && it != Themes.DOKI }
        val overlays = own.map { it.tintedControlsOverlay() }

        assertEquals(own.size, overlays.toSet().size)
    }
}
