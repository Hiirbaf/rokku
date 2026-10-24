package yokai.core.migration.migrations

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.extension.model.ContentWarning
import yokai.core.migration.Migration
import yokai.core.migration.MigrationContext

class ContentWarningMigration : Migration {
    override val version: Float = 160f // pon el versionCode de la build que lo publica

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val oldKey = "show_nsfw_source"
        prefs.edit {
            if (prefs.contains(oldKey) && !prefs.getBoolean(oldKey, true)) {
                putStringSet(
                    "enabled_content_warnings",
                    setOf(ContentWarning.SAFE.name, ContentWarning.MIXED.name),
                )
            }
            remove(oldKey)
        }
        return true
    }
}
