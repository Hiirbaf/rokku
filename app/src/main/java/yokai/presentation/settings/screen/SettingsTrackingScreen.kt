package yokai.presentation.settings.screen

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.bangumi.BangumiApi
import eu.kanade.tachiyomi.data.track.hikka.HikkaApi
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBakaApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy
import yokai.i18n.MR
import yokai.presentation.component.LoadingButton
import yokai.presentation.component.preference.Preference
import yokai.presentation.settings.ComposableSettings
import yokai.util.lang.getString
import kotlin.math.ln

object SettingsTrackingScreen : ComposableSettings() {

    private fun readResolve() = SettingsTrackingScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes(): StringResource = MR.strings.tracking

    @Composable
    override fun RowScope.AppBarAction() {
        val uriHandler = LocalUriHandler.current
        IconButton(onClick = { uriHandler.openUri("https://rokku-app.github.io/docs/guides/tracking") }) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                contentDescription = null,
            )
        }
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val preferences: PreferencesHelper by injectLazy()
        val trackManager: TrackManager by injectLazy()
        val sourceManager: SourceManager by injectLazy()

        // Replaces updatePreference()/onActivityResumed from the old controller.
        var refresh by remember { mutableIntStateOf(0) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

        var dialog by remember { mutableStateOf<Any?>(null) }
        when (val d = dialog) {
            is LoginDialog -> TrackingLoginDialog(d.tracker, d.uNameStringRes) {
                dialog = null
                refresh++
            }

            is LogoutDialog -> TrackingLogoutDialog(d.tracker) {
                dialog = null
                refresh++
            }
        }

        fun oauth(service: TrackService, url: () -> Uri) = Preference.PreferenceItem.TrackerPreference(
            tracker = service,
            title = context.getString(service.nameRes()),
            login = { context.openInBrowser(url(), service.getLogoColor(), true) },
            logout = { dialog = LogoutDialog(service) },
        )

        fun credentials(service: TrackService, uname: StringResource) =
            Preference.PreferenceItem.TrackerPreference(
                tracker = service,
                title = context.getString(service.nameRes()),
                login = { dialog = LoginDialog(service, uname) },
                logout = { dialog = LogoutDialog(service) },
            )

        val services = remember(refresh) {
            buildList<Preference.PreferenceItem<out Any>> {
                add(oauth(trackManager.myAnimeList) { MyAnimeListApi.authUrl() })
                add(oauth(trackManager.aniList) { AnilistApi.authUrl() })
                add(credentials(trackManager.kitsu, MR.strings.email))
                add(credentials(trackManager.mangaUpdates, MR.strings.username))
                add(oauth(trackManager.shikimori) { ShikimoriApi.authUrl() })
                add(oauth(trackManager.bangumi) { BangumiApi.authUrl() })
                add(oauth(trackManager.mangaBaka) { MangaBakaApi.authUrl() })
                add(oauth(trackManager.hikka) { HikkaApi.authUrl() })
                add(Preference.PreferenceItem.InfoPreference(context.getString(MR.strings.tracking_info)))
            }.toImmutableList()
        }

        val enhanced = remember(refresh) {
            buildList<Preference.PreferenceItem<out Any>> {
                trackManager.services
                    .filter { s ->
                        s is EnhancedTrackService &&
                            sourceManager.getCatalogueSources().any { s.accept(it) }
                    }
                    .forEach { s ->
                        add(
                            Preference.PreferenceItem.TrackerPreference(
                                tracker = s,
                                title = context.getString(s.nameRes()),
                                login = {
                                    (s as EnhancedTrackService).loginNoop()
                                    refresh++
                                },
                                logout = {
                                    s.logout()
                                    refresh++
                                },
                            ),
                        )
                    }
                add(Preference.PreferenceItem.InfoPreference(context.getString(MR.strings.enhanced_tracking_info)))
            }.toImmutableList()
        }

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                pref = preferences.autoUpdateTrack(),
                title = stringResource(MR.strings.update_tracking_after_reading),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = preferences.trackMarkedAsRead(),
                title = stringResource(MR.strings.update_tracking_marked_read),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.services),
                preferenceItems = services,
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.enhanced_services),
                preferenceItems = enhanced,
            ),
        )
    }

    /**
     * Mirrors the legacy login dialog (pref_account_login.xml): centered title, two outlined
     * fields and a single loading button, with no cancel button.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TrackingLoginDialog(
        tracker: TrackService,
        uNameStringRes: StringResource,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var username by remember { mutableStateOf(tracker.getUsername()) }
        var password by remember { mutableStateOf(tracker.getPassword()) }
        var processing by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        var hidePassword by remember { mutableStateOf(true) }

        fun login() {
            if (username.isBlank() || password.isBlank()) {
                failed = true
                context.toast(MR.strings.username_must_not_be_blank)
                return
            }
            scope.launch {
                processing = true
                failed = false
                try {
                    if (withIOContext { tracker.login(username, password) }) {
                        context.toast(MR.strings.successfully_logged_in)
                        onDismissRequest()
                    } else {
                        tracker.logout()
                        failed = true
                    }
                } catch (e: Exception) {
                    tracker.logout()
                    failed = true
                    e.message?.let { context.toast(it) }
                } finally {
                    processing = false
                }
            }
        }

        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.secondary,
            focusedLabelColor = MaterialTheme.colorScheme.secondary,
            cursorColor = MaterialTheme.colorScheme.secondary,
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )

        LegacyStyledDialog(
            onDismissRequest = onDismissRequest,
            dismissible = !processing,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(MR.strings.log_in_to_, stringResource(tracker.nameRes())),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.headlineSmall,
                )
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 15.dp),
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(text = stringResource(uNameStringRes)) },
                    singleLine = true,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(text = stringResource(MR.strings.password)) },
                    singleLine = true,
                    colors = fieldColors,
                    visualTransformation = if (hidePassword) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    trailingIcon = {
                        IconButton(onClick = { hidePassword = !hidePassword }) {
                            Icon(
                                imageVector = if (hidePassword) {
                                    Icons.Rounded.Visibility
                                } else {
                                    Icons.Rounded.VisibilityOff
                                },
                                contentDescription = null,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val buttonText = stringResource(if (failed) MR.strings.unknown_error else MR.strings.log_in)
                    LoadingButton(
                        text = { buttonText },
                        loading = { processing },
                        onClick = ::login,
                    )
                }
            }
        }
    }

    @Composable
    private fun TrackingLogoutDialog(
        tracker: TrackService,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current

        LegacyStyledDialog(onDismissRequest = onDismissRequest) {
            Column(modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 14.dp)) {
                Text(
                    text = stringResource(MR.strings.log_out_from_, stringResource(tracker.nameRes())),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 30.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(text = stringResource(MR.strings.cancel))
                    }
                    TextButton(
                        onClick = {
                            tracker.logout()
                            onDismissRequest()
                            context.toast(MR.strings.successfully_logged_out)
                        },
                    ) {
                        Text(text = stringResource(MR.strings.log_out))
                    }
                }
            }
        }
    }

    /**
     * Compose counterpart of the legacy MaterialAlertDialog (Theme.Widget.Yokai.AlertDialog):
     * 10dp background inset, 28dp corners, 95% of the width in portrait (65% in landscape) and a
     * colorSurface background with a colorSecondary elevation overlay at the M3 dialog elevation (6dp).
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun LegacyStyledDialog(
        onDismissRequest: () -> Unit,
        dismissible: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        val configuration = LocalConfiguration.current
        val widthFraction = if (configuration.screenWidthDp > configuration.screenHeightDp) 0.65f else 0.95f
        val overlayAlpha = (4.5f * ln(6f + 1f) + 2f) / 100f
        val colors = MaterialTheme.colorScheme

        BasicAlertDialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(
                dismissOnBackPress = dismissible,
                dismissOnClickOutside = dismissible,
                usePlatformDefaultWidth = false,
            ),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .padding(10.dp),
                shape = RoundedCornerShape(28.dp),
                color = colors.secondary.copy(alpha = overlayAlpha).compositeOver(colors.surface),
                contentColor = colors.onSurface,
                content = content,
            )
        }
    }
}

private data class LoginDialog(
    val tracker: TrackService,
    val uNameStringRes: StringResource,
)

private data class LogoutDialog(
    val tracker: TrackService,
)
