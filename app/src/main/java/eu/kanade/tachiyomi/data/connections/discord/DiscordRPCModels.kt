package eu.kanade.tachiyomi.data.connections.discord

import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R

const val RICH_PRESENCE_TAG = "discord_rpc"

const val DOWNLOAD_BUTTON_LABEL = "Descargar"
const val DOWNLOAD_BUTTON_URL = "https://github.com/rokku-app/rokku/releases"
const val DISCORD_BUTTON_LABEL = "Discord"
const val DISCORD_BUTTON_URL = "TU_INVITE_AQUI"

data class ReaderData(
    val incognitoMode: Boolean = false,
    val mangaId: Long? = null,
    val mangaTitle: String? = null,
    val chapterNumber: Pair<Float, Int> = Pair(0f, 0),
    val chapterTitle: String? = null,
    val thumbnailUrl: String? = null,
)

enum class DiscordScreen(
    @StringRes val text: Int,
    @StringRes val details: Int,
    @StringRes val state: Int,
    val imageUrl: String,
) {
    APP(R.string.app_name, R.string.browsing, R.string.library, tachiyomiImageUrl),
    LIBRARY(R.string.app_name, R.string.browsing, R.string.library, libraryImageUrl),
    UPDATES(R.string.app_name, R.string.scrolling, R.string.recents, updatesImageUrl),
    HISTORY(R.string.app_name, R.string.scrolling, R.string.recents, historyImageUrl),
    BROWSE(R.string.app_name, R.string.browsing, R.string.browse, browseImageUrl),
    MORE(R.string.app_name, R.string.messing, R.string.settings, moreImageUrl),
    WEBVIEW(R.string.app_name, R.string.browsing, R.string.action_web_view, webviewImageUrl),
    MANGA(R.string.app_name, R.string.comic, R.string.reading, mangaImageUrl),
}

private const val CDN = "https://cdn.discordapp.com/"
private const val tachiyomiImageUrl = "${CDN}emojis/1391938380727713842.webp?quality=lossless"
private const val libraryImageUrl = "${CDN}emojis/1391940601015959714.webp?quality=lossless"
private const val updatesImageUrl = "${CDN}emojis/1391945005194674237.webp?quality=lossless"
private const val historyImageUrl = "${CDN}emojis/1391945005194674237.webp?quality=lossless"
private const val browseImageUrl = "${CDN}emojis/1391945777517166804.webp?quality=lossless"
private const val moreImageUrl = "${CDN}emojis/1391947518224371772.webp?quality=lossless"
private const val webviewImageUrl = "${CDN}emojis/1391952048223817791.webp?quality=lossless"
private const val mangaImageUrl = "${CDN}emojis/1391953132329898124.webp?quality=lossless"
