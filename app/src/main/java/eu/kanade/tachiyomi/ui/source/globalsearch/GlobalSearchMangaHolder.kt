package eu.kanade.tachiyomi.ui.source.globalsearch

import android.graphics.drawable.RippleDrawable
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.databinding.SourceGlobalSearchControllerCardItemBinding
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.makeShapeCorners
import eu.kanade.tachiyomi.util.view.setCards
import yokai.domain.manga.models.MangaCover
import yokai.domain.manga.models.cover
import yokai.presentation.manga.components.MangaCover as MangaCoverComposable
import yokai.presentation.theme.YokaiTheme

class GlobalSearchMangaHolder(view: View, adapter: GlobalSearchCardAdapter) :
    BaseFlexibleViewHolder(view, adapter) {

    private val binding = SourceGlobalSearchControllerCardItemBinding.bind(view)

    // Same Coil-Compose-backed cover renderer already used (and working) by Browse/Latest's
    // BrowseSourceGridHolder. The previous manual ImageView + dispose()/CoverViewTarget approach
    // was prone to a classic RecyclerView async-image race - the list resorts/rebinds this
    // holder repeatedly, and by the time a cover finished loading the holder had often moved on
    // to a different item already. Coil's Compose integration scopes the request to composition
    // state instead, sidestepping that race entirely.
    private var cover by mutableStateOf(MangaCover(0L, 0L, "", 0L, false))

    init {
        binding.itemImage.setContent {
            YokaiTheme {
                MangaCoverComposable(data = cover)
            }
        }

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
        cover = manga.cover()
    }
}
