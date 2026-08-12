package yokai.presentation.extension.repo.component

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextFieldDefaults.indicatorLine
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.util.compose.textHint
import yokai.domain.extension.repo.model.ExtensionRepo
import yokai.presentation.component.Gap
import yokai.presentation.theme.Size

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExtensionRepoItem(
    modifier: Modifier = Modifier,
    extensionRepo: ExtensionRepo,
    onDeleteClick: (String) -> Unit = {},
    onEditClick: (String, String) -> Unit = { _, _ -> },
    onCopyUrl: (String) -> Unit = {},
) {
    var isEditing by remember(extensionRepo.baseUrl) { mutableStateOf(false) }
    var editedUrl by remember(extensionRepo.baseUrl) { mutableStateOf(extensionRepo.baseUrl) }

    fun submitEdit() {
        val newUrl = editedUrl.trim()
        if (newUrl.isEmpty() || newUrl == extensionRepo.baseUrl) {
            isEditing = false
            return
        }
        onEditClick(extensionRepo.baseUrl, newUrl)
    }

    fun cancelEdit() {
        editedUrl = extensionRepo.baseUrl
        isEditing = false
    }

    Row(
        modifier = modifier
            .combinedClickable(
                onClick = {},
                onLongClick = { onCopyUrl(extensionRepo.baseUrl) },
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.padding(horizontal = 8.dp),
            imageVector = Icons.AutoMirrored.Outlined.Label,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
        )
        Column(
            modifier = modifier.weight(1.0f),
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
                text = extensionRepo.name,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
            )
            Gap(Size.tiny)
            if (isEditing) {
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = editedUrl,
                    onValueChange = { editedUrl = it },
                    textStyle = TextStyle(fontSize = 16.sp),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(),
                )
            } else {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(),
                    text = extensionRepo.baseUrl,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                )
            }
        }
        if (isEditing) {
            IconButton(onClick = { cancelEdit() }) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            IconButton(onClick = { submitEdit() }) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        } else {
            IconButton(onClick = { isEditing = true }) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            IconButton(onClick = { onDeleteClick(extensionRepo.baseUrl) }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
fun ExtensionRepoInput(
    inputHint: String,
    modifier: Modifier = Modifier,
    inputText: String = "",
    onInputChange: (String) -> Unit = {},
    onAddClick: (String) -> Unit = {},
    isLoading: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val colors = TextFieldDefaults.colors().copy(
        cursorColor = MaterialTheme.colorScheme.secondary,
        focusedPlaceholderColor = MaterialTheme.colorScheme.textHint,
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.textHint,
        errorPlaceholderColor = MaterialTheme.colorScheme.textHint,
        focusedTextColor = MaterialTheme.colorScheme.onBackground,
        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
        errorTextColor = MaterialTheme.colorScheme.onBackground,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )
    Row(
        modifier = modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.padding(horizontal = 8.dp),
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
        )
        TextField(
            modifier = Modifier
                .indicatorLine(
                    enabled = false,
                    colors = colors,
                    interactionSource = interactionSource,
                    isError = true,
                )
                .weight(1.0f),
            value = inputText,
            onValueChange = onInputChange,
            enabled = !isLoading,
            placeholder = { Text(text = inputHint, fontSize = 16.sp) },
            textStyle = TextStyle(fontSize = 16.sp),
            colors = colors,
        )
        IconButton(
            onClick = { onAddClick(inputText) },
            enabled = inputText.isNotEmpty(),
        ) {
            if (!isLoading) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
            } else {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
fun ExtensionRepoItemPreview() {
    val input = "https://raw.githubusercontent.com/null2264/totally-real-extensions/repo/index.min.json"
    Surface {
        Column {
            ExtensionRepoItem(extensionRepo = ExtensionRepo("", "", "", "", ""))
            ExtensionRepoInput(inputHint = "Input")
            ExtensionRepoInput(inputHint = "", inputText = input)
            ExtensionRepoInput(inputHint = "", inputText = input, isLoading = true)
        }
    }
}
