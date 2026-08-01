package yokai.domain.library

import eu.kanade.tachiyomi.core.preference.PreferenceStore

class LibraryPreferences(private val preferenceStore: PreferenceStore) {
    fun randomSortSeed() = preferenceStore.getInt("library_random_sort_seed", 0)

    fun markDuplicateReadChapterAsRead() = preferenceStore.getStringSet("mark_duplicate_read_chapter_read", emptySet())

    fun fetchMetadataOnAdd() = preferenceStore.getBoolean("fetch_metadata_on_add", false)
    fun fetchChaptersOnAdd() = preferenceStore.getBoolean("fetch_chapters_on_add", false)

    companion object {
        const val MARK_DUPLICATE_READ_CHAPTER_READ_NEW = "new"
        const val MARK_DUPLICATE_READ_CHAPTER_READ_EXISTING = "existing"
    }
}
