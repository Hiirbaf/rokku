package eu.kanade.tachiyomi.ui.source.browse

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.domain.manga.models.Manga
import yokai.domain.manga.models.MangaCover
import yokai.domain.manga.models.cover
import yokai.presentation.manga.components.MangaComfortableGridItem
import yokai.presentation.manga.components.MangaCompactGridItem
import yokai.presentation.theme.YokaiTheme

/**
 * Class used to hold the displayed data of a manga in the library, like the cover or the title.
 * All the elements from the layout file "item_catalogue_grid" are available in this class.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @constructor creates a new library holder.
 */
class BrowseSourceGridHolder(
    private val view: ComposeView,
    private val adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    compact: Boolean,
    showOutline: Boolean,
) : BrowseSourceHolder(view, adapter) {

    var title by mutableStateOf("")
    var cover by mutableStateOf(MangaCover(0L, 0L, "", 0L, false))
    var isDuplicate by mutableStateOf(false)

    init {
        view.setContent {
            YokaiTheme {
                if (compact) {
                    MangaCompactGridItem(
                        coverData = cover,
                        title = title,
                        isSelected = cover.inLibrary || isDuplicate,
                        showOutline = showOutline,
                        inLibrary = cover.inLibrary,
                        isDuplicate = isDuplicate,
                    )
                } else {
                    MangaComfortableGridItem(
                        coverData = cover,
                        title = title,
                        isSelected = cover.inLibrary || isDuplicate,
                        showOutline = showOutline,
                        inLibrary = cover.inLibrary,
                        isDuplicate = isDuplicate,
                    )
                }
            }
        }
//        setCards(showOutline, binding.card, binding.unreadDownloadBadge.badgeView)
    }

    /**
     * Method called from [LibraryCategoryAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param manga the manga item to bind.
     */
    override fun onSetValues(manga: Manga, isDuplicate: Boolean) {
        // Update the title of the manga.
        title = manga.title
        cover = manga.cover()
        this.isDuplicate = isDuplicate

        // Update the cover.
        setImage(manga)
    }

    override fun setImage(manga: Manga) {
        if ((view.context as? Activity)?.isDestroyed == true) return
        cover = manga.cover()
    }
}
