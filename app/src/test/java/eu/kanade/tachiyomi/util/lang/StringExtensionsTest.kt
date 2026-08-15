package eu.kanade.tachiyomi.util.lang

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StringExtensionsTest {

    @Test
    fun `chop returns original string when within count`() {
        assertEquals("hello", "hello".chop(10))
    }

    @Test
    fun `chop truncates and appends replacement when over count`() {
        assertEquals("hel…", "hello world".chop(4))
    }

    @Test
    fun `chop supports a custom replacement`() {
        assertEquals("he...", "hello world".chop(5, "..."))
    }

    @Test
    fun `chopByWords returns original string when within count`() {
        assertEquals("hello world", "hello world".chopByWords(20))
    }

    @Test
    fun `chopByWords keeps only whole words that fit`() {
        assertEquals("hello world", "hello world foo bar".chopByWords(11))
    }

    @Test
    fun `chopByWords chops the first word if it alone exceeds count`() {
        assertEquals("hel…", "helloworld foo".chopByWords(4))
    }

    @Test
    fun `removeArticles strips leading a`() {
        assertEquals("Cat in the Hat", "A Cat in the Hat".removeArticles())
    }

    @Test
    fun `removeArticles strips leading an`() {
        assertEquals("Apple a Day", "An Apple a Day".removeArticles())
    }

    @Test
    fun `removeArticles strips leading the`() {
        assertEquals("Lord of the Rings", "The Lord of the Rings".removeArticles())
    }

    @Test
    fun `removeArticles is case insensitive`() {
        assertEquals("cat", "THE cat".removeArticles())
    }

    @Test
    fun `removeArticles leaves string untouched without a leading article`() {
        assertEquals("One Piece", "One Piece".removeArticles())
    }

    @Test
    fun `sqLite escapes single quotes`() {
        assertEquals("It''s a test", "It's a test".sqLite)
    }

    @Test
    fun `trimOrNull trims a valid string`() {
        assertEquals("hello", "  hello  ".trimOrNull())
    }

    @Test
    fun `trimOrNull returns null for a blank string`() {
        assertNull("   ".trimOrNull())
    }

    @Test
    fun `truncateCenter returns original string when within count`() {
        assertEquals("hello", "hello".truncateCenter(10))
    }

    @Test
    fun `truncateCenter keeps head and tail with replacement in the middle`() {
        val result = "abcdefghijklmnopqrstuvwxyz".truncateCenter(10)

        assertEquals("abc...xyz", result)
    }

    @Test
    fun `capitalizeWords capitalizes each space and hyphen separated word`() {
        assertEquals("One-Piece Story", "one-piece story".capitalizeWords())
    }

    @Test
    fun `compareToCaseInsensitiveNaturalOrder sorts numbers naturally`() {
        val sorted = listOf("chapter 10", "chapter 2", "chapter 1")
            .sortedWith(String::compareToCaseInsensitiveNaturalOrder)

        assertEquals(listOf("chapter 1", "chapter 2", "chapter 10"), sorted)
    }

    @Test
    fun `compareToCaseInsensitiveNaturalOrder ignores case`() {
        assertEquals(0, "Chapter".compareToCaseInsensitiveNaturalOrder("chapter"))
    }

    @Test
    fun `indexesOf finds all occurrences case-insensitively by default`() {
        val result = "Ab ab AB".indexesOf("ab")

        assertEquals(listOf(0, 3, 6), result)
    }

    @Test
    fun `indexesOf respects ignoreCase false`() {
        val result = "Ab ab AB".indexesOf("ab", ignoreCase = false)

        assertEquals(listOf(3), result)
    }

    @Test
    fun `indexesOf returns empty list for blank substring`() {
        assertTrue("hello".indexesOf("").isEmpty())
    }

    @Test
    fun `indexesOf returns empty list when not found`() {
        assertTrue("hello".indexesOf("xyz").isEmpty())
    }

    @Test
    fun `toNormalized replaces curly apostrophe with straight one`() {
        assertEquals("it's", "it’s".toNormalized())
    }

    @Test
    fun `getUrlWithoutDomain strips scheme and host`() {
        assertEquals("/path/to/page", "https://example.com/path/to/page".getUrlWithoutDomain())
    }

    @Test
    fun `getUrlWithoutDomain keeps query and fragment`() {
        assertEquals("/search?q=test#top", "https://example.com/search?q=test#top".getUrlWithoutDomain())
    }

    @Test
    fun `getUrlWithoutDomain returns original string on invalid uri`() {
        val invalid = "http://[invalid"

        assertEquals(invalid, invalid.getUrlWithoutDomain())
    }
}
