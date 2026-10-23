package yokai.presentation.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import dev.icerock.moko.resources.compose.stringResource
import yokai.domain.base.BasePreferences.AppIcons
import yokai.i18n.MR

private const val PREVIEW_SIZE_PX = 288

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIconDialog(
    selected: AppIcons,
    followsTheme: Boolean,
    themeIcon: AppIcons,
    onApply: (icon: AppIcons, followsTheme: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var icon by remember { mutableStateOf(selected) }
    var follows by remember { mutableStateOf(followsTheme) }
    var expanded by remember { mutableStateOf(false) }
    val shownIcon = if (follows) themeIcon else icon

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val betaStyle = SpanStyle(
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                baselineShift = BaselineShift.Superscript,
            )
            val title = stringResource(MR.strings.app_icon)
            val beta = stringResource(MR.strings.beta)
            Text(
                text = buildAnnotatedString {
                    append(title)
                    append(" ")
                    withStyle(betaStyle) { append(beta) }
                },
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AppIconPreview(shownIcon)

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = stringResource(shownIcon.nameRes),
                        onValueChange = {},
                        readOnly = true,
                        enabled = !follows,
                        label = { Text(text = stringResource(MR.strings.app_icon_color)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !follows)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        AppIcons.entries.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(text = stringResource(entry.nameRes)) },
                                onClick = {
                                    icon = entry
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { follows = !follows },
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(MR.strings.app_icon_match_theme),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(MR.strings.app_icon_match_theme_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = follows, onCheckedChange = null)
                }

                Text(
                    text = stringResource(MR.strings.app_icon_experimental_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(shownIcon, follows) }) {
                Text(text = stringResource(MR.strings.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(MR.strings.cancel))
            }
        },
    )
}

@Composable
private fun AppIconPreview(icon: AppIcons) {
    val context = LocalContext.current
    val bitmap = remember(icon) {
        (ResourcesCompat.getDrawable(context.resources, icon.icon, context.theme) as? AdaptiveIconDrawable)
            ?.render(PREVIEW_SIZE_PX)
            ?.asImageBitmap()
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
    }
}

// Drawing the AdaptiveIconDrawable itself can pick its monochrome layer on newer Android versions,
// so the layers are composed by hand
private fun AdaptiveIconDrawable.render(sizePx: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val circle = Path().apply { addCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, Path.Direction.CW) }
    canvas.clipPath(circle)
    // Layers are 108dp and only the central 72dp is visible
    val inset = sizePx / 4
    listOfNotNull(background, foreground).forEach {
        it.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
        it.draw(canvas)
    }
    return bitmap
}
