package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.view.View
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PagerPageTransitionTest {

    private class PageState {
        var alpha = 1f
        var scaleX = 1f
        var scaleY = 1f
        var translationX = 0f
        var translationY = 0f
    }

    private fun mockPage(state: PageState, width: Int = 1000, height: Int = 1500): View {
        val view = mockk<View>(relaxed = true)
        every { view.width } returns width
        every { view.height } returns height
        every { view.setAlpha(any()) } answers { state.alpha = firstArg() }
        every { view.setScaleX(any()) } answers { state.scaleX = firstArg() }
        every { view.setScaleY(any()) } answers { state.scaleY = firstArg() }
        every { view.setTranslationX(any()) } answers { state.translationX = firstArg() }
        every { view.setTranslationY(any()) } answers { state.translationY = firstArg() }
        return view
    }

    private fun transform(
        effect: Int,
        position: Float,
        isHorizontal: Boolean = true,
        leavingIsNegative: Boolean = true,
    ): PageState {
        val transformer = PagerPageTransition.createTransformer(effect, isHorizontal, leavingIsNegative)!!
        val state = PageState()
        transformer.transformPage(mockPage(state), position)
        return state
    }

    private fun assertState(
        state: PageState,
        alpha: Float = 1f,
        scale: Float = 1f,
        translationX: Float = 0f,
        translationY: Float = 0f,
    ) {
        assertEquals(alpha, state.alpha, DELTA, "alpha")
        assertEquals(scale, state.scaleX, DELTA, "scaleX")
        assertEquals(scale, state.scaleY, DELTA, "scaleY")
        assertEquals(translationX, state.translationX, DELTA, "translationX")
        assertEquals(translationY, state.translationY, DELTA, "translationY")
    }

    @Test
    fun `slide has no transformer so the pager stays untouched`() {
        assertNull(PagerPageTransition.createTransformer(PagerPageTransition.EFFECT_SLIDE, true, true))
    }

    @Test
    fun `unknown effect falls back to the default slide`() {
        assertNull(PagerPageTransition.createTransformer(99, true, true))
        assertNull(PagerPageTransition.createTransformer(-1, true, true))
    }

    @Test
    fun `every other effect has a transformer`() {
        listOf(
            PagerPageTransition.EFFECT_FADE,
            PagerPageTransition.EFFECT_DEPTH,
            PagerPageTransition.EFFECT_ZOOM_OUT,
        ).forEach {
            assertNotNull(PagerPageTransition.createTransformer(it, true, true))
        }
    }

    @Test
    fun `default speed keeps the pager duration`() {
        assertNull(PagerPageTransition.durationFor(0))
    }

    @Test
    fun `speeds map to increasing durations`() {
        val durations = (1..5).map { PagerPageTransition.durationFor(it) }

        assertEquals(listOf(1, 150, 300, 600, 1000), durations)
    }

    @Test
    fun `unknown speed keeps the pager duration`() {
        assertNull(PagerPageTransition.durationFor(6))
        assertNull(PagerPageTransition.durationFor(-1))
    }

    @Test
    fun `fade keeps the current page untouched`() {
        assertState(transform(PagerPageTransition.EFFECT_FADE, 0f))
    }

    @Test
    fun `fade crossfades pages in place while sliding`() {
        assertState(
            transform(PagerPageTransition.EFFECT_FADE, -0.25f),
            alpha = 0.75f,
            translationX = 250f,
        )
        assertState(
            transform(PagerPageTransition.EFFECT_FADE, 0.5f),
            alpha = 0.5f,
            translationX = -500f,
        )
    }

    @Test
    fun `fade uses the vertical axis and height for a vertical pager`() {
        assertState(
            transform(PagerPageTransition.EFFECT_FADE, 0.5f, isHorizontal = false),
            alpha = 0.5f,
            translationY = -750f,
        )
    }

    @Test
    fun `fade leaves neighbours off screen instead of stacking them on the current page`() {
        listOf(-2f, -1f, -0.996f, 0.996f, 1f, 2f).forEach {
            assertState(transform(PagerPageTransition.EFFECT_FADE, it), alpha = 0f)
        }
    }

    @Test
    fun `depth keeps the leaving page on the negative side untouched`() {
        assertState(transform(PagerPageTransition.EFFECT_DEPTH, -0.5f))
        assertState(transform(PagerPageTransition.EFFECT_DEPTH, 0f))
    }

    @Test
    fun `depth fades and shrinks the entering page on the positive side`() {
        assertState(
            transform(PagerPageTransition.EFFECT_DEPTH, 0.5f),
            alpha = 0.5f,
            scale = 0.875f,
            translationX = -500f,
        )
    }

    @Test
    fun `depth mirrors the sides on a right to left pager`() {
        assertState(transform(PagerPageTransition.EFFECT_DEPTH, 0.5f, leavingIsNegative = false))
        assertState(
            transform(PagerPageTransition.EFFECT_DEPTH, -0.5f, leavingIsNegative = false),
            alpha = 0.5f,
            scale = 0.875f,
            translationX = 500f,
        )
    }

    @Test
    fun `depth uses the vertical axis for a vertical pager`() {
        assertState(
            transform(PagerPageTransition.EFFECT_DEPTH, 0.5f, isHorizontal = false),
            alpha = 0.5f,
            scale = 0.875f,
            translationY = -750f,
        )
    }

    @Test
    fun `depth leaves neighbours off screen instead of stacking them on the current page`() {
        listOf(-2f, -1f, 0.996f, 1f, 2f).forEach {
            assertState(transform(PagerPageTransition.EFFECT_DEPTH, it), alpha = 0f)
        }
    }

    @Test
    fun `zoom out keeps the current page untouched`() {
        assertState(transform(PagerPageTransition.EFFECT_ZOOM_OUT, 0f))
    }

    @Test
    fun `zoom out shrinks and fades pages as they move away`() {
        assertState(
            transform(PagerPageTransition.EFFECT_ZOOM_OUT, -0.1f),
            alpha = 0.5f + (0.9f - 0.85f) / 0.15f * 0.5f,
            scale = 0.9f,
            translationX = 1000f * 0.1f / 2f - 1500f * 0.1f / 2f / 2f,
        )
    }

    @Test
    fun `zoom out clamps the scale and alpha at the minimum`() {
        assertState(
            transform(PagerPageTransition.EFFECT_ZOOM_OUT, 0.5f),
            alpha = 0.5f,
            scale = 0.85f,
            translationX = -1000f * 0.15f / 2f + 1500f * 0.15f / 2f / 2f,
        )
    }

    @Test
    fun `zoom out uses the vertical axis for a vertical pager`() {
        assertState(
            transform(PagerPageTransition.EFFECT_ZOOM_OUT, 0.5f, isHorizontal = false),
            alpha = 0.5f,
            scale = 0.85f,
            translationY = -1500f * 0.15f / 2f + 1000f * 0.15f / 2f / 2f,
        )
    }

    @Test
    fun `zoom out hides pages that are fully off screen`() {
        listOf(-2f, -1f, 1f, 2f).forEach {
            assertState(transform(PagerPageTransition.EFFECT_ZOOM_OUT, it), alpha = 0f)
        }
    }

    @Test
    fun `reset restores a transformed page`() {
        val state = PageState()
        val page = mockPage(state)
        PagerPageTransition.createTransformer(PagerPageTransition.EFFECT_DEPTH, true, true)!!
            .transformPage(page, 0.5f)

        PagerPageTransition.reset(page)

        assertState(state)
    }

    @Test
    fun `switching effects leaves no leftovers from the previous one`() {
        val state = PageState()
        val page = mockPage(state)
        PagerPageTransition.createTransformer(PagerPageTransition.EFFECT_ZOOM_OUT, false, true)!!
            .transformPage(page, 0.5f)

        PagerPageTransition.createTransformer(PagerPageTransition.EFFECT_FADE, true, true)!!
            .transformPage(page, 0f)

        assertState(state)
    }

    private companion object {
        const val DELTA = 0.001f
    }
}
