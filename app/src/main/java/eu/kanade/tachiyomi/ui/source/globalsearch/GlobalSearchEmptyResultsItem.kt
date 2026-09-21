package eu.kanade.tachiyomi.ui.source.globalsearch

import android.view.View
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.widget.EmptyView
import yokai.i18n.MR

class GlobalSearchEmptyResultsItem : AbstractFlexibleItem<GlobalSearchEmptyResultsItem.Holder>() {
    override fun getLayoutRes(): Int = R.layout.source_global_search_empty_footer

    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    ): Holder = Holder(view, adapter)

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: Holder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        (holder.itemView as EmptyView).show(Icons.Outlined.SearchOff, MR.strings.no_results_found)
    }

    override fun isSelectable() = false

    override fun isSwipeable() = false

    override fun isDraggable() = false

    override fun equals(other: Any?): Boolean = this === other

    class Holder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    ) : FlexibleViewHolder(view, adapter)

    override fun hashCode(): Int = javaClass.hashCode()
}
