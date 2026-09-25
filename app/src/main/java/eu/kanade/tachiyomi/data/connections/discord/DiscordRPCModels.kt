package eu.kanade.tachiyomi.data.connections.discord

import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R

const val RICH_PRESENCE_TAG = "discord_rpc"

const val DOWNLOAD_BUTTON_LABEL = "Descargar"
const val DOWNLOAD_BUTTON_URL = "https://github.com/rokku-app/rokku/releases"
const val DISCORD_BUTTON_LABEL = "Discord"
const val DISCORD_BUTTON_URL = "https://discord.gg/fvskrQZb9j"

data class ReaderData(
    val incognitoMode: Boolean = false,
    val mangaId: Long? = null,
    val mangaTitle: String? = null,
    val chapterNumber: Float = 0f,
    val chapterProgress: Pair<Int, Int> = Pair(0, 0),
    val chapterTitle: String? = null,
    val thumbnailUrl: String? = null,
    val browsingOnly: Boolean = false,
)

enum class DiscordScreen(
    @StringRes val text: Int,
    @StringRes val details: Int,
    @StringRes val state: Int,
    val imageUrl: String,
) {
    APP(R.string.app_name, R.string.browsing, R.string.library, ROKKU_IMAGE_URL),
    LIBRARY(R.string.app_name, R.string.browsing, R.string.library, LIBRARY_IMAGE_URL),
    // UPDATES(R.string.app_name, R.string.scrolling, R.string.recents, updatesImageUrl),
    HISTORY(R.string.app_name, R.string.scrolling, R.string.recents, HISTORY_IMAGE_URL),
    BROWSE(R.string.app_name, R.string.browsing, R.string.browse, BROWSE_IMAGE_URL),
    MORE(R.string.app_name, R.string.messing, R.string.settings, moreImageUrl),
    WEBVIEW(R.string.app_name, R.string.browsing, R.string.action_web_view, webviewImageUrl),
    MANGA(R.string.app_name, R.string.comic, R.string.reading, mangaImageUrl),
}

private const val CDN = "https://cdn.discordapp.com/"
private const val ROKKU_IMAGE_URL = "${CDN}emojis/1550782013022281728.webp?quality=lossless"
private const val LIBRARY_IMAGE_URL = "${CDN}emojis/1552645911254138911.webp?quality=lossless"
// private const val updatesImageUrl = "${CDN}emojis/1391945005194674237.webp?quality=lossless"
private const val HISTORY_IMAGE_URL = "${CDN}emojis/1552649831149998131.webp?quality=lossless"
private const val BROWSE_IMAGE_URL = "${CDN}emojis/1552656747083206747.webp?quality=lossless"
private const val moreImageUrl = "${CDN}emojis/1391947518224371772.webp?quality=lossless"
private const val webviewImageUrl = "${CDN}emojis/1391952048223817791.webp?quality=lossless"
private const val mangaImageUrl = "${CDN}emojis/1391953132329898124.webp?quality=lossless"
