package eu.kanade.tachiyomi.util.manga

import android.graphics.BitmapFactory
import android.os.Process
import androidx.annotation.ColorInt
import androidx.palette.graphics.Palette
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.coil.getBestColor
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.domain.manga.models.Manga
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Object that holds info about a covers size ratio + dominant colors */
object MangaCoverMetadata {
    private var coverRatioMap = ConcurrentHashMap<Long, Float>()
    private var coverColorMap = ConcurrentHashMap<Long, Pair<Int, Int>>()
    private var vibrantCoverColorMap = ConcurrentHashMap<Long, Int>()
    private val preferences by injectLazy<PreferencesHelper>()
    private val coverCache by injectLazy<CoverCache>()

    /**
     * Decoding a cover and running a palette over it is CPU work; a background-priority thread
     * gets a smaller OS-scheduled share of CPU under contention, so it competes less with the UI
     * thread while the library is scrolling.
     */
    private val metadataScope =
        CoroutineScope(
            SupervisorJob() +
                Executors
                    .newSingleThreadExecutor { runnable ->
                        Thread {
                            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                            runnable.run()
                        }.apply { name = "cover-metadata" }
                    }.asCoroutineDispatcher(),
        )

    fun load() {
        val ratios = preferences.coverRatios().get()
        coverRatioMap = ConcurrentHashMap(
            ratios.mapNotNull {
                val splits = it.split("|")
                val id = splits.firstOrNull()?.toLongOrNull()
                val ratio = splits.lastOrNull()?.toFloatOrNull()
                if (id != null && ratio != null) {
                    id to ratio
                } else {
                    null
                }
            }.toMap(),
        )
        val colors = preferences.coverColors().get()
        coverColorMap = ConcurrentHashMap(
            colors.mapNotNull {
                val splits = it.split("|")
                val id = splits.firstOrNull()?.toLongOrNull()
                val color = splits.getOrNull(1)?.toIntOrNull()
                val textColor = splits.getOrNull(2)?.toIntOrNull()
                if (id != null && color != null) {
                    id to (color to (textColor ?: 0))
                } else {
                    null
                }
            }.toMap(),
        )
    }

    fun setRatioAndColors(mangaId: Long?, mangaThumbnailUrl: String?, isInLibrary: Boolean, ogFile: UniFile? = null, force: Boolean = false) {
        if (!isInLibrary) {
            remove(mangaId)
        }
        if (getVibrantColor(mangaId) != null && !isInLibrary) return
        val file = ogFile
            ?: UniFile.fromFile(coverCache.getCustomCoverFile(mangaId))?.takeIf { it.exists() }
            ?: UniFile.fromFile(coverCache.getCoverFile(mangaThumbnailUrl, !isInLibrary))
        // if the file exists and the there was still an error then the file is corrupted
        if (file?.exists() == true) {
            val options = BitmapFactory.Options()
            val hasVibrantColor = if (isInLibrary) vibrantCoverColorMap[mangaId] != null else true
            if (getColors(mangaId) != null && hasVibrantColor && !force) {
                options.inJustDecodeBounds = true
            } else {
                options.inSampleSize = 4
            }
            val bitmap = try {
                val stream = file.openInputStream()
                BitmapFactory.decodeStream(stream, null, options)
            } catch (_: Throwable) {
                null
            }
            if (bitmap != null) {
                val palette = Palette.from(bitmap).generate()
                if (isInLibrary) {
                    palette.dominantSwatch?.let { swatch ->
                        addCoverColor(mangaId, swatch.rgb, swatch.titleTextColor)
                    }
                }
                palette.getBestColor()?.let { setVibrantColor(mangaId, it) }
            }
            if (isInLibrary && !(options.outWidth == -1 || options.outHeight == -1)) {
                addCoverRatio(mangaId, options.outWidth / options.outHeight.toFloat())
            }
        }
    }

    /** Queues [setRatioAndColors] onto the background-priority thread above. */
    fun setRatioAndColorsAsync(mangaId: Long?, mangaThumbnailUrl: String?, isInLibrary: Boolean, ogFile: UniFile? = null, force: Boolean = false) {
        metadataScope.launch {
            setRatioAndColors(mangaId, mangaThumbnailUrl, isInLibrary, ogFile, force)
        }
    }

    fun remove(manga: Manga) {
        remove(manga.id)
    }

    fun remove(mangaId: Long?) {
        mangaId ?: return
        coverRatioMap.remove(mangaId)
        coverColorMap.remove(mangaId)
    }

    fun addCoverRatio(manga: Manga, ratio: Float) {
        addCoverRatio(manga.id, ratio)
    }

    fun addCoverRatio(mangaId: Long?, ratio: Float) {
        mangaId ?: return
        coverRatioMap[mangaId] = ratio
    }

    fun addCoverColor(manga: Manga, @ColorInt color: Int, @ColorInt textColor: Int) {
        addCoverColor(manga.id, color, textColor)
    }

    fun addCoverColor(mangaId: Long?, @ColorInt color: Int, @ColorInt textColor: Int) {
        mangaId ?: return
        coverColorMap[mangaId] = color to textColor
    }

    fun getColors(manga: Manga): Pair<Int, Int>? = getColors(manga.id)

    fun getColors(mangaId: Long?): Pair<Int, Int>? {
        return coverColorMap[mangaId]
    }

    fun getRatio(manga: Manga): Float? {
        return coverRatioMap[manga.id]
    }

    fun setVibrantColor(mangaId: Long?, @ColorInt color: Int?) {
        mangaId ?: return

        if (color == null) {
            vibrantCoverColorMap.remove(mangaId)
            return
        }

        vibrantCoverColorMap[mangaId] = color
    }

    fun getVibrantColor(mangaId: Long?): Int? {
        return vibrantCoverColorMap[mangaId]
    }

    fun savePrefs() {
        val mapCopy = coverRatioMap.toMap()
        preferences.coverRatios().set(mapCopy.map { "${it.key}|${it.value}" }.toSet())
        val mapColorCopy = coverColorMap.toMap()
        preferences.coverColors().set(mapColorCopy.map { "${it.key}|${it.value.first}|${it.value.second}" }.toSet())
    }
}
