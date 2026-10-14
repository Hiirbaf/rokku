package eu.kanade.tachiyomi.data.track.bangumi.dto

import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BGMSearchResult(
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
    val data: List<BGMSubject> = emptyList(),
)

@Serializable
data class BGMSubject(
    val id: Long,
    @SerialName("name_cn")
    val nameCn: String,
    val name: String,
    val summary: String?,
    val date: String?,
    val images: BGMSubjectImages?,
    val volumes: Long = 0,
    val eps: Long = 0,
    val rating: BGMSubjectRating?,
    val platform: String?,
) {
    fun toTrackSearch(trackId: Long): TrackSearch = TrackSearch.create(trackId).apply {
        media_id = this@BGMSubject.id
        title = nameCn.ifBlank { name }
        cover_url = images?.common.orEmpty()
        summary = if (nameCn.isNotBlank()) {
            "作品原名：$name" + this@BGMSubject.summary?.let { "\n${it.trim()}" }.orEmpty()
        } else {
            this@BGMSubject.summary?.trim().orEmpty()
        }
        score = rating?.score?.toFloat() ?: -1f
        tracking_url = "https://bangumi.tv/subject/${this@BGMSubject.id}"
        total_chapters = eps
        start_date = date ?: ""
    }
}

@Serializable
data class BGMSubjectImages(
    val common: String?,
)

@Serializable
data class BGMSubjectRating(
    val score: Double?,
)

@Serializable
data class BGMSearchItem(
    val id: Long,
    @SerialName("name_cn")
    val nameCn: String,
    val name: String,
    val type: Int,
    val summary: String?,
    val images: BGMSearchItemCovers?,
    @SerialName("eps_count")
    val epsCount: Long?,
    val rating: BGMSearchItemRating?,
    val url: String,
) {
    fun toTrackSearch(trackId: Long): TrackSearch = TrackSearch.create(trackId).apply {
        media_id = this@BGMSearchItem.id
        title = nameCn.ifBlank { name }
        cover_url = images?.common.orEmpty()
        summary = if (nameCn.isNotBlank()) {
            "作品原名：$name" + this@BGMSearchItem.summary?.let { "\n$it" }.orEmpty()
        } else {
            this@BGMSearchItem.summary.orEmpty()
        }
        score = rating?.score?.toFloat() ?: -1.0F
        tracking_url = url
        total_chapters = epsCount ?: 0
    }
}

@Serializable
data class BGMSearchItemCovers(
    val common: String?,
)

@Serializable
data class BGMSearchItemRating(
    val score: Double?,
)
