package eu.kanade.tachiyomi.util.system

import com.materialkolor.PaletteStyle
import dev.icerock.moko.resources.StringResource
import yokai.i18n.MR

// null represents "Legacy" (the old behavior, without material-color)
val coverThemeOptions: List<PaletteStyle?> = listOf(null) + PaletteStyle.entries

fun PaletteStyle?.labelRes(): StringResource = when (this) {
    null -> MR.strings.legacy_theme
    PaletteStyle.TonalSpot -> MR.strings.palette_style_tonal_spot
    PaletteStyle.Neutral -> MR.strings.palette_style_neutral
    PaletteStyle.Vibrant -> MR.strings.palette_style_vibrant
    PaletteStyle.Expressive -> MR.strings.palette_style_expressive
    PaletteStyle.Rainbow -> MR.strings.palette_style_rainbow
    PaletteStyle.FruitSalad -> MR.strings.palette_style_fruit_salad
    PaletteStyle.Monochrome -> MR.strings.palette_style_monochrome
    PaletteStyle.Fidelity -> MR.strings.palette_style_fidelity
    PaletteStyle.Content -> MR.strings.palette_style_content
}
