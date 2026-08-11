package eu.kanade.tachiyomi.ui.manga.chapter

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.MissingChaptersItemBinding
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.manga.MangaDetailsAdapter
import yokai.i18n.MR
import yokai.util.lang.getString

class MissingChaptersItem(val count: Int) : AbstractFlexibleItem<MissingChaptersItem.Holder>() {

    override fun getLayoutRes(): Int {
        return R.layout.missing_chapters_item
    }

    override fun isSelectable(): Boolean {
        return false
    }

    override fun isSwipeable(): Boolean {
        return false
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): Holder {
        return Holder(view, adapter as MangaDetailsAdapter)
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: Holder,
        position: Int,
        payloads: MutableList<Any?>?,
    ) {
        holder.bind(count)
    }

    override fun equals(other: Any?): Boolean {
        return other is MissingChaptersItem && other.count == count
    }

    override fun hashCode(): Int {
        return count.hashCode()
    }

    class Holder(view: View, adapter: MangaDetailsAdapter) : BaseFlexibleViewHolder(view, adapter) {
        private val binding = MissingChaptersItemBinding.bind(view)

        fun bind(count: Int) {
            binding.missingChaptersText.text =
                itemView.context.getString(MR.plurals.missing_chapters_count, count, count)
        }
    }
}
