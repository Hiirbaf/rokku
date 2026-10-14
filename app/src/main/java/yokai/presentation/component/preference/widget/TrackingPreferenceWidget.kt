package yokai.presentation.component.preference.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.core.storage.preference.collectAsState
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.RefreshResult
import eu.kanade.tachiyomi.data.track.TrackPreferences
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy
import yokai.i18n.MR
import yokai.presentation.component.TrackLogoIcon
import yokai.presentation.component.preference.LocalPreferenceHighlighted

@Composable
fun TrackingPreferenceWidget(
    modifier: Modifier = Modifier,
    tracker: TrackService,
    checked: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val trackPreferences: TrackPreferences by injectLazy()

    val username by trackPreferences.trackUsername(tracker).collectAsState()
    val displayName by trackPreferences.trackDisplayUsername(tracker).collectAsState()
    val shownName = displayName.ifBlank { username }
    val isRefreshing by tracker.isRefreshingFlow.collectAsState()

    LaunchedEffect(tracker) {
        tracker.refreshResultFlow.collect { result ->
            if (result is RefreshResult.Error) {
                context.toast(result.error.message.toString())
            }
        }
    }

    val highlighted = LocalPreferenceHighlighted.current
    Box(modifier = Modifier.highlightBackground(highlighted)) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = PrefsHorizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = onClick != null, onClick = { onClick?.invoke() }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackLogoIcon(tracker)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(tracker.nameRes()),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        maxLines = 1,
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = TitleFontSize,
                    )
                    if (checked && shownName.isNotBlank() && tracker !is EnhancedTrackService) {
                        Text(
                            text = shownName,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (checked) {
                if (tracker !is EnhancedTrackService) {
                    IconButton(
                        enabled = !isRefreshing,
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                tracker.refreshUser()
                            }
                        },
                    ) {
                        if (!isRefreshing) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(32.dp),
                                contentDescription = null,
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
                Icon(
                    imageVector = Icons.Outlined.Done,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(32.dp),
                    tint = Color(0xFF4CAF50),
                    contentDescription = stringResource(MR.strings.successfully_logged_in),
                )
            }
        }
    }
}
