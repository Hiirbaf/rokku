package yokai.core.migration.migrations

import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.extension.model.ContentWarning
import yokai.core.migration.Migration
import yokai.core.migration.MigrationContext

class ContentWarningMigration : Migration {
    override val version: Float = 161f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return false
        convertLegacyNsfwPreference(preferenceStore)
        return true
    }
}

private const val LEGACY_SHOW_NSFW_SOURCE = "show_nsfw_source"

// Also called after restoring app preferences: a backup from an older version brings the legacy key back.
fun convertLegacyNsfwPreference(preferenceStore: PreferenceStore) {
    val legacy = preferenceStore.getBoolean(LEGACY_SHOW_NSFW_SOURCE, true)
    if (!legacy.isSet()) return

    val enabled = preferenceStore.getStringSet(PreferenceKeys.enabledContentWarnings)
    if (!legacy.get() && !enabled.isSet()) {
        enabled.set(setOf(ContentWarning.SAFE.name, ContentWarning.MIXED.name))
    }
    legacy.delete()
}
