package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.view.View.MeasureSpec
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the webtoon zoom-after-resize gap fixed by replacing the one-shot
 * `heightSet` guard in [WebtoonRecyclerView.onMeasure] with a `layoutParams.height == MATCH_PARENT`
 * check: the old guard measured the window's height exactly once, so a resize (split-screen,
 * foldable fold/unfold) that happened after that first measure was invisible to the zoom math --
 * zooming back out left the view pinned to the stale pre-resize height instead of filling the
 * (now different) window, i.e. a visible gap.
 */
@RunWith(AndroidJUnit4::class)
class WebtoonRecyclerViewTest {

    // WebtoonRecyclerView's own GestureDetector requires a Looper, which only the main thread
    // has in an instrumented test process -- every test body runs through here.
    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private fun newView(): WebtoonRecyclerView {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return WebtoonRecyclerView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            canZoomOut = true
        }
    }

    private fun measureAt(view: WebtoonRecyclerView, widthPx: Int, heightPx: Int) {
        view.measure(
            MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(heightPx, MeasureSpec.EXACTLY),
        )
    }

    @Test
    fun firstMeasureCapturesOriginalHeight() {
        var originalHeight = 0
        onMain {
            val view = newView()
            measureAt(view, 1080, 2000)
            originalHeight = view.originalHeight
        }

        assertEquals(2000, originalHeight)
    }

    @Test
    fun resizingWhileNotZoomedUpdatesOriginalHeight() {
        // Regression check for the bug itself: before the fix, a `heightSet` boolean latched
        // true after the first measure, so this second measure (simulating a split-screen/
        // foldable resize while at default zoom, where layoutParams.height is still
        // MATCH_PARENT) would have been silently ignored and originalHeight would stay 2000.
        var originalHeight = 0
        onMain {
            val view = newView()
            measureAt(view, 1080, 2000)
            measureAt(view, 1080, 1500)
            originalHeight = view.originalHeight
        }

        assertEquals(1500, originalHeight)
    }

    @Test
    fun zoomingWhileNotMatchParentDoesNotOverwriteOriginalHeight() {
        // While zoomed out, layoutParams.height holds a computed pixel value (not MATCH_PARENT),
        // so a relayout pass during that state (e.g. from scrolling) must not stomp the
        // original, unzoomed window height that the zoom math depends on.
        var originalHeight = 0
        onMain {
            val view = newView()
            measureAt(view, 1080, 2000)
            view.onScale(0.5f)
            val heightWhileZoomed = view.layoutParams.height
            measureAt(view, 1080, heightWhileZoomed)
            originalHeight = view.originalHeight
        }

        assertEquals(2000, originalHeight)
    }

    @Test
    fun zoomOutAfterResizeUsesTheResizedHeight() {
        var heightAfterZoomOut = 0
        onMain {
            val view = newView()
            measureAt(view, 1080, 2000)
            measureAt(view, 1080, 1500) // simulated resize while unzoomed

            view.onScale(0.5f)
            heightAfterZoomOut = view.layoutParams.height
        }

        assertEquals((1500 / 0.5f).toInt(), heightAfterZoomOut)
    }

    @Test
    fun zoomingBackToDefaultFillsTheParentInsteadOfAFixedHeight() {
        var heightAfterZoomingBack = 0
        onMain {
            val view = newView()
            measureAt(view, 1080, 2000)
            measureAt(view, 1080, 1500) // simulated resize while unzoomed
            view.onScale(0.5f) // zoom out, using the resized (1500) height

            view.onScale(2f) // currentScale 0.5 * 2 = 1.0, back to default
            heightAfterZoomingBack = view.layoutParams.height
        }

        assertEquals(
            "Zooming back to the default rate should fill the parent (MATCH_PARENT) so a " +
                "later resize keeps working, not freeze at whatever pixel height was last computed",
            ViewGroup.LayoutParams.MATCH_PARENT,
            heightAfterZoomingBack,
        )
    }
}
