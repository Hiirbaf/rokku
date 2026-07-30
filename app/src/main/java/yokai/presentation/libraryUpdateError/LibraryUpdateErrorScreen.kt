package yokai.presentation.libraryUpdateError

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.ui.migration.manga.design.PreMigrationController
import eu.kanade.tachiyomi.util.compose.LocalBackPress
import eu.kanade.tachiyomi.util.compose.LocalRouter
import eu.kanade.tachiyomi.util.compose.currentOrThrow
import eu.kanade.tachiyomi.util.isTablet
import yokai.i18n.MR
import yokai.presentation.AppBarType
import yokai.presentation.YokaiScaffold
import yokai.presentation.component.EmptyScreen
import yokai.util.Screen

class LibraryUpdateErrorScreen : Screen() {
    @Composable
    override fun Content() {
        val onBackPress = LocalBackPress.currentOrThrow
        val router = LocalRouter.currentOrThrow

        val screenModel = rememberScreenModel { LibraryUpdateErrorScreenModel() }
        val state by screenModel.state.collectAsState()
        val selectedCount = state.selected.size

        YokaiScaffold(
            onNavigationIconClicked = onBackPress,
            title = stringResource(MR.strings.view_errors),
            appBarType = AppBarType.SMALL,
            actions = {
                if (state.items.isNotEmpty()) {
                    TextButton(
                        onClick = { screenModel.toggleAllSelection(selectedCount != state.items.size) },
                    ) {
                        Text(
                            text = stringResource(
                                if (selectedCount == state.items.size) MR.strings.clear_selection else MR.strings.select_all,
                            ),
                        )
                    }
                }
            },
        ) { innerPadding ->
            if (state.isLoading) return@YokaiScaffold

            if (state.items.isEmpty()) {
                EmptyScreen(
                    modifier = Modifier.padding(innerPadding),
                    image = Icons.Filled.CheckCircle,
                    message = stringResource(MR.strings.information_no_library_update_errors),
                    isTablet = isTablet(),
                )
                return@YokaiScaffold
            }

            Column(modifier = Modifier.padding(innerPadding)) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.items, key = { it.error.errorId }) { item ->
                        LibraryUpdateErrorRow(
                            item = item,
                            onToggle = { screenModel.toggleSelection(item, !item.selected) },
                        )
                    }
                }

                if (selectedCount > 0) {
                    Surface(tonalElevation = 3.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = { screenModel.deleteSelected() },
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                                Text(text = stringResource(MR.strings.delete))
                            }
                            TextButton(
                                onClick = {
                                    val mangaIds = screenModel.getSelectedMangaIds()
                                    if (mangaIds.isNotEmpty()) {
                                        PreMigrationController.navigateToMigration(
                                            skipPre = false,
                                            router = router,
                                            mangaIds = mangaIds,
                                        )
                                    }
                                },
                            ) {
                                Text(text = stringResource(MR.strings.migrate))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryUpdateErrorRow(
    item: LibraryUpdateErrorItem,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.selected, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = item.error.mangaTitle,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.sourceName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.error.message.isNullOrBlank()) {
                Text(
                    text = item.error.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
