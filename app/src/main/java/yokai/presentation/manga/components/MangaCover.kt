package yokai.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import eu.kanade.tachiyomi.R
import yokai.util.rememberResourceBitmapPainter

private val PlaceholderColor = Color(0x1F888888)

@Composable
fun MangaCover(
    data: Any?,
    modifier: Modifier = Modifier,
    ratio: Float? = null,
    contentDescription: String = "",
    shape: Shape = RoundedCornerShape(12.dp),
    contentScale: ContentScale = ContentScale.Crop,
    onClick: (() -> Unit)? = null,
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
) {
    val sizedModifier = modifier
        .then(if (ratio != null) Modifier.aspectRatio(ratio) else Modifier)
        .clip(shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    role = Role.Button,
                    onClick = onClick,
                )
            } else {
                Modifier
            },
        )

    // Coil's AsyncImage treats a null model as a genuine failed request
    // (NullRequestDataException), rendering the error/broken-image state - not a "nothing to
    // load yet" placeholder. A manga with no cover data yet (still loading/searching) is not
    // broken, so render the plain placeholder box directly instead of asking Coil to "load"
    // nothing.
    if (data == null) {
        Box(modifier = sizedModifier.fillMaxSize().background(PlaceholderColor))
        return
    }

    AsyncImage(
        model = data,
        placeholder = ColorPainter(PlaceholderColor),
        error = rememberResourceBitmapPainter(id = R.drawable.cover_error),
        contentDescription = contentDescription,
        contentScale = contentScale,
        onLoading = { state -> onState?.invoke(state) },
        onSuccess = { state -> onState?.invoke(state) },
        onError = { state -> onState?.invoke(state) },
        modifier = sizedModifier,
    )
}

object MangaCoverRatio {
    val SQUARE = 1f / 1f
    val BOOK = 2f / 3f
}
