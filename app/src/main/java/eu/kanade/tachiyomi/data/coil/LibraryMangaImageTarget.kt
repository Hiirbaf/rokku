package eu.kanade.tachiyomi.data.coil

import android.graphics.BitmapFactory
import android.view.View
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.palette.graphics.Palette
import co.touchlab.kermit.Logger
import coil3.Image
import coil3.asDrawable
import coil3.target.ImageViewTarget
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.updateCoverLastModified
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.util.system.launchIO
import uy.kohesive.injekt.injectLazy

class LibraryMangaImageTarget(
    override val view: ImageView,
    private val libraryManga: Manga,
    private val progress: View? = null,
) : ImageViewTarget(view) {

    override fun onStart(placeholder: Image?) {
        progress?.isVisible = true
        Logger.d { "LibraryMangaImageTarget onStart for mangaId=${libraryManga.id} title=${libraryManga.title}" }
        super.onStart(placeholder)
    }

    override fun onSuccess(result: Image) {
        progress?.isVisible = false
        Logger.d { "LibraryMangaImageTarget onSuccess for mangaId=${libraryManga.id} title=${libraryManga.title}" }
        // Bypasses super.onSuccess(), so callers must disable crossfade or the view never repaints.
        view.setImageDrawable(result.asDrawable(view.context.resources))
    }

    override fun onError(error: Image?) {
        progress?.isVisible = false
        super.onError(error)
        Logger.w { "LibraryMangaImageTarget onError for mangaId=${libraryManga.id} title=${libraryManga.title} thumbnail_url=${libraryManga.thumbnail_url}" }
        checkForCorruptedCover(libraryManga)
    }
}

/**
 * A load failing for a favorite manga usually means its cached cover file is corrupted, so
 * invalidate it and let the next load re-fetch/re-decode it. Shared by every target that loads a
 * manga's cover, not just [LibraryMangaImageTarget].
 */
fun checkForCorruptedCover(manga: Manga) {
    if (!manga.favorite) return
    val coverCache: CoverCache by injectLazy()
    launchIO {
        val file = coverCache.getCoverFile(manga.thumbnail_url, false)
        // if the file exists and the there was still an error then the file is corrupted
        if (file != null && file.exists()) {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.path, options)
            if (options.outWidth == -1 || options.outHeight == -1) {
                manga.updateCoverLastModified()
                file.delete()
            }
        }
    }
}

fun Palette.getBestColor(defaultColor: Int) = getBestColor() ?: defaultColor

fun Palette.getBestColor(): Int? {
    val vibPopulation = vibrantSwatch?.population ?: -1
    val domLum = dominantSwatch?.hsl?.get(2) ?: -1f
    val mutedPopulation = mutedSwatch?.population ?: -1
    val mutedSaturationLimit = if (mutedPopulation > vibPopulation * 3f) 0.1f else 0.25f
    return when {
        (dominantSwatch?.hsl?.get(1) ?: 0f) >= .25f &&
            domLum <= .8f && domLum > .2f -> dominantSwatch?.rgb

        vibPopulation >= mutedPopulation * 0.75f -> vibrantSwatch?.rgb

        mutedPopulation > vibPopulation * 1.5f &&
            (mutedSwatch?.hsl?.get(1) ?: 0f) > mutedSaturationLimit -> mutedSwatch?.rgb

        else -> arrayListOf(vibrantSwatch, lightVibrantSwatch, darkVibrantSwatch).maxByOrNull {
            if (it === vibrantSwatch) (it?.population ?: -1) * 3 else it?.population ?: -1
        }?.rgb
    }
}
