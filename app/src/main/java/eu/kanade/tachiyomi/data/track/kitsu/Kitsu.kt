package eu.kanade.tachiyomi.data.track.kitsu

import android.content.Context
import android.graphics.Color
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuOAuth
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.data.track.updateNewTrackInfo
import eu.kanade.tachiyomi.util.system.e
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.injectLazy
import yokai.i18n.MR
import yokai.util.lang.getString
import java.text.DecimalFormat

class Kitsu(private val context: Context, id: Long) : TrackService(id) {

    private data class RatingSystem(
        val name: String,
        val scoreList: List<String>,
        // Values on the Rokku scale (0–10). Kitsu uses a 2–20 scale, and toApiScore() multiplies the value by 2 upon submission.
        val tenScale: List<Float>,
    )

    companion object {
        const val READING = 1
        const val COMPLETED = 2
        const val ON_HOLD = 3
        const val DROPPED = 4
        const val PLAN_TO_READ = 5

        const val DEFAULT_STATUS = READING
        const val DEFAULT_SCORE = 0f

        const val RATING_SIMPLE = "simple"
        const val RATING_REGULAR = "regular"
        const val RATING_ADVANCED = "advanced"

        private val ratingSystems = mapOf(
            // Smileys
            RATING_SIMPLE to RatingSystem(
                name = RATING_SIMPLE,
                scoreList = listOf("-", "😡", "😐", "😊", "😀"),
                tenScale = listOf(1f, 4f, 7f, 10f), // 2, 8, 14, 20 on Kitsu
            ),
            // DecimalFormatter is not thread safe, so new formatters for each map instead of extracted val attribute
            // to not incite reuse
            // Stars (0.5-5 step 0.5)
            RATING_REGULAR to RatingSystem(
                name = RATING_REGULAR,
                scoreList = (0..10).map { it / 2.0 }.map(DecimalFormat("0.#")::format).map { "$it ★" },
                tenScale = (1..10).map { it.toFloat() }, // 2, 4, ..., 20 on Kitsu
            ),
            // 10 point decimal (step 0.5, starting at 1) + 0 for our "not rated" placeholder
            RATING_ADVANCED to RatingSystem(
                name = RATING_ADVANCED,
                scoreList = listOf("0") + (2..20).map { it / 2.0 }.map(DecimalFormat("0.#")::format),
                tenScale = (2..20).map { it / 2f }, // 2, 3, ..., 20 on Kitsu
            ),
        )
    }

    override fun nameRes() = MR.strings.kitsu

    override val supportsReadingDates: Boolean = true

    override val supportsPrivateTracking: Boolean = true

    private val json: Json by injectLazy()

    private val interceptor by lazy { KitsuInterceptor(this) }

    private val api by lazy { KitsuApi(client, interceptor) }

    private val scorePreference by lazy { trackPreferences.kitsuScoreType() }

    override fun getLogo() = R.drawable.ic_tracker_kitsu

    override fun getTrackerColor() = Color.rgb(253, 117, 92)

    override fun getLogoColor() = Color.rgb(51, 37, 50)

    override fun getStatusList(): List<Int> {
        return listOf(READING, PLAN_TO_READ, COMPLETED, ON_HOLD, DROPPED)
    }

    override fun isCompletedStatus(index: Int) = getStatusList()[index] == COMPLETED

    override fun completedStatus(): Int = COMPLETED
    override fun readingStatus() = READING
    override fun planningStatus() = PLAN_TO_READ

    override fun getStatus(status: Int): String = with(context) {
        when (status) {
            READING -> getString(MR.strings.currently_reading)
            PLAN_TO_READ -> getString(MR.strings.want_to_read)
            COMPLETED -> getString(MR.strings.completed)
            ON_HOLD -> getString(MR.strings.on_hold)
            DROPPED -> getString(MR.strings.dropped)
            else -> ""
        }
    }

