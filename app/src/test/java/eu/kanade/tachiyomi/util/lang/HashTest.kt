package eu.kanade.tachiyomi.util.lang

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HashTest {

    @Test
    fun `md5 matches known digest`() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", Hash.md5("hello"))
    }

    @Test
    fun `sha256 matches known digest`() {
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            Hash.sha256("hello"),
        )
    }

    @Test
    fun `md5 is deterministic for the same input`() {
        assertEquals(Hash.md5("same input"), Hash.md5("same input"))
    }

    @Test
    fun `md5 differs for different input`() {
        assert(Hash.md5("input a") != Hash.md5("input b"))
    }

    @Test
    fun `md5 of empty string is the well known empty digest`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Hash.md5(""))
    }
}
