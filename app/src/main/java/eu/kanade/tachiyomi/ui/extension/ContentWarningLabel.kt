package eu.kanade.tachiyomi.ui.extension

import androidx.annotation.AttrRes
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.model.ContentWarning
import yokai.i18n.MR

data class ContentWarningLabel(
    val title: StringResource,
    val description: StringResource,
    @AttrRes val colorAttr: Int,
)

val ContentWarning.label: ContentWarningLabel?
    get() = when (this) {
        ContentWarning.SAFE -> null

        ContentWarning.MIXED -> ContentWarningLabel(
            MR.strings.ext_mixed_short,
            MR.strings.ext_content_warning_mixed_description,
            R.attr.colorPrimary,
        )

        ContentWarning.NSFW -> ContentWarningLabel(
            MR.strings.nsfw_short,
            MR.strings.ext_content_warning_nsfw_description,
            R.attr.colorError,
        )
    }