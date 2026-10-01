package eu.kanade.tachiyomi.ui.setting.controllers

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import coil3.compose.AsyncImage
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.data.connections.discord.DiscordAccount
import eu.kanade.tachiyomi.ui.setting.connections.DiscordLoginActivity
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.connections.service.ConnectionsPreferences
import yokai.i18n.MR

data class DiscordAccountsScreenState(
    val accounts: List<DiscordAccount> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@Composable
fun DiscordAccountsDialog(
    onDismiss: () -> Unit,
    onAccountChanged: () -> Unit,
) {
    val context = LocalContext.current
    val screenModel = remember { DiscordAccountsScreenModel() }
    val state by screenModel.state.collectAsState()

    val noAccountsFoundString = stringResource(MR.strings.no_accounts_found)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            screenModel.refreshAccounts()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 20.dp,
                            end = 8.dp,
                            top = 12.dp,
                            bottom = 8.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(MR.strings.discord_accounts),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )

                    IconButton(
                        onClick = {
                            launcher.launch(
                                Intent(
                                    context,
                                    DiscordLoginActivity::class.java,
                                ),
                            )
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(MR.strings.add),
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                        )
                    }
                }

                when {
                    state.isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    state.error != null -> {
                        Text(
                            text = state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(20.dp),
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = 16.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.accounts) { account ->
                                DiscordAccountItem(
                                    account = account,
                                    onRemove = {
                                        screenModel.removeAccount(account.id)
                                    },
                                    onSetActive = {
                                        screenModel.setActiveAccount(
                                            account.id,
                                            onSuccess = onAccountChanged,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        screenModel.setNoAccountsFoundString(noAccountsFoundString)
        screenModel.refreshAccounts()
    }
}

class DiscordAccountsScreenModel : StateScreenModel<DiscordAccountsScreenState>(
    DiscordAccountsScreenState(),
) {
    private val discord = Injekt.get<ConnectionsManager>().discord
    private val connectionsPreferences = Injekt.get<ConnectionsPreferences>()
    private val context: Context by injectLazy()
    private var noAccountsFoundString: String = ""

    init {
        screenModelScope.launch {
            connectionsPreferences.discordAccounts().changes()
                .collect { loadAccounts() }
        }

        loadAccounts()
    }

    fun setNoAccountsFoundString(value: String) {
        noAccountsFoundString = value
    }

    private fun loadAccounts() {
        screenModelScope.launch {
            mutableState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                )
            }

            runCatching {
                val accounts = discord.getAccounts()

                if (accounts.isEmpty()) {
                    mutableState.update {
                        it.copy(
                            accounts = emptyList(),
                            isLoading = false,
                            error = noAccountsFoundString,
                        )
                    }
                } else {
                    mutableState.update {
                        it.copy(
                            accounts = accounts,
                            isLoading = false,
                        )
                    }
                }
            }.onFailure { e ->
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: context.getString(MR.strings.unknown_error)
                    )
                }
            }
        }
    }

    fun removeAccount(accountId: String) {
        screenModelScope.launch {
            mutableState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                )
            }

            runCatching {
                discord.removeAccount(accountId)
                loadAccounts()
            }.onFailure { e ->
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: context.getString(MR.strings.unknown_error)
                    )
                }
            }
        }
    }

    fun setActiveAccount(
        accountId: String,
        onSuccess: () -> Unit,
    ) {
        screenModelScope.launch {
            mutableState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                )
            }

            runCatching {
                discord.setActiveAccount(accountId)
                loadAccounts()
            }.onSuccess {
                onSuccess()
            }.onFailure { e ->
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: context.getString(MR.strings.unknown_error)
                    )
                }
            }
        }
    }

    fun refreshAccounts() {
        loadAccounts()
    }
}

@Composable
private fun DiscordAccountItem(
    account: DiscordAccount,
    onRemove: () -> Unit,
    onSetActive: () -> Unit,
) {
    val avatarUrl = account.avatarUrl
        ?: "https://cdn.discordapp.com/embed/avatars/${(account.id.toLong() shr 22) % 6}.png"

    Card(
        onClick = onSetActive,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = account.username,
                    style = MaterialTheme.typography.titleMedium,
                )

                if (account.isActive) {
                    Text(
                        text = stringResource(MR.strings.active_account),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (!account.isActive) {
                IconButton(
                    onClick = onRemove,
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(MR.strings.delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
