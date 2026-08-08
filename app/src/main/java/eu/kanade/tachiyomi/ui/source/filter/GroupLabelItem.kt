package eu.kanade.tachiyomi.ui.source.filter

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractExpandableItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.flexibleadapter.items.ISectionable
import eu.davidea.viewholders.ExpandableViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.util.view.setAnimVectorCompat

/**
 * Label for a [Filter.Group] nested within another [Filter.Group]. Uses FlexibleAdapter's own
 * expandable-item mechanism (like [GroupItem]/[SortGroup]) rather than manually inserting or
 * removing rows, so expand/collapse gets the same smooth, well-tested animation as those - a
 * hand-rolled toggle animated noticeably worse and was prone to library-internal bugs
 * (see the (now removed) `addItems`/`removeItems`/`updateDataSet`-based approaches this replaced).
 *
 * [ISectionable.getHeader] always points at the top-level ancestor [GroupItem], never at this (or
 * any other intermediate) label: a prior attempt at this same expandable+sectionable combo pointed
 * each level's children at its *own* nested group as header, which left them invisible until the
 * whole filter sheet was rebuilt (e.g. by tapping Reset). Keeping every descendant's header on the
 * single top-level group avoids that.
 */
class GroupLabelItem(val filter: Filter.Group<*>) :
    AbstractExpandableItem<GroupLabelItem.Holder, ISectionable<*, GroupItem>>(),
    ISectionable<GroupLabelItem.Holder, GroupItem> {

    init {
        isExpanded = false
    }

    private var head: GroupItem? = null

    override fun getHeader(): GroupItem? = head

    override fun setHeader(header: GroupItem?) {
        head = header
    }

    override fun getLayoutRes(): Int {
        return R.layout.navigation_view_group
    }

    override fun getItemViewType(): Int {
        return 104
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): Holder {
        return Holder(view, adapter)
    }

    override fun bindViewHolder(adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>, holder: Holder, position: Int, payloads: MutableList<Any?>?) {
        holder.title.text = filter.name

        holder.icon.setAnimVectorCompat(
            if (isExpanded) {
                R.drawable.anim_expand_more_to_less
            } else {
                R.drawable.anim_expand_less_to_more
            },
        )

        holder.itemView.setOnClickListener(holder)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return filter == (other as GroupLabelItem).filter
    }

    override fun hashCode(): Int {
        return filter.hashCode()
    }

    open class Holder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>) : ExpandableViewHolder(
        view,
        adapter,
        true,
    ) {

        val title: TextView = itemView.findViewById(R.id.title)
        val icon: ImageView = itemView.findViewById(R.id.expand_icon)

        override fun shouldNotifyParentOnClick(): Boolean {
            return true
        }
    }
}
