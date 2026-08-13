package eu.kanade.tachiyomi.ui.source.browse

import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil3.dispose
import coil3.request.crossfade
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.databinding.MangaListItemBinding
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.util.view.setCards
import yokai.domain.manga.models.cover
import yokai.util.coil.loadManga

/**
 * Class used to hold the displayed data of a manga in the catalogue, like the cover or the title.
 * All the elements from the layout file "item_catalogue_list" are available in this class.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @constructor creates a new catalogue holder.
 */
class BrowseSourceListHolder(
    private val view: View,
    adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    showOutline: Boolean,
) :
    BrowseSourceHolder(view, adapter) {

    private val binding = MangaListItemBinding.bind(view)

    // Identifies whatever cover this view is currently bound to, so a re-bind that doesn't
    // actually change the manga (e.g. list refreshes) doesn't dispose a perfectly fine cover
    // and restart loading it from scratch.
    private var boundCoverKey: String? = null

    init {
        setCards(showOutline, binding.card, binding.unreadDownloadBadge.badgeView)
    }

    /**
     * Method called from [CatalogueAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param manga the manga to bind.
     */
    override fun onSetValues(manga: Manga) {
        binding.title.text = manga.title
        binding.inLibraryBadge.badge.isVisible = manga.favorite

        setImage(manga)
    }

    override fun setImage(manga: Manga) {
        // Update the cover.
        if (manga.thumbnail_url == null) {
            boundCoverKey = null
            binding.coverThumbnail.dispose()
            binding.coverThumbnail.setImageDrawable(null)
        } else {
            manga.id ?: return
            val coverKey = "${manga.id}:${manga.thumbnail_url}:${manga.cover_last_modified}"
            binding.coverThumbnail.alpha = if (manga.favorite) 0.34f else 1.0f
            if (coverKey == boundCoverKey) return
            boundCoverKey = coverKey

            binding.coverThumbnail.dispose()
            binding.coverThumbnail.setImageDrawable(null)
            binding.coverThumbnail.loadManga(manga.cover()) {
                // The default crossfade can get stuck mid-fade (showing nothing) when the
                // result lands while this row is off-screen during a fast scroll - the row
                // only paints again once it's rebound, e.g. by scrolling back over it a
                // second time.
                crossfade(false)
            }
        }
    }
}
