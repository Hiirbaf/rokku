package eu.kanade.tachiyomi.ui.source.filter

import android.annotation.SuppressLint
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.flexibleadapter.items.ISectionable
import eu.davidea.viewholders.FlexibleViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.util.system.getResourceColor

/**
 * Label for a [Filter.Group] nested within another [Filter.Group]. Unlike [GroupItem], this is a
 * plain (non-expandable) row: nested groups are flattened into their parent's sub-items instead
 * of being expandable themselves, since FlexibleAdapter doesn't reliably display sub-items that
 * are both expandable and sectionable at the same time.
 */
class GroupLabelItem(val filter: Filter.Group<*>) :
    AbstractFlexibleItem<GroupLabelItem.Holder>(),
    ISectionable<GroupLabelItem.Holder, GroupItem> {

    private var head: GroupItem? = null

    override fun getHeader(): GroupItem? = head

    override fun setHeader(header: GroupItem?) {
        head = header
    }

    @SuppressLint("PrivateResource")
    override fun getLayoutRes(): Int {
        return com.google.android.material.R.layout.design_navigation_item_subheader
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): Holder {
        return Holder(view, adapter)
    }

    override fun bindViewHolder(adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>, holder: Holder, position: Int, payloads: MutableList<Any?>?) {
        val view = holder.itemView as TextView
        view.text = filter.name
        view.setTextColor(view.context.getResourceColor(R.attr.colorOnBackground))
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
    )
}
