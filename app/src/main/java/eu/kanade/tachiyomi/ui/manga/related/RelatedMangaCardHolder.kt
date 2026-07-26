package eu.kanade.tachiyomi.ui.manga.related

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import yokai.domain.manga.models.MangaCover
import yokai.domain.manga.models.cover
import yokai.i18n.MR
import yokai.presentation.manga.components.BadgeSegment
import yokai.presentation.manga.components.MangaComfortableGridItem
import yokai.presentation.manga.components.MangaCompactGridItem
import yokai.presentation.theme.YokaiTheme

class RelatedMangaCardHolder(private val view: ComposeView, private val adapter: RelatedMangaCardAdapter) :
    BaseFlexibleViewHolder(view, adapter) {

    private var title by mutableStateOf("")
    private var cover by mutableStateOf(MangaCover(0L, 0L, "", 0L, false))

    init {
        itemView.setOnClickListener {
            val item = adapter.getItem(flexibleAdapterPosition)
            if (item != null) adapter.clickListener.onMangaClick(item.manga)
        }
        view.setContent {
            YokaiTheme {
                val badgeSegments = buildList {
                    if (cover.inLibrary) {
                        add(
                            BadgeSegment.text(
                                backgroundColor = MaterialTheme.colorScheme.secondary,
                                text = stringResource(MR.strings.in_library),
                                textColor = MaterialTheme.colorScheme.onSecondary,
                            ),
                        )
                    }
                }
                if (adapter.compact) {
                    MangaCompactGridItem(
                        coverData = cover,
                        title = title,
                        isSelected = cover.inLibrary,
                        showOutline = adapter.showOutlines,
                        badgeSegments = badgeSegments,
                    )
                } else {
                    MangaComfortableGridItem(
                        coverData = cover,
                        title = title,
                        isSelected = cover.inLibrary,
                        showOutline = adapter.showOutlines,
                        badgeSegments = badgeSegments,
                    )
                }
            }
        }
    }

    fun bind(manga: Manga) {
        title = manga.title
        cover = manga.cover()
    }
}
