package eu.kanade.tachiyomi.ui.source.globalsearch

import android.graphics.drawable.RippleDrawable
import android.view.View
import androidx.core.view.isVisible
import coil3.dispose
import eu.kanade.tachiyomi.databinding.SourceGlobalSearchControllerCardItemBinding
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.makeShapeCorners
import eu.kanade.tachiyomi.util.view.setCards
import yokai.domain.manga.models.cover
import yokai.util.coil.loadManga

class GlobalSearchMangaHolder(view: View, adapter: GlobalSearchCardAdapter) :
    BaseFlexibleViewHolder(view, adapter) {

    private val binding = SourceGlobalSearchControllerCardItemBinding.bind(view)

    // The list gets rebound repeatedly in quick succession (multiple times within a single
    // source's own update, well under the debounce window). dispose()+reload on every bind
    // cancels the in-flight Coil request each time, so - since network fetches routinely take
    // longer than the gap between rebinds - the cover can be cancelled every single time before
    // it ever finishes loading, no matter how it's throttled upstream. Skip the dispose+reload
    // entirely if this is the same manga/cover as last time, regardless of whether that load
    // has finished yet, so the first enqueued request is left alone to complete.
    private var lastBoundMangaId: Long? = null
    private var lastBoundThumbnailUrl: String? = null

    init {
        itemView.setOnClickListener {
            val item = adapter.getItem(flexibleAdapterPosition)
            if (item != null) {
                adapter.mangaClickListener.onMangaClick(item.manga)
            }
        }
        val bottom = 2.dpToPx
        val others = 5.dpToPx
        (binding.constraintLayout.foreground as? RippleDrawable)?.apply {
            setLayerSize(1, 0, 0)
            for (i in 0 until numberOfLayers) {
                setLayerInset(i, others, others, others, bottom)
            }
        }
        binding.favoriteButton.shapeAppearanceModel =
            binding.card.makeShapeCorners(binding.card.radius, binding.card.radius)
        itemView.setOnLongClickListener {
            adapter.mangaClickListener.onMangaLongClick(flexibleAdapterPosition, adapter)
            true
        }
        setCards(adapter.showOutlines, binding.card, binding.favoriteButton)
    }

    fun bind(manga: Manga) {
        binding.title.text = manga.title
        binding.favoriteButton.isVisible = manga.favorite
        setImage(manga)
    }

    fun setImage(manga: Manga) {
        if (manga.id == lastBoundMangaId && manga.thumbnail_url == lastBoundThumbnailUrl) {
            return
        }
        lastBoundMangaId = manga.id
        lastBoundThumbnailUrl = manga.thumbnail_url

        binding.itemImage.dispose()
        if (!manga.thumbnail_url.isNullOrEmpty()) {
            binding.itemImage.loadManga(manga.cover(), binding.progress)
        }
    }
}
