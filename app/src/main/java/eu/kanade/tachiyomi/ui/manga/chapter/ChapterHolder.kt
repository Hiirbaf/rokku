package eu.kanade.tachiyomi.ui.manga.chapter

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.View
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import com.google.android.material.shape.CornerFamily
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.databinding.ChaptersItemBinding
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.ui.manga.MangaDetailsAdapter
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.preferredChapterName
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import yokai.i18n.MR
import yokai.util.lang.getString
import android.R as AR

@SuppressLint("ClickableViewAccessibility")
class ChapterHolder(
    view: View,
    private val adapter: MangaDetailsAdapter,
) : BaseChapterHolder(view, adapter) {

    private val binding = ChaptersItemBinding.bind(view)
    private var localSource = false

    init {
        binding.downloadButton.downloadButton.setOnLongClickListener {
            adapter.delegate.startDownloadRange(flexibleAdapterPosition)
            true
        }
        binding.expandedDownloadTarget.setOnTouchListener { _, event ->
            binding.downloadButton.downloadButton.onTouchEvent(event)
        }
    }

    fun bind(item: ChapterItem, manga: Manga) {
        val chapter = item.chapter
        val isLocked = item.isLocked
        itemView.transitionName = "details chapter ${chapter.id ?: 0L} transition"
        binding.chapterTitle.text =
            chapter.preferredChapterName(itemView.context, manga, adapter.preferences)

        binding.downloadButton.downloadButton.isVisible = !manga.isLocal() && !isLocked
        localSource = manga.isLocal()

        ChapterUtil.setTextViewForChapter(binding.chapterTitle, item, hideStatus = isLocked)

        val statuses = mutableListOf<String>()

        ChapterUtil.relativeDate(chapter)?.let { statuses.add(it) }

        val showPagesLeft = !chapter.read && chapter.last_page_read > 0 && !isLocked

        if (showPagesLeft) {
            statuses.add(
                itemView.context.getString(
                    MR.strings.page_x_of_y,
                    chapter.last_page_read + 1,
                    chapter.pages_left + chapter.last_page_read,
                ),
            )
        }

        if (chapter.scanlator?.isNotBlank() == true) {
            statuses.add(chapter.scanlator!!)
        }

        if (getFrontView().translationX == 0f) {
            binding.read.setImageResource(
                if (item.read) R.drawable.ic_eye_off_24dp else R.drawable.ic_eye_24dp,
            )
            binding.bookmark.setImageResource(
                if (item.bookmark) R.drawable.ic_bookmark_off_24dp else R.drawable.ic_bookmark_24dp,
            )
        }
        ChapterUtil.setTextViewForChapter(
            binding.chapterScanlator,
            item,
            showBookmark = false,
            hideStatus = isLocked,
            isDetails = true,
        )
        binding.chapterScanlator.text = statuses.joinToString(" • ")

        val status = when {
            adapter.isSelected(flexibleAdapterPosition) -> Download.State.CHECKED
            else -> item.status
        }

        notifyStatus(status, item.isLocked, item.progress)
        resetFrontView()
        if (flexibleAdapterPosition == 1) {
            if (!adapter.hasShownSwipeTut.get()) showSlideAnimation()
        }
    }

    private fun showSlideAnimation() {
        val slide = 100f.dpToPx
        val animatorSet = AnimatorSet()
        val anim1 = slideAnimation(0f, slide)
        anim1.startDelay = 1000
        anim1.doOnStart { binding.startView.isVisible = true }
        val anim2 = slideAnimation(slide, -slide)
        anim2.duration = 600
        anim2.startDelay = 500
        anim2.addUpdateListener {
            if (binding.startView.isVisible && getFrontView().translationX <= 0) {
                binding.startView.isVisible = false
                binding.endView.isVisible = true
            }
        }
        val anim3 = slideAnimation(-slide, 0f)
        anim3.startDelay = 750
        animatorSet.playSequentially(anim1, anim2, anim3)
        animatorSet.doOnEnd { adapter.hasShownSwipeTut.set(true) }
        animatorSet.start()
    }

    private fun slideAnimation(from: Float, to: Float): ObjectAnimator {
        return ObjectAnimator.ofFloat(getFrontView(), View.TRANSLATION_X, from, to)
            .setDuration(300)
    }

    override fun getFrontView(): View {
        return binding.chapterCard
    }

    override fun getRearEndView(): View {
        return binding.endView
    }

    override fun getRearStartView(): View {
        return binding.startView
    }

    private fun resetFrontView() {
        if (getFrontView().translationX != 0f) {
            itemView.post {
                androidx.transition.TransitionManager.endTransitions(adapter.recyclerView)
                adapter.notifyItemChanged(flexibleAdapterPosition)
            }
        }
    }

    fun notifyStatus(status: Download.State, locked: Boolean, progress: Int, animated: Boolean = false) = with(
        binding.downloadButton.downloadButton,
    ) {
        adapter.delegate.accentColor()?.let {
            binding.startView.setCardBackgroundColor(it)

            val color = binding.chapterCard.cardBackgroundColor.defaultColor
            val bgArray = FloatArray(3)
            val accentArray = FloatArray(3)
            ColorUtils.colorToHSL(color, bgArray)
            ColorUtils.colorToHSL(it, accentArray)
            bgArray[0] = accentArray[0]
            binding.chapterCard.setCardBackgroundColor(ColorUtils.HSLToColor(bgArray))

            binding.bookmark.imageTintList = ColorStateList.valueOf(
                context.getResourceColor(AR.attr.textColorPrimaryInverse),
            )
            TextViewCompat.setCompoundDrawableTintList(
                binding.chapterTitle,
                ColorStateList.valueOf(it),
            )
            accentColor = it
        }
        if (locked) {
            isVisible = false
            return
        }
        isVisible = !localSource
        setDownloadStatus(status, progress, animated)
    }

    fun setCorners(
        top: Boolean,
        bottom: Boolean,
    ) {
        val shapeModel =
            binding.chapterCard.shapeAppearanceModel
                .toBuilder()
                .apply {
                    setTopLeftCorner(CornerFamily.ROUNDED, if (top) 12f.dpToPx else 2f.dpToPx)
                    setTopRightCorner(CornerFamily.ROUNDED, if (top) 12f.dpToPx else 2f.dpToPx)
                    setBottomLeftCorner(CornerFamily.ROUNDED, if (bottom) 12f.dpToPx else 2f.dpToPx)
                    setBottomRightCorner(CornerFamily.ROUNDED, if (bottom) 12f.dpToPx else 2f.dpToPx)
                }.build()
        binding.chapterCard.shapeAppearanceModel = shapeModel
        binding.startView.shapeAppearanceModel = shapeModel
        binding.endView.shapeAppearanceModel = shapeModel
    }
}
