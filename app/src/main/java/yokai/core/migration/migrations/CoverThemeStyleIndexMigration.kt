package yokai.core.migration.migrations

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import yokai.core.migration.Migration
import yokai.core.migration.MigrationContext

class CoverThemeStyleIndexMigration : Migration {
    override val version: Float = 158f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val preferencesHelper = migrationContext.get<PreferencesHelper>() ?: return false
        val coverThemeStyle = preferencesHelper.coverThemeStyle()

        if (coverThemeStyle.isSet()) {
            coverThemeStyle.set(coverThemeStyle.get() + 1)
        }

        return true
    }
}
