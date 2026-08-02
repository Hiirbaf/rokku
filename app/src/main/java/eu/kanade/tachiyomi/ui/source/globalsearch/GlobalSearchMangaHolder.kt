package eu.kanade.tachiyomi.ui.source.globalsearch

import android.graphics.drawable.RippleDrawable
import android.util.Log
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
        binding.itemImage.dispose()
        // TEMP DIAGNOSTIC LOGGING (blank-cover bug investigation) - remove once diagnosed.
        Log.e(
            "RokkuCoverDebug",
            "GlobalSearchMangaHolder.setImage: title=${manga.title} " +
                "thumbnail_url=${manga.thumbnail_url} initialized=${manga.initialized} favorite=${manga.favorite}",
        )
        if (!manga.thumbnail_url.isNullOrEmpty()) {
            binding.itemImage.loadManga(manga.cover(), binding.progress)
        } else {
            Log.e("RokkuCoverDebug", "GlobalSearchMangaHolder.setImage: SKIPPED load, blank thumbnail_url for ${manga.title}")
        }
    }
}
