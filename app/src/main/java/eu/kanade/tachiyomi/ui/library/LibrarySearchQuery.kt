package eu.kanade.tachiyomi.ui.library

import android.content.Context
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.seriesType
import eu.kanade.tachiyomi.source.SourceManager

/**
 * Parses and evaluates library search queries supporting:
 * - `&&` / `||` boolean operators (default combination between terms is AND)
 * - `-term` negation
 * - field prefixes: `title:`, `author:`, `artist:`, `src:`/`source:`, `genre:`/`tag:`
 * - numeric comparators on `chapters`/`ch`, `unread`, and `read`: `chapters>10`, `unread=0`, `read<5`
 *
 * Anything that doesn't parse as a recognized field/comparator falls back to the legacy
 * substring search across title/author/artist/source/genre.
 */
sealed class LibrarySearchExpr {
    data class Or(val terms: List<LibrarySearchExpr>) : LibrarySearchExpr()
    data class And(val terms: List<LibrarySearchExpr>) : LibrarySearchExpr()
    data class Not(val term: LibrarySearchExpr) : LibrarySearchExpr()
    data class Field(val field: String, val op: Char, val value: String) : LibrarySearchExpr()
    data class Text(val value: String) : LibrarySearchExpr()
}

object LibrarySearchQuery {

    private val numericFields = setOf("chapters", "ch", "unread", "read")
    private val fieldNamePattern = Regex("^([a-zA-Z]+)([:><=])(.+)$")

    private var lastRaw: String? = null
    private var lastParsed: LibrarySearchExpr = LibrarySearchExpr.Or(emptyList())

    fun parse(raw: String): LibrarySearchExpr {
        if (raw == lastRaw) return lastParsed
        val parsed = parseOr(raw)
        lastRaw = raw
        lastParsed = parsed
        return parsed
    }

    private fun parseOr(raw: String): LibrarySearchExpr {
        val groups = splitTopLevel(raw, "||")
        if (groups.size <= 1) return parseAnd(raw)
        return LibrarySearchExpr.Or(groups.map { parseAnd(it) })
    }

    private fun parseAnd(raw: String): LibrarySearchExpr {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return LibrarySearchExpr.And(emptyList())
        val parts = if (trimmed.contains("&&")) {
            splitTopLevel(trimmed, "&&")
        } else {
            trimmed.split(Regex("\\s+"))
        }
        val terms = parts.mapNotNull { parseTerm(it.trim()) }
        return LibrarySearchExpr.And(terms)
    }

    private fun splitTopLevel(raw: String, delimiter: String): List<String> {
        return raw.split(delimiter).map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun parseTerm(term: String): LibrarySearchExpr? {
        if (term.isEmpty()) return null
        val negate = term.startsWith("-") && term.length > 1
        val body = if (negate) term.substring(1) else term

        val expr = fieldNamePattern.matchEntire(body)?.let { match ->
            val (field, opStr, value) = match.destructured
            val normalizedField = field.lowercase()
            val op = opStr[0]
            if (isKnownField(normalizedField, op)) {
                LibrarySearchExpr.Field(normalizedField, op, value)
            } else {
                null
            }
        } ?: LibrarySearchExpr.Text(body)

        return if (negate) LibrarySearchExpr.Not(expr) else expr
    }

    private fun isKnownField(field: String, op: Char): Boolean {
        return when (field) {
            "title", "author", "artist", "src", "source", "genre", "tag" -> op == ':'
            in numericFields -> true
            else -> false
        }
    }

    fun matches(
        expr: LibrarySearchExpr,
        manga: LibraryManga,
        context: Context?,
        sourceManager: SourceManager,
    ): Boolean {
        return when (expr) {
            is LibrarySearchExpr.Or -> expr.terms.isEmpty() || expr.terms.any { matches(it, manga, context, sourceManager) }
            is LibrarySearchExpr.And -> expr.terms.all { matches(it, manga, context, sourceManager) }
            is LibrarySearchExpr.Not -> !matches(expr.term, manga, context, sourceManager)
            is LibrarySearchExpr.Field -> matchesField(expr, manga, context, sourceManager)
            is LibrarySearchExpr.Text -> matchesText(expr.value, manga, context, sourceManager)
        }
    }

    private fun matchesField(
        expr: LibrarySearchExpr.Field,
        manga: LibraryManga,
        context: Context?,
        sourceManager: SourceManager,
    ): Boolean {
        val m = manga.manga
        return when (expr.field) {
            "title" -> m.title.contains(expr.value, true)
            "author" -> m.author?.contains(expr.value, true) ?: false
            "artist" -> m.artist?.contains(expr.value, true) ?: false
            "src", "source" -> {
                val sourceName by lazy { sourceManager.getOrStub(m.source).name }
                expr.value.toLongOrNull()?.let { it == m.source } ?: sourceName.contains(expr.value, true)
            }
            "genre", "tag" -> containsGenre(expr.value, m.genre?.split(", "), context, manga, sourceManager)
            in numericFields -> matchesNumeric(expr, manga)
            else -> false
        }
    }

    private fun matchesNumeric(expr: LibrarySearchExpr.Field, manga: LibraryManga): Boolean {
        val target = expr.value.toDoubleOrNull() ?: return false
        val actual = when (expr.field) {
            "chapters", "ch" -> manga.totalChapters
            "unread" -> manga.unread
            "read" -> manga.read
            else -> return false
        }.toDouble()
        return when (expr.op) {
            '>' -> actual > target
            '<' -> actual < target
            '=' -> actual == target
            else -> false
        }
    }

    private fun matchesText(
        value: String,
        manga: LibraryManga,
        context: Context?,
        sourceManager: SourceManager,
    ): Boolean {
        val m = manga.manga
        val sourceName by lazy { sourceManager.getOrStub(m.source).name }
        return m.title.contains(value, true) ||
            (m.author?.contains(value, true) ?: false) ||
            (m.artist?.contains(value, true) ?: false) ||
            sourceName.contains(value, true) ||
            containsGenre(value, m.genre?.split(", "), context, manga, sourceManager)
    }

    private fun containsGenre(
        tag: String,
        genres: List<String>?,
        context: Context?,
        manga: LibraryManga,
        sourceManager: SourceManager,
    ): Boolean {
        if (tag.trim().isEmpty()) return true
        if (genres?.any { it.trim().equals(tag, ignoreCase = true) } == true) return true
        context ?: return false
        return manga.manga.seriesType(context, sourceManager).equals(tag, true)
    }
}
