package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.util.AttributeSet
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.system.ThemeUtil
import eu.kanade.tachiyomi.util.system.withIOContext
import yokai.presentation.reader.ChapterTransition
import yokai.presentation.theme.YokaiTheme

class ReaderTransitionView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AbstractComposeView(context, attrs) {

    private var data: Data? by mutableStateOf(null)

    init {
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }

    fun bind(theme: Int, transition: ChapterTransition, downloadManager: DownloadManager, manga: Manga?) {
        data = if (manga != null) {
            Data(
                theme = theme,
                manga = manga,
                transition = transition,
                currChapterDownloaded = transition.from.pageLoader?.isLocal == true,
                downloadManager = downloadManager,
            )
        } else {
            null
        }
    }

    @Composable
    override fun Content() {
        data?.let {
            // skipCache = true walks the SAF tree, which blocks on a binder call; keep it off the main thread.
            val goingToChapterDownloaded by produceState(initialValue = it.manga.isLocal(), it) {
                value = it.manga.isLocal() ||
                    it.transition.to?.chapter?.let { goingToChapter ->
                        withIOContext {
                            it.downloadManager.isChapterDownloaded(
                                chapter = goingToChapter,
                                manga = it.manga,
                                skipCache = true,
                            )
                        }
                    } ?: false
            }
            YokaiTheme {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodySmall,
                    LocalContentColor provides
                        ThemeUtil.readerContentColor(it.theme, MaterialTheme.colorScheme.onBackground),
                ) {
                    ChapterTransition(
                        manga = it.manga,
                        transition = it.transition,
                        currChapterDownloaded = it.currChapterDownloaded,
                        goingToChapterDownloaded = goingToChapterDownloaded,
                    )
                }
            }
        }
    }

    private data class Data(
        val theme: Int,
        val manga: Manga,
        val transition: ChapterTransition,
        val currChapterDownloaded: Boolean,
        val downloadManager: DownloadManager,
    )
}

fun missingChapterCount(transition: ChapterTransition): Int {
    if (transition.to == null) {
        return 0
    }

    val hasMissingChapters = when (transition) {
        is ChapterTransition.Prev -> hasMissingChapters(transition.from, transition.to)
        is ChapterTransition.Next -> hasMissingChapters(transition.to, transition.from)
    }

    if (!hasMissingChapters) {
        return 0
    }

    val chapterDifference = when (transition) {
        is ChapterTransition.Prev -> calculateChapterDifference(transition.from, transition.to)
        is ChapterTransition.Next -> calculateChapterDifference(transition.to, transition.from)
    }

    return chapterDifference.toInt()
}
