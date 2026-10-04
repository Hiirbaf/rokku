package eu.kanade.tachiyomi.data.connections.discord

import android.content.Context
import android.util.Log
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.util.system.launchIO
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import uy.kohesive.injekt.injectLazy
import yokai.domain.connections.service.ConnectionsPreferences

object DiscordAuthHelper {
    private const val TAG = "DiscordAuthHelper"

    private val connectionsManager: ConnectionsManager by injectLazy()
    private val connectionsPreferences: ConnectionsPreferences by injectLazy()

    @OptIn(DelicateCoroutinesApi::class)
    fun startLogin(context: Context, onResult: (Boolean) -> Unit = {}) {
        if (!DiscordRpcManager.isInitialized()) {
            DiscordRpcManager.init(context.applicationContext)
        }

        DiscordRpcManager.authorize { success ->
            if (!success) {
                Log.e(TAG, "Authorization failed or was cancelled")
                onResult(false)
                return@authorize
            }
            launchIO {
                val token = DiscordRpcManager.getAccessToken()
                if (token == null) {
                    onResult(false)
                    return@launchIO
                }
                val user = withContext(Dispatchers.IO) {
                    DiscordRpcManager.fetchCurrentUser(token)
                }
                if (user == null) {
                    onResult(false)
                    return@launchIO
                }
                val account = DiscordAccount(
                    id = user.id,
                    username = user.username,
                    avatarUrl = user.avatar,
                    token = token,
                    isActive = true,
                )
                connectionsManager.discord.addAccount(account)
                connectionsPreferences.connectionsToken(connectionsManager.discord).set(token)
                connectionsPreferences.setConnectionsCredentials(connectionsManager.discord, "Discord", "Logged In")
                DiscordTokenStore.store(token)
                if (connectionsPreferences.enableDiscordRPC().get()) {
                    DiscordRPCService.start(context.applicationContext)
                }
                onResult(true)
            }
        }
    }
}
