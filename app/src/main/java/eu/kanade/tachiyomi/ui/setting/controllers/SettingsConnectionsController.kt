package eu.kanade.tachiyomi.ui.setting.controllers

import eu.kanade.tachiyomi.ui.setting.SettingsComposeController
import eu.kanade.tachiyomi.ui.setting.controllers.SettingsConnectionsScreen
import yokai.presentation.settings.ComposableSettings

class SettingsConnectionsController : SettingsComposeController() {
    override fun getComposableSettings(): ComposableSettings {
        return SettingsConnectionsScreen
    }
}
