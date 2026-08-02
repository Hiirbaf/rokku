package eu.kanade.tachiyomi.smartsearch

import eu.kanade.tachiyomi.data.database.models.create
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.lang.toNormalized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import uy.kohesive.injekt.injectLazy
import yokai.domain.manga.interactor.GetManga
import yokai.domain.manga.interactor.InsertManga
import yokai.util.normalizedLevenshteinSimilarity
import kotlin.coroutines.CoroutineContext

class SmartSearchEngine(
    parentContext: CoroutineContext,
    val extraSearchParams: String? = null,
) : CoroutineScope {
    override val coroutineContext: CoroutineContext = parentContext + Job() + Dispatchers.Default

    private val getManga: GetManga by injectLazy()
    private val insertManga: InsertManga by injectLazy()

    /**
     * Tries several variations of [title] (full cleaned title, then progressively shorter
     * word combinations) against [source], aggregating every candidate above the eligibility
     * threshold, instead of a single query with the full title. A single query often misses a
     * legitimate match if the source's own search doesn't rank it highly for the exact
     * (possibly noisy) title text - e.g. subtitles, alternate symbols/punctuation, or word
     * order differences the source's search doesn't handle well.
     */
    suspend fun smartSearch(source: CatalogueSource, title: String): SManga? {
        val cleanedTitle = cleanSmartSearchTitle(title)

        val queries = getSmartSearchQueries(cleanedTitle)

        val eligibleManga = supervisorScope {
            queries.map { query ->
                async(Dispatchers.Default) {
                    val builtQuery = if (extraSearchParams != null) {
                        "$query ${extraSearchParams.trim()}"
                    } else {
                        query
                    }

                    val searchResults = try {
                        source.getSearchManga(1, builtQuery, source.getFilterList())
                    } catch (e: Exception) {
                        return@async emptyList()
                    }

                    searchResults.mangas.map {
                        val cleanedMangaTitle = cleanSmartSearchTitle(it.title)
                        val normalizedDistance = normalizedLevenshteinSimilarity(cleanedTitle, cleanedMangaTitle)
                        SearchEntry(it, normalizedDistance)
                    }.filter { (_, normalizedDistance) ->
                        normalizedDistance >= MIN_SMART_ELIGIBLE_THRESHOLD
                    }
                }
            }.flatMap { it.await() }
        }

        return eligibleManga.maxByOrNull { it.dist }?.manga
    }

    private fun cleanSmartSearchTitle(title: String): String {
        val preTitle = title.toNormalized().lowercase()

        // Remove text in brackets
        var cleanedTitle = removeTextInBrackets(preTitle, true)
        if (cleanedTitle.length <= 5) { // Title is suspiciously short, try parsing it backwards
            cleanedTitle = removeTextInBrackets(preTitle, false)
        }

        // Strip non-special characters
        cleanedTitle = cleanedTitle.replace(titleRegex, " ")

        // Strip splitters and consecutive spaces
        cleanedTitle = cleanedTitle.trim().replace(" - ", " ").replace(consecutiveSpacesRegex, " ").trim()

        return cleanedTitle
    }

    private fun getSmartSearchQueries(cleanedTitle: String): List<String> {
        val splitCleanedTitle = cleanedTitle.split(" ")
        val splitSortedByLargest = splitCleanedTitle.sortedByDescending { it.length }

        if (splitCleanedTitle.isEmpty()) {
            return emptyList()
        }

        // Search cleaned title
        // Search two largest words
        // Search largest word
        // Search first two words
        // Search first word
        val searchQueries = listOf(
            listOf(cleanedTitle),
            splitSortedByLargest.take(2),
            splitSortedByLargest.take(1),
            splitCleanedTitle.take(2),
            splitCleanedTitle.take(1),
        )

        return searchQueries
            .map { it.joinToString(" ").trim() }
            .distinct()
    }

    private fun removeTextInBrackets(text: String, readForward: Boolean): String {
        val bracketPairs = listOf(
            '(' to ')',
            '[' to ']',
            '<' to '>',
            '{' to '}',
        )
        var openingBracketPairs = bracketPairs.mapIndexed { index, (opening, _) ->
            opening to index
        }.toMap()
        var closingBracketPairs = bracketPairs.mapIndexed { index, (_, closing) ->
            closing to index
        }.toMap()

        // Reverse pairs if reading backwards
        if (!readForward) {
            val tmp = openingBracketPairs
            openingBracketPairs = closingBracketPairs
            closingBracketPairs = tmp
        }

        val depthPairs = bracketPairs.map { 0 }.toMutableList()

        val result = StringBuilder()
        for (c in if (readForward) text else text.reversed()) {
            val openingBracketDepthIndex = openingBracketPairs[c]
            if (openingBracketDepthIndex != null) {
                depthPairs[openingBracketDepthIndex]++
            } else {
                val closingBracketDepthIndex = closingBracketPairs[c]
                if (closingBracketDepthIndex != null) {
                    depthPairs[closingBracketDepthIndex]--
                } else {
                    if (depthPairs.all { it <= 0 }) {
                        result.append(c)
                    } else {
                        // In brackets, do not append to result
                    }
                }
            }
        }

        return result.toString()
    }

    /**
     * Returns a manga from the database for the given manga from network. It creates a new entry
     * if the manga is not yet in the database.
     *
     * @param sManga the manga from the source.
     * @return a manga from the database.
     */
    suspend fun networkToLocalManga(sManga: SManga, sourceId: Long): Manga {
        var localManga = getManga.awaitByUrlAndSource(sManga.url, sourceId)
        if (localManga == null) {
            val newManga = Manga.create(sManga.url, sManga.title, sourceId)
            newManga.copyFrom(sManga)
            newManga.id = insertManga.await(newManga)
            localManga = newManga
        }
        return localManga
    }

    companion object {
        const val MIN_SMART_ELIGIBLE_THRESHOLD = 0.4

        private val titleRegex = Regex("[^a-zA-Z0-9- ]")
        private val consecutiveSpacesRegex = Regex(" +")
    }
}

data class SearchEntry(val manga: SManga, val dist: Double)
