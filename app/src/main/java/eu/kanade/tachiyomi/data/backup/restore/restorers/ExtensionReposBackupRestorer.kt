package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.extension.repo.ExtensionRepoRepository

class ExtensionReposBackupRestorer(
    private val extensionRepoRepository: ExtensionRepoRepository = Injekt.get(),
) {
    suspend fun restoreExtensionRepos(backupExtensionRepos: List<BackupExtensionRepo>) {
        backupExtensionRepos.forEach {
            // upsertRepository() only resolves conflicts on base_url (its primary key), but
            // signing_key_fingerprint is unique too, so restoring a repo whose fingerprint
            // already exists under a different base_url throws SQLiteConstraintException.
            // replaceRepository() resolves conflicts on the fingerprint instead, which is what
            // we want here: the fingerprint is the repo's trust anchor, and a backup should win
            // over whatever base_url a same-fingerprint repo is currently registered under.
            extensionRepoRepository.replaceRepository(it.getExtensionRepo())
        }
    }
}
