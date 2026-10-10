package eu.kanade.tachiyomi.ui.setting.controllers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.storage.preference.collectAsState
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.data.connections.discord.DiscordAccount
import eu.kanade.tachiyomi.data.connections.discord.DiscordRpcManager
import eu.kanade.tachiyomi.data.connections.discord.DiscordTokenStore
import yokai.domain.category.interactor.GetCategories
import yokai.domain.connections.service.ConnectionsPreferences
import yokai.domain.connections.service.DiscordButton
import yokai.domain.connections.service.DiscordProgressMode
import yokai.presentation.component.preference.Preference
import yokai.presentation.component.preference.PreferenceItem
import yokai.presentation.settings.ComposableSettings
import yokai.i18n.MR
import android.R as AR

object SettingsDiscordScreen : ComposableSettings() {

    private var showDiscordStatusDialog by mutableStateOf(false)

    fun requestDiscordStatusDialog() {
        showDiscordStatusDialog = true
    }

    @Composable
    private fun PreferenceGroupCard(group: Preference.PreferenceGroup) {
        AnimatedVisibility(
            visible = group.enabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    group.preferenceItems.forEach { item ->
                        PreferenceItem(item = item, highlightKey = null)
                    }
                }
            }
        }
    }

    private fun Preference.PreferenceGroup.asCard() =
    Preference.PreferenceItem.CustomPreference(title = "") {
        PreferenceGroupCard(this@asCard)
    }

    @Composable
    fun DiscordStatusDialogHost() {
        val connectionsPreferences = remember { Injekt.get<ConnectionsPreferences>() }
        val discordRPCStatus = connectionsPreferences.discordRPCStatus()
        val status by discordRPCStatus.collectAsState()

        if (showDiscordStatusDialog) {
            DiscordStatusDialog(
                value = status,
                onDismissRequest = { showDiscordStatusDialog = false },
                onValueChange = {
                    discordRPCStatus.set(it)
                    showDiscordStatusDialog = false
                },
            )
        }
    }

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.connections_discord

    @Composable
    override fun getPreferences(): List<Preference> {
        val connectionsPreferences = remember { Injekt.get<ConnectionsPreferences>() }
        val connectionsManager = remember { Injekt.get<ConnectionsManager>() }
        val enableDRPCPref = connectionsPreferences.enableDiscordRPC()
        val useChapterTitlesPref = connectionsPreferences.useChapterTitles()
        val discordRPCStatus = connectionsPreferences.discordRPCStatus()

        var activeAccount by remember {
            mutableStateOf(
                connectionsManager.discord.getAccounts().find { it.isActive },
            )
        }

        val enableDRPC by enableDRPCPref.collectAsState()

        val customMessagePref = connectionsPreferences.discordCustomMessage()
        val showAppIconPref = connectionsPreferences.discordShowAppIcon()
        val uploadLocalCoversPref = connectionsPreferences.discordUploadLocalCovers()
        val progressModePref = connectionsPreferences.discordProgressMode()

        var showCustomMessageDialog by rememberSaveable { mutableStateOf(false) }
        var tempCustomMessage by rememberSaveable { mutableStateOf(customMessagePref.get()) }
        var showDiscordSettings by rememberSaveable { mutableStateOf(false) }

        if (showCustomMessageDialog) {
            AlertDialog(
                onDismissRequest = {
                    showCustomMessageDialog = false
                    tempCustomMessage = customMessagePref.get()
                },
                title = { Text(stringResource(MR.strings.pref_discord_custom_message)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = tempCustomMessage,
                            onValueChange = { tempCustomMessage = it },
                            label = { Text(stringResource(MR.strings.pref_discord_custom_message_summary)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        TextButton(
                            onClick = {
                                customMessagePref.delete()
                                tempCustomMessage = ""
                            },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(stringResource(MR.strings.reset))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        customMessagePref.set(tempCustomMessage)
                        showCustomMessageDialog = false
                    }) {
                        Text(stringResource(AR.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showCustomMessageDialog = false
                        tempCustomMessage = customMessagePref.get()
                    }) {
                        Text(stringResource(AR.string.cancel))
                    }
                },
            )
        }

        var dialog by remember { mutableStateOf<Any?>(null) }
        dialog?.run {
            when (this) {
                is LogoutConnectionsDialog -> {
                    ConnectionsLogoutDialog(
                        service = service,
                        onDismissRequest = {
                            dialog = null
                            enableDRPCPref.set(false)
                        },
                    )
                }
            }
        }

        return listOf(
            Preference.PreferenceItem.CustomPreference(
                title = "",
            ) {
                DiscordAccountRow(
                    account = activeAccount!!,
                    showConnected = DiscordRpcManager.connectionStatus.collectAsState()
                    onLogout = {
                        dialog = LogoutConnectionsDialog(connectionsManager.discord)
                    },
                    onSettings = {
                        showDiscordSettings = true
                    },
                )
                if (showDiscordSettings) {
                    DiscordAccountsDialog(
                        onDismiss = {
                            showDiscordSettings = false
                        },
                        onAccountChanged = {
                            activeAccount = connectionsManager.discord
                                .getAccounts()
                                .find { it.isActive }
                        },
                    )
                }
            },
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.general),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = enableDRPCPref,
                        title = stringResource(MR.strings.pref_enable_discord_rpc),
                    ),
                ),
            ).asCard(),
            getRPCIncognitoGroup(
                connectionsPreferences = connectionsPreferences,
                enabled = enableDRPC,
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_category_discord_presence),
                enabled = enableDRPC,
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = showAppIconPref,
                        title = stringResource(MR.strings.pref_discord_show_app_icon),
                        subtitle = stringResource(MR.strings.pref_discord_show_app_icon_summary),
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.pref_discord_custom_message),
                        subtitle = stringResource(MR.strings.pref_discord_custom_message_summary),
                        onClick = { showCustomMessageDialog = true },
                    ),
                    Preference.PreferenceItem.ListPreference(
                        pref = discordRPCStatus,
                        title = stringResource(MR.strings.pref_discord_status),
                        entries = persistentMapOf(
                            -1 to stringResource(MR.strings.pref_discord_dnd),
                            0 to stringResource(MR.strings.pref_discord_idle),
                            1 to stringResource(MR.strings.pref_discord_online),
                        ),
                        enabled = enableDRPC,
                    ),
                ),
            ).asCard(),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_category_discord_customization),
                enabled = enableDRPC,
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = uploadLocalCoversPref,
                        title = stringResource(MR.strings.pref_discord_upload_local_covers),
                        subtitle = stringResource(MR.strings.pref_discord_upload_local_covers_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        pref = useChapterTitlesPref,
                        enabled = enableDRPC,
                        title = stringResource(MR.strings.show_chapters_titles_title),
                        subtitle = stringResource(MR.strings.show_chapters_titles_subtitle),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        pref = progressModePref,
                        title = stringResource(MR.strings.pref_discord_show_progress),
                        entries = persistentMapOf(
                            DiscordProgressMode.OFF to stringResource(MR.strings.pref_discord_progress_off),
                            DiscordProgressMode.PAGES to stringResource(MR.strings.pref_discord_progress_pages),
                            DiscordProgressMode.CHAPTERS to stringResource(MR.strings.pref_discord_progress_chapters),
                        ),
                    ),
                ),
            ).asCard(),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_category_discord_buttons),
                enabled = enableDRPC,
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.MultiSelectListPreference(
                        pref = connectionsPreferences.discordButtons(),
                        title = stringResource(MR.strings.pref_discord_buttons),
                        subtitle = stringResource(MR.strings.pref_buttons_selected) + "\n\n" +
                            stringResource(MR.strings.pref_discord_buttons_summary),
                        entries = persistentMapOf(
                            DiscordButton.MANGA to stringResource(MR.strings.pref_discord_button_manga),
                            DiscordButton.DOWNLOAD to stringResource(MR.strings.download_app),
                        ),
                    ),
                ),
            ).asCard(),
        )
    }

    @Composable
    private fun getRPCIncognitoGroup(
        connectionsPreferences: ConnectionsPreferences,
        enabled: Boolean,
    ): Preference.PreferenceGroup {
        val getCategories = remember { Injekt.get<GetCategories>() }
        val allCategories by getCategories.subscribe().collectAsState(initial = runBlocking { getCategories.await() })

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_discord_privacy),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = connectionsPreferences.discordRPCIncognito(),
                    title = stringResource(MR.strings.pref_discord_incognito),
                    subtitle = stringResource(MR.strings.pref_discord_incognito_summary),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    pref = connectionsPreferences.discordRPCIncognitoCategories(),
                    title = stringResource(MR.strings.categories),
                    subtitle = stringResource(MR.strings.pref_buttons_selected) + "\n\n" +
                        stringResource(MR.strings.pref_discord_incognito_categories_details),
                    entries = allCategories.associate { it.id.toString() to it.name }.toImmutableMap(),
                ),
            ),
            enabled = enabled,
        )
    }

    @Composable
    private fun DiscordAccountRow(
        account: DiscordAccount,
        showConnected: Boolean,
        onLogout: () -> Unit,
        onSettings: () -> Unit,
    ) {
        val status by DiscordRpcManager.connectionStatus.collectAsState()
        val isConnected = showConnected && status == DiscordRpcManager.Status.Connected
        val avatarUrl = account?.let {
            it.avatarUrl ?: "https://cdn.discordapp.com/embed/avatars/${(it.id.toLong() shr 22) % 6}.png"
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = account.name
                        ?.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
                        ?: account.username,
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Text(
                        text = "@${account.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (isConnected) {
                        Text(
                            text = stringResource(MR.strings.connected),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }

                Row(
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    IconButton(
                        onClick = onSettings,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(MR.strings.settings),
                        )
                    }

                    IconButton(
                        onClick = onLogout,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = stringResource(MR.strings.log_out),
                            tint = colorResource(R.color.holo_red),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun DiscordStatusDialog(
        value: Int,
        onDismissRequest: () -> Unit,
        onValueChange: (Int) -> Unit,
    ) {
        val entries = persistentMapOf(
            -1 to stringResource(MR.strings.pref_discord_dnd),
            0 to stringResource(MR.strings.pref_discord_idle),
            1 to stringResource(MR.strings.pref_discord_online),
        )

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = stringResource(MR.strings.pref_discord_status)) },
            text = {
                Column {
                    entries.forEach { current ->
                        DiscordStatusDialogRow(
                            label = current.value,
                            isSelected = value == current.key,
                            onSelected = { onValueChange(current.key) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.cancel))
                }
            },
        )
    }

    @Composable
    private fun DiscordStatusDialogRow(
        label: String,
        isSelected: Boolean,
        onSelected: () -> Unit,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .selectable(
                    selected = isSelected,
                    onClick = { if (!isSelected) onSelected() },
                )
                .fillMaxWidth()
                .minimumInteractiveComponentSize(),
        ) {
            RadioButton(selected = isSelected, onClick = null)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.merge(),
                modifier = Modifier.padding(start = 24.dp),
            )
        }
    }
}
