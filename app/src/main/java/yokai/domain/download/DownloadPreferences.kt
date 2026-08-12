package yokai.domain.download

import eu.kanade.tachiyomi.core.preference.PreferenceStore

class DownloadPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun downloadWithId() = preferenceStore.getBoolean("download_with_id", false)

    fun parallelSourceLimit() = preferenceStore.getInt("download_parallel_source_limit", 5)

    fun parallelPageLimit() = preferenceStore.getInt("download_parallel_page_limit", 2)

    /** Megabytes of free space below which the queue pauses itself. Zero disables the check. */
    fun pauseBelowFreeSpaceMb() = preferenceStore.getInt("pause_download_below_free_space_mb", 0)
}
