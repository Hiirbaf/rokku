package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.content.Context
import android.view.View
import android.view.animation.Interpolator
import android.widget.Scroller
import androidx.viewpager.widget.ViewPager
import kotlin.math.abs
import kotlin.math.max

/**
 * Page transition effects and speeds available in the paged reader. Values are the indexes of the
 * matching entries in the reader settings spinners.
 */
object PagerPageTransition {
    const val EFFECT_SLIDE = 0
    const val EFFECT_FADE = 1
    const val EFFECT_DEPTH = 2
    const val EFFECT_ZOOM_OUT = 3

    /**
     * Settle durations in milliseconds, indexed by the speed preference. Null keeps the pager's own
     * distance based duration.
     */
    private val durations = arrayOf<Int?>(null, 1, 150, 300, 600, 1000)

    fun durationFor(speed: Int): Int? = durations.getOrNull(speed)

    /**
     * Pages that are this close to being fully off screen are left untouched, so the neighbours of
     * the current page never end up stacked on top of it where they would steal its touch events.
     */
    private const val OFF_SCREEN_THRESHOLD = 0.995f

    /**
     * Creates the transformer for the given [effect], or null for the default slide.
     *
     * [leavingIsNegative] tells which side the page that is being left sits on when moving forward,
     * which is the right side on a right to left pager.
     */
    fun createTransformer(
        effect: Int,
        isHorizontal: Boolean,
        leavingIsNegative: Boolean,
    ): ViewPager.PageTransformer? {
        return when (effect) {
            EFFECT_FADE -> ViewPager.PageTransformer { page, position ->
                val size = page.sizeAlong(isHorizontal)
                if (abs(position) >= OFF_SCREEN_THRESHOLD) {
                    page.applyTransform(isHorizontal, alpha = 0f)
                } else {
                    page.applyTransform(isHorizontal, alpha = 1f - abs(position), translation = -position * size)
                }
            }

            EFFECT_DEPTH -> ViewPager.PageTransformer { page, position ->
                val size = page.sizeAlong(isHorizontal)
                val leavingPosition = if (leavingIsNegative) position else -position
                when {
                    abs(position) >= OFF_SCREEN_THRESHOLD -> page.applyTransform(isHorizontal, alpha = 0f)

                    leavingPosition <= 0f -> page.applyTransform(isHorizontal)

                    else -> page.applyTransform(
                        isHorizontal,
                        alpha = 1f - leavingPosition,
                        translation = -position * size,
                        scale = 0.75f + 0.25f * (1f - leavingPosition),
                    )
                }
            }

            EFFECT_ZOOM_OUT -> ViewPager.PageTransformer { page, position ->
                if (abs(position) >= 1f) {
                    page.applyTransform(isHorizontal, alpha = 0f)
                } else {
                    val scale = max(ZOOM_OUT_MIN_SCALE, 1f - abs(position))
                    val majorMargin = page.sizeAlong(isHorizontal) * (1f - scale) / 2f
                    val minorMargin = page.sizeAlong(!isHorizontal) * (1f - scale) / 2f
                    val translation = if (position < 0f) {
                        majorMargin - minorMargin / 2f
                    } else {
                        -majorMargin + minorMargin / 2f
                    }
                    page.applyTransform(
                        isHorizontal,
                        alpha = ZOOM_OUT_MIN_ALPHA +
                            (scale - ZOOM_OUT_MIN_SCALE) / (1f - ZOOM_OUT_MIN_SCALE) * (1f - ZOOM_OUT_MIN_ALPHA),
                        translation = translation,
                        scale = scale,
                    )
                }
            }

            else -> null
        }
    }

    /**
     * Restores the untransformed state of [page].
     */
    fun reset(page: View) {
        page.applyTransform(isHorizontal = true)
    }

    private fun View.sizeAlong(isHorizontal: Boolean) = (if (isHorizontal) width else height).toFloat()

    private fun View.applyTransform(
        isHorizontal: Boolean,
        alpha: Float = 1f,
        translation: Float = 0f,
        scale: Float = 1f,
    ) {
        this.alpha = alpha
        scaleX = scale
        scaleY = scale
        translationX = if (isHorizontal) translation else 0f
        translationY = if (isHorizontal) 0f else translation
    }

    private const val ZOOM_OUT_MIN_SCALE = 0.85f
    private const val ZOOM_OUT_MIN_ALPHA = 0.5f
}

/**
 * Scroller that can force the duration of every settle animation of the pager.
 */
internal class DurationScroller(context: Context) : Scroller(context, SettleInterpolator) {

    var fixedDuration: Int? = null

    override fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int, duration: Int) {
        super.startScroll(startX, startY, dx, dy, fixedDuration ?: duration)
    }

    private object SettleInterpolator : Interpolator {
        override fun getInterpolation(input: Float): Float {
            val t = input - 1f
            return t * t * t * t * t + 1f
        }
    }
}
