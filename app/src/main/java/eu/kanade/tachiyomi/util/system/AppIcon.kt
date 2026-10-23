package eu.kanade.tachiyomi.util.system

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import yokai.domain.base.BasePreferences

fun Context.setAppIcon(basePreferences: BasePreferences, selectedIcon: BasePreferences.AppIcons) {
    val dummy = ComponentName(this, "${BuildConfig.APPLICATION_ID}.MainActivityDefaultDummy")

    try {
        applyComponentStates(
            enabled = ComponentName(this, selectedIcon.id),
            reset = BasePreferences.AppIcons.entries.filter { it != selectedIcon }.map { ComponentName(this, it.id) },
            disabled = dummy,
        )
        basePreferences.appIcon().set(selectedIcon)
    } catch (e: Exception) {
        Logger.e(e) { "Failed to set app icon ${selectedIcon.name}" }
        packageManager.setComponentEnabledSetting(
            dummy,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}

private fun Context.applyComponentStates(enabled: ComponentName, reset: List<ComponentName>, disabled: ComponentName) {
    val enabledState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    val resetState = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    val disabledState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    val flags = PackageManager.DONT_KILL_APP

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        // A single batch sends one package-changed broadcast, so the launcher never shows the old and new icon together
        packageManager.setComponentEnabledSettings(
            listOf(PackageManager.ComponentEnabledSetting(enabled, enabledState, flags)) +
                reset.map { PackageManager.ComponentEnabledSetting(it, resetState, flags) } +
                PackageManager.ComponentEnabledSetting(disabled, disabledState, flags),
        )
    } else {
        // Enabling the new alias first keeps the app launchable while the others are switched off
        packageManager.setComponentEnabledSetting(enabled, enabledState, flags)
        reset.forEach { packageManager.setComponentEnabledSetting(it, resetState, flags) }
        packageManager.setComponentEnabledSetting(disabled, disabledState, flags)
    }
}

fun Themes.matchingAppIcon(): BasePreferences.AppIcons = when (this) {
    Themes.SPRING_AND_DUSK -> BasePreferences.AppIcons.PINK
    Themes.STRAWBERRIES -> BasePreferences.AppIcons.RED
    Themes.TEAL_AND_SAPPHIRE -> BasePreferences.AppIcons.TEAL
    Themes.LAVENDER -> BasePreferences.AppIcons.PURPLE
    Themes.TAKO -> BasePreferences.AppIcons.LILAC
    Themes.YIN_AND_YANG -> BasePreferences.AppIcons.WHITE
    Themes.YOTSUBA -> BasePreferences.AppIcons.ORANGE
    Themes.DOKI -> BasePreferences.AppIcons.GOLD
    Themes.MONET, Themes.DEFAULT, Themes.LIME -> BasePreferences.AppIcons.DEFAULT
}

fun Context.syncAppIconWithTheme(preferences: PreferencesHelper, basePreferences: BasePreferences) {
    if (!basePreferences.appIconFollowsTheme().get()) return
    val target = getPrefTheme(preferences).matchingAppIcon()
    if (target != basePreferences.appIcon().get()) setAppIcon(basePreferences, target)
}
