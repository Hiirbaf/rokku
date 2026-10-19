package yokai.core.migration.migrations

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import yokai.core.migration.Migration
import yokai.core.migration.MigrationContext
import yokai.domain.extension.repo.model.ContentWarning

class ContentWarningMigration : Migration {
    override val version: Float = 160f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val preferences = migrationContext.get<PreferencesHelper>() ?: return false

        val oldPref = preferences.showNsfwSources()
        if (oldPref.isSet() && !oldPref.get()) {
            preferences.enabledContentWarnings().set(
                setOf(ContentWarning.SAFE.name, ContentWarning.MIXED.name),
            )
        }
        oldPref.delete()

        return true
    }
}