    override fun getGlobalStatus(status: Int): String = with(context) {
        when (status) {
            READING -> getString(MR.strings.reading)
            PLAN_TO_READ -> getString(MR.strings.plan_to_read)
            COMPLETED -> getString(MR.strings.completed)
            ON_HOLD -> getString(MR.strings.on_hold)
            DROPPED -> getString(MR.strings.dropped)
            else -> ""
        }
    }

    private fun getCurrentRatingSystem(): RatingSystem {
        val ratingSystem = scorePreference.get().lowercase()
        return ratingSystems[ratingSystem] ?: throw Exception("Unknown score type $ratingSystem")
    }

    override fun getScoreList(): ImmutableList<String> {
        return getCurrentRatingSystem().scoreList.toImmutableList()
    }

    override fun indexToScore(index: Int): Float {
        if (index == 0) return 0f
        return getCurrentRatingSystem().tenScale[index - 1]
    }

    override fun displayScore(track: Track): String {
        val ratingSystem = getCurrentRatingSystem()
        // Since Kitsu's valid score range is 2-20, unset values of -1.0 or 0.0 will both return -1 from indexOfLast
        // which is turned into index 0 of the scoreList, giving us the "unset" display score (- or 0).
        // Proper scores are "rounded down" to the nearest value of the scale (also what Kitsu's website does)
        return ratingSystem.scoreList[ratingSystem.tenScale.indexOfLast { it <= track.score } + 1]
    }

    override suspend fun update(track: Track, setToRead: Boolean): Track {
        updateTrackStatus(track, setToRead, setToComplete = true, mustReadToComplete = false)
        return api.updateLibManga(track)
    }

    override suspend fun add(track: Track): Track {
        track.score = DEFAULT_SCORE
        track.status = DEFAULT_STATUS
        updateNewTrackInfo(track)
        return api.addLibManga(track, getUserId())
    }

    override suspend fun bind(track: Track): Track {
        val remoteTrack = api.findLibManga(track, getUserId())
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.media_id = remoteTrack.media_id
            update(track)
        } else {
            add(track)
        }
    }

    override fun canRemoveFromService() = true

    override suspend fun removeFromService(track: Track): Boolean {
        return api.remove(track)
    }

    override suspend fun search(query: String): List<TrackSearch> {
        return api.search(query)
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getLibManga(track)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override suspend fun login(username: String, password: String): Boolean {
        return try {
            val oauth = api.login(username, password)
            interceptor.newAuth(oauth)
            val currentUser = api.getCurrentUser()
            applyRatingSystem(currentUser.ratingSystem)
            saveDisplayUsername(currentUser.name)
            saveCredentials(username, currentUser.id)
            true
        } catch (e: Exception) {
            Logger.e(e) { "Unable to login" }
            false
        }
    }

    override suspend fun updateUserConfig() {
        val currentUser = api.getCurrentUser()
        applyRatingSystem(currentUser.ratingSystem)
        saveDisplayUsername(currentUser.name)
    }

    private fun applyRatingSystem(ratingSystem: String) {
        val system = ratingSystem.lowercase()
        if (system in listOf(RATING_SIMPLE, RATING_REGULAR, RATING_ADVANCED)) {
            scorePreference.set(system)
        } else {
            Logger.e(Exception("Unsupported Kitsu score type: $ratingSystem"))
            scorePreference.set(RATING_ADVANCED)
        }
    }

    override fun logout() {
        super.logout()
        interceptor.newAuth(null)
    }

    private fun getUserId(): String {
        return getPassword()
    }

    fun saveToken(oauth: KitsuOAuth?) {
        trackPreferences.trackToken(this).set(json.encodeToString(oauth))
    }

    fun restoreToken(): KitsuOAuth? {
        return try {
            json.decodeFromString<KitsuOAuth>(trackPreferences.trackToken(this).get())
        } catch (e: Exception) {
            null
        }
    }
}
