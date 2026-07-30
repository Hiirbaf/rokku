package yokai.domain.libraryUpdateError.model

data class LibraryUpdateErrorWithManga(
    val errorId: Long,
    val mangaId: Long,
    val message: String?,
    val date: Long,
    val mangaTitle: String,
    val mangaThumbnailUrl: String?,
    val mangaSource: Long,
)
