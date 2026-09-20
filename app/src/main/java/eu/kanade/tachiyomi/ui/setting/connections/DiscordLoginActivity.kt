package eu.kanade.tachiyomi.ui.setting.connections

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import yokai.domain.connections.service.ConnectionsPreferences
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.data.connections.discord.DiscordAccount
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordRpcManager
import eu.kanade.tachiyomi.data.connections.discord.DiscordTokenStore
import eu.kanade.tachiyomi.data.connections.discord.DiscordUser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yokai.i18n.MR
import uy.kohesive.injekt.injectLazy

class DiscordLoginActivity : AppCompatActivity() {

    private val connectionsManager: ConnectionsManager by injectLazy()
    private val connectionsPreferences: ConnectionsPreferences by injectLazy()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "DiscordLogin"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!DiscordRpcManager.isInitialized()) {
            DiscordRpcManager.init(applicationContext)
        }

        Log.i(TAG, "Starting Discord OAuth PKCE authorization flow")

        DiscordRpcManager.authorize { success ->
            if (success) {
                scope.launch {
                    val token = DiscordRpcManager.getAccessToken()
                    if (token != null) {
                        val user = withContext(Dispatchers.IO) {
                            DiscordRpcManager.fetchCurrentUser(token)
                        }
                        if (user != null) {
                            saveAccount(user, token)
                            handleLoginSuccess()
                        } else {
                            handleLoginError("Failed to fetch Discord user info")
                        }
                    } else {
                        handleLoginError("Authorization succeeded but no token received")
                    }
                }
            } else {
                handleLoginError("Discord authorization failed or was cancelled")
            }
        }
    }

    private fun saveAccount(user: DiscordUser, token: String) {
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
    }

    private fun handleLoginSuccess() {
        toast(MR.strings.successfully_logged_in)
        setResult(RESULT_OK)
        if (connectionsPreferences.enableDiscordRPC().get()) {
            DiscordRPCService.start(applicationContext)
        }
        finish()
    }

    private fun handleLoginError(message: String) {
        toast(message)
        setResult(RESULT_CANCELED)
        finish()
    }
}
