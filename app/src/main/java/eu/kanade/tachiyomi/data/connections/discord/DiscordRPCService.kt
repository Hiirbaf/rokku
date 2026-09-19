package eu.kanade.tachiyomi.data.connections.discord

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.compose.ui.util.fastAny
import yokai.domain.connections.service.ConnectionsPreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.formatChapterNumber
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import yokai.domain.category.interactor.GetCategories
import yokai.domain.category.models.Category.Companion.UNCATEGORIZED_ID
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class DiscordRPCService : Service() {

    private val connectionsManager: ConnectionsManager by injectLazy()

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        // Initialize the native SDK if not already done
        if (!DiscordRpcManager.isInitialized()) {
            DiscordRpcManager.init(applicationContext)
        }

        // Get stored OAuth token for the native SDK
        val effectiveToken = DiscordTokenStore.retrieve()

        if (!effectiveToken.isNullOrBlank()) {
            if (!DiscordRpcManager.isAuthorized()) {
                DiscordRpcManager.reconnectWithToken(effectiveToken)
            }

            // Listen for connection ready state and update RPC automatically when established
            launchIO {
                DiscordRpcManager.connectionStatus.collect { status ->
                    if (status == DiscordRpcManager.Status.Connected && !isPaused) {
                        try {
                            DiscordRpcManager.setOnlineStatus(
                                when (connectionsPreferences.discordRPCStatus().get()) {
                                    -1 -> DiscordRpcManager.StatusType.Dnd
                                    0 -> DiscordRpcManager.StatusType.Idle
                                    else -> DiscordRpcManager.StatusType.Online
                                },
                            )
                            val data = activeReaderData ?: ReaderData()
                            setScreen(this@DiscordRPCService, currentScreen, data)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating presence on connection ready: ${e.message}", e)
                        }
                    }
                }
            }
            notification(this)
        } else {
            connectionsPreferences.enableDiscordRPC().set(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCORD_TOGGLE_PAUSE_RESUME -> {
                isPaused = !isPaused
                notification(this)
                if (isPaused) {
                    DiscordRpcManager.clear()
                } else {
                    val data = activeReaderData ?: ReaderData()
                    launchIO { setScreen(this@DiscordRPCService, currentScreen, data) }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        NotificationReceiver.dismissNotification(this, Notifications.ID_DISCORD_RPC)
        DiscordRpcManager.destroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private fun notification(context: Context) {
        val toggleIcon = if (isPaused) R.drawable.ic_play_arrow_24dp else R.drawable.ic_pause_24dp
        val toggleText = if (isPaused) getString(R.string.resume) else getString(R.string.pause)
        val builder = context.notificationBuilder(Notifications.CHANNEL_DISCORD_RPC) {
            setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            setSmallIcon(R.drawable.ic_discord_24dp)
            setContentText(context.resources.getString(R.string.pref_discord_rpc))
            setAutoCancel(false)
            setOngoing(true)
            setWhen(since)
            setShowWhen(true)
            setUsesChronometer(true)
            addAction(toggleIcon, toggleText, togglePauseResumePendingIntent(context))
        }

        try {
            startForeground(Notifications.ID_DISCORD_RPC, builder.build())
        } catch (_: Exception) {
            DiscordRpcManager.clear()
            stopSelf()
        }
    }

    private fun togglePauseResumePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DiscordRPCService::class.java).apply {
            this.action = ACTION_DISCORD_TOGGLE_PAUSE_RESUME
        }
        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {

        private val connectionsPreferences: ConnectionsPreferences by injectLazy()
        private val handler = Handler(Looper.getMainLooper())

        internal var isPaused = false

        const val ACTION_DISCORD_TOGGLE_PAUSE_RESUME = "eu.kanade.tachiyomi.action.DISCORD_RPC_TOGGLE_PAUSE_RESUME"

        private const val ACTIVITY_UPDATE_DELAY_MS = 5_000L
        private var pendingActivityUpdate: Job? = null
        private var since = 0L
        private var activeReaderData: ReaderData? = null
        internal var currentScreen = DiscordScreen.APP

        private const val TAG = "DiscordRPCService"

        internal var lastUsedScreen = DiscordScreen.APP
            set(value) {
                field = if (value == DiscordScreen.MANGA || value == DiscordScreen.WEBVIEW) field else value
            }

        fun start(context: Context) {
            handler.removeCallbacksAndMessages(null)
            if (connectionsPreferences.enableDiscordRPC().get()) {
                since = System.currentTimeMillis()
                context.startForegroundService(Intent(context, DiscordRPCService::class.java))
            }
        }

        fun stop(context: Context, delay: Long = 30000L) {
            handler.postDelayed(
                { context.stopService(Intent(context, DiscordRPCService::class.java)) },
                delay,
            )
        }

        internal suspend fun setScreen(
            context: Context,
            discordScreen: DiscordScreen,
            readerData: ReaderData = ReaderData(),
        ) {
            if (!connectionsPreferences.enableDiscordRPC().get()) return
            currentScreen = discordScreen
            if (discordScreen != DiscordScreen.MANGA && discordScreen != DiscordScreen.WEBVIEW) {
                lastUsedScreen = discordScreen
                activeReaderData = null
            }
            if (!DiscordRpcManager.isReady() || isPaused) return
            updateDiscordRPC(context, readerData, discordScreen)
        }

        private fun updateDiscordRPC(
            context: Context,
            readerData: ReaderData,
            discordScreen: DiscordScreen,
        ) {
            val appName = context.getString(R.string.app_name)
            val customMessage = connectionsPreferences.discordCustomMessage().get()
            val showProgress = connectionsPreferences.discordShowProgress().get()
            val showTimestamp = connectionsPreferences.discordShowTimestamp().get()
            val showButtons = connectionsPreferences.discordShowButtons().get()
            val showDownloadButton = connectionsPreferences.discordShowDownloadButton().get()
            val showDiscordButton = connectionsPreferences.discordShowDiscordButton().get()

            val details = sanitizeField(
                when {
                    customMessage.isNotBlank() -> customMessage
                    readerData.mangaTitle != null -> readerData.mangaTitle
                    else -> context.getString(discordScreen.details)
                },
            )
            val chapterText = getFormattedChapterTitle(context, readerData)
            val state = sanitizeField(
                when {
                    !showProgress -> null
                    chapterText != null -> chapterText
                    else -> context.getString(discordScreen.state)
                },
            )
            val imageUrl = readerData.thumbnailUrl.takeUnless { it.isNullOrBlank() } ?: discordScreen.imageUrl

            val button1Label = if (showButtons && showDownloadButton) DOWNLOAD_BUTTON_LABEL else null
            val button1Url = if (showButtons && showDownloadButton) DOWNLOAD_BUTTON_URL else null
            val button2Label = if (showButtons && showDiscordButton) DISCORD_BUTTON_LABEL else null
            val button2Url = if (showButtons && showDiscordButton) DISCORD_BUTTON_URL else null

            DiscordRpcManager.setActivity(
                DiscordNativeActivity(
                    activityType = DiscordNativeActivity.TYPE_WATCHING,
                    name = appName,
                    details = details,
                    state = state,
                    startTimestamp = if (showTimestamp) since / 1000L else 0L,
                    largeImage = imageUrl,
                    largeText = appName,
                    smallImage = DiscordScreen.APP.imageUrl,
                    smallText = context.getString(DiscordScreen.APP.text),
                    button1Label = button1Label,
                    button1Url = button1Url,
                    button2Label = button2Label,
                    button2Url = button2Url,
                ),
            )
        }

        @OptIn(DelicateCoroutinesApi::class)
        fun updateReaderActivity(context: Context, readerData: ReaderData = ReaderData()) {
            pendingActivityUpdate?.cancel()
            pendingActivityUpdate = launchIO {
                delay(ACTIVITY_UPDATE_DELAY_MS)
                setReaderActivity(context, readerData)
            }
        }

        @Suppress("SwallowedException", "TooGenericExceptionCaught")
        internal suspend fun setReaderActivity(context: Context, readerData: ReaderData = ReaderData()) {
            if (!connectionsPreferences.enableDiscordRPC().get()) return
            if (readerData.mangaId == null) return
            try {
                val categories = Injekt.get<GetCategories>()
                    .awaitByMangaId(readerData.mangaId)
                    .map { it.id.toString() }
                    .run { ifEmpty { plus(UNCATEGORIZED_ID.toString()) } }

                val discordIncognito = isIncognito(categories, readerData.incognitoMode)
                val mangaTitle = readerData.mangaTitle.takeUnless { discordIncognito }
                val mangaThumbnail = if (discordIncognito) null else readerData.thumbnailUrl.takeUnless { it.isNullOrBlank() }

                val data = ReaderData(
                    incognitoMode = discordIncognito,
                    mangaId = readerData.mangaId,
                    mangaTitle = mangaTitle,
                    chapterNumber = readerData.chapterNumber,
                    chapterTitle = readerData.chapterTitle,
                    thumbnailUrl = mangaThumbnail,
                )

                currentScreen = DiscordScreen.MANGA
                activeReaderData = data

                if (!DiscordRpcManager.isReady() || isPaused) return
                withIOContext { setScreen(context, DiscordScreen.MANGA, data) }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting reader activity: ${e.message}", e)
            }
        }

        private fun getFormattedChapterTitle(context: Context, readerData: ReaderData): String? {
            if (readerData.incognitoMode) return null
            return if (connectionsPreferences.useChapterTitles().get()) {
                readerData.chapterTitle
            } else {
                context.resources.getString(
                    R.string.chapter_,
                    formatChapterNumber(readerData.chapterNumber.first.toDouble()),
                ) + "/${readerData.chapterNumber.second}"
            }
        }

        private fun sanitizeField(value: String?): String? {
            if (value == null) return null
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.length < 2) return "$trimmed "
            if (trimmed.length > 128) return trimmed.take(128)
            return trimmed
        }

        private fun isIncognito(categories: List<String>, incognitoMode: Boolean): Boolean {
            val discordIncognitoMode = connectionsPreferences.discordRPCIncognito().get()
            val incognitoCategories = connectionsPreferences.discordRPCIncognitoCategories().get()
            val incognitoCategory = categories.fastAny { it in incognitoCategories }
            return discordIncognitoMode || incognitoMode || incognitoCategory
        }
    }
}
