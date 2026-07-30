package yokai.util.coil

import android.content.Context
import android.text.Spanned
import android.widget.TextView
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableLoader
import io.noties.markwon.image.AsyncDrawableScheduler
import io.noties.markwon.image.DrawableUtils
import io.noties.markwon.image.ImageSpanFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.commonmark.node.Image

/**
 * Markwon image-loading plugin backed by Coil3.
 *
 * Markwon's official image-coil artifact is built against Coil 2.x's `coil.*` package and isn't
 * usable with Coil3 (`coil3.*`), so this ports the same [AsyncDrawableLoader] approach directly
 * on top of Coil3's suspend-based [coil3.ImageLoader.execute], the same loading pattern already
 * used elsewhere in this codebase (e.g. LibraryUpdateNotifier), instead of the callback-based
 * Target/Disposable API the original plugin uses.
 */
class MarkwonCoil3ImagesPlugin private constructor(context: Context) : AbstractMarkwonPlugin() {

    private val loader = Coil3AsyncDrawableLoader(context)

    override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
        builder.setFactory(Image::class.java, ImageSpanFactory())
    }

    override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
        builder.asyncDrawableLoader(loader)
    }

    override fun beforeSetText(textView: TextView, markdown: Spanned) {
        AsyncDrawableScheduler.unschedule(textView)
    }

    override fun afterSetText(textView: TextView) {
        AsyncDrawableScheduler.schedule(textView)
    }

    private class Coil3AsyncDrawableLoader(private val context: Context) : AsyncDrawableLoader() {
        private val scope = CoroutineScope(Dispatchers.Main.immediate)
        private val jobs = HashMap<AsyncDrawable, Job>()

        override fun load(drawable: AsyncDrawable) {
            jobs[drawable] = scope.launch {
                val request = ImageRequest.Builder(context)
                    .data(drawable.destination)
                    .build()
                val loadedDrawable = runCatching {
                    context.imageLoader.execute(request).image?.asDrawable(context.resources)
                }.getOrNull()
                jobs.remove(drawable)
                if (loadedDrawable != null && drawable.isAttached) {
                    DrawableUtils.applyIntrinsicBoundsIfEmpty(loadedDrawable)
                    drawable.result = loadedDrawable
                }
            }
        }

        override fun cancel(drawable: AsyncDrawable) {
            jobs.remove(drawable)?.cancel()
        }

        override fun placeholder(drawable: AsyncDrawable) = null
    }

    companion object {
        fun create(context: Context): MarkwonCoil3ImagesPlugin = MarkwonCoil3ImagesPlugin(context)
    }
}
