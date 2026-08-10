package eu.kanade.tachiyomi.ui.source.filter

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.flexibleadapter.items.ISectionable
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.util.view.setAnimVectorCompat

/**
 * Label for a [Filter.Group] nested within another [Filter.Group].
 *
 * FlexibleAdapter doesn't reliably display sub-items of an item that is both
 * [eu.davidea.flexibleadapter.items.IExpandable] and [ISectionable] at the same time - two
 * separate attempts at that combo (a fully expandable nested [GroupItem], and an expandable
 * [GroupLabelItem]) both left the nested group's own children invisible. So this label stays a
 * plain, non-expandable [ISectionable] row. Its own [children] are pre-built once (with
 * [ISectionable.getHeader] pointing at the single top-level [GroupItem], matching FlexibleAdapter's
 * single-level section model) and are only spliced into the adapter's live list - via
 * [FlexibleAdapter.addItems]/[FlexibleAdapter.removeRange] plus the top-level [GroupItem]'s own
 * [GroupItem.addSubItems]/[GroupItem.removeSubItems] to keep its subItems model in sync, since
 * that's what [FlexibleAdapter.collapse] reads from when the *top-level* group collapses - when
 * the user taps this label. That gives each nested group its own collapse/expand without ever
 * making a single item both expandable and sectionable.
 */
class GroupLabelItem(val filter: Filter.Group<*>) :
    AbstractFlexibleItem<GroupLabelItem.Holder>(),
    ISectionable<GroupLabelItem.Holder, GroupItem> {

    private var head: GroupItem? = null

    override fun getHeader(): GroupItem? = head

    override fun setHeader(header: GroupItem?) {
        head = header
    }

    var isExpanded = false
        private set

    /** This label's direct children, pre-built with their [ISectionable.getHeader] already set. */
    var children: List<ISectionable<*, GroupItem>> = emptyList()

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

        holder.itemView.setOnClickListener {
            toggle(adapter, holder.bindingAdapterPosition)
        }
    }

    private fun toggle(adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>, position: Int) {
        if (position < 0 || children.isEmpty()) return
        val header = head ?: return

        if (isExpanded) {
            val removed = collapseVisible()
            adapter.removeRange(position + 1, removed)
            header.removeSubItems(children)
        } else {
            @Suppress("UNCHECKED_CAST")
            adapter.addItems(position + 1, children as List<IFlexible<RecyclerView.ViewHolder>>)
            val modelIndex = header.getSubItemPosition(this)
            if (modelIndex >= 0) {
                header.addSubItems(modelIndex + 1, children)
            }
        }
        isExpanded = !isExpanded
        adapter.notifyItemChanged(position)
    }

    /**
     * Recursively counts how many descendant rows are currently visible under this label
     * (accounting for any nested [GroupLabelItem] that's expanded), resetting every descendant's
     * [isExpanded] flag to false along the way so re-expanding this label always starts from a
     * consistent, fully-collapsed state.
     */
    private fun collapseVisible(): Int {
        var count = children.size
        for (child in children) {
            if (child is GroupLabelItem && child.isExpanded) {
                count += child.collapseVisible()
                child.isExpanded = false
            }
        }
        return count
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return filter == (other as GroupLabelItem).filter
    }

    override fun hashCode(): Int {
        return filter.hashCode()
    }

    class Holder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>) : FlexibleViewHolder(
        view,
        adapter,
    ) {
        val title: TextView = itemView.findViewById(R.id.title)
        val icon: ImageView = itemView.findViewById(R.id.expand_icon)
    }
}
