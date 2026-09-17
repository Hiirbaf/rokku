package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.isLTR
import android.R as AR

class MangaDetailsDivider(context: Context, val padding: Int = 12.dpToPx) : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {

    private val divider: Drawable
    private val baseDividerColor = ContextCompat.getColor(context, AR.color.divider)

    /** Shifts the divider's hue to match the cover's accent color, keeping its original alpha/lightness */
    var accentColor: Int? = null
        set(value) {
            field = value
            divider.setTint(
                value?.let {
                    val hsl = FloatArray(3)
                    ColorUtils.colorToHSL(baseDividerColor, hsl)
                    val accentHsl = FloatArray(3)
                    ColorUtils.colorToHSL(it, accentHsl)
                    hsl[0] = accentHsl[0]
                    ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(hsl), Color.alpha(baseDividerColor))
                } ?: baseDividerColor,
            )
        }

    init {
        val a = context.obtainStyledAttributes(intArrayOf(AR.attr.listDivider))
        divider = a.getDrawable(0)!!.mutate()
        a.recycle()
    }

    override fun onDraw(
        c: Canvas,
        parent: androidx.recyclerview.widget.RecyclerView,
        state: androidx.recyclerview.widget.RecyclerView.State,
    ) {
        val childCount = parent.childCount
        for (i in 0 until childCount - 1) {
            val child = parent.getChildAt(i)
            val params =
                child.layoutParams as androidx.recyclerview.widget.RecyclerView.LayoutParams
            val top = child.bottom + params.bottomMargin
            val bottom = top + divider.intrinsicHeight
            val left = parent.paddingStart + if (parent.context.resources.isLTR) padding else 0
            val right =
                parent.width - parent.paddingEnd - if (!parent.context.resources.isLTR) padding else 0

            divider.setBounds(left, top, right, bottom)
            divider.draw(c)
        }
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: androidx.recyclerview.widget.RecyclerView,
        state: androidx.recyclerview.widget.RecyclerView.State,
    ) {
        outRect.set(0, 0, 0, divider.intrinsicHeight)
    }
}
