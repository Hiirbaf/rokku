package yokai.domain.base.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VersionTest {

    @Test
    fun `parses a plain stable version`() {
        val version = Version.parse("1.2.3")

        assertEquals(Version.Type.STABLE, version.type)
        assertEquals(Version.Stage.RELEASE, version.stage)
        assertEquals(1, version.major)
        assertEquals(2, version.minor)
        assertEquals(3, version.patch)
        assertEquals(0, version.hotfix)
        assertEquals(0, version.build)
    }

    @Test
    fun `parses a v-prefixed stable version`() {
        val version = Version.parse("v1.2.3")

        assertEquals(Version.Type.STABLE, version.type)
        assertEquals(1, version.major)
        assertEquals(2, version.minor)
        assertEquals(3, version.patch)
    }

    @Test
    fun `parses a nightly version keeping only the build number`() {
        val version = Version.parse("r42")

        assertEquals(Version.Type.NIGHTLY, version.type)
        assertEquals(0, version.major)
        assertEquals(0, version.minor)
        assertEquals(0, version.patch)
        assertEquals(42, version.build)
    }

    @Test
    fun `parses a beta stage with its build number`() {
        val version = Version.parse("1.2.3-beta4")

        assertEquals(Version.Type.STABLE, version.type)
        assertEquals(Version.Stage.BETA, version.stage)
        assertEquals(1, version.major)
        assertEquals(2, version.minor)
        assertEquals(3, version.patch)
        assertEquals(4, version.build)
    }

    @Test
    fun `parses an alpha stage with its build number`() {
        val version = Version.parse("2.0.0-alpha1")

        assertEquals(Version.Stage.ALPHA, version.stage)
        assertEquals(1, version.build)
    }

    @Test
    fun `defaults missing minor, patch and hotfix to zero`() {
        val version = Version.parse("5")

        assertEquals(5, version.major)
        assertEquals(0, version.minor)
        assertEquals(0, version.patch)
        assertEquals(0, version.hotfix)
    }

    @Test
    fun `toString omits hotfix and build when both are zero`() {
        assertEquals("1.2.3", Version.parse("1.2.3").toString())
    }

    @Test
    fun `toString includes hotfix when present`() {
        val version = Version(Version.Type.STABLE, Version.Stage.RELEASE, 1, 2, 3, 4)

        assertEquals("1.2.3.4", version.toString())
    }

    @Test
    fun `toString includes build with the type prefix when present`() {
        assertEquals("1.2.3-v4", Version.parse("1.2.3-beta4").toString())
    }

    @Test
    fun `compareTo detects a higher major version`() {
        assertTrue(Version.parse("2.0.0") > Version.parse("1.9.9"))
    }

    @Test
    fun `compareTo detects a higher minor version`() {
        assertTrue(Version.parse("1.3.0") > Version.parse("1.2.9"))
    }

    @Test
    fun `compareTo detects a higher patch version`() {
        assertTrue(Version.parse("1.2.4") > Version.parse("1.2.3"))
    }

    @Test
    fun `compareTo detects a higher hotfix`() {
        val base = Version(Version.Type.STABLE, Version.Stage.RELEASE, 1, 2, 3, 0)
        val hotfixed = Version(Version.Type.STABLE, Version.Stage.RELEASE, 1, 2, 3, 1)

        assertTrue(hotfixed > base)
    }

    @Test
    fun `compareTo treats release as higher than beta at the same version numbers`() {
        assertTrue(Version.parse("1.0.0") > Version.parse("1.0.0-beta1"))
    }

    @Test
    fun `compareTo treats beta as higher than alpha at the same version numbers`() {
        assertTrue(Version.parse("1.0.0-beta1") > Version.parse("1.0.0-alpha1"))
    }

    @Test
    fun `compareTo treats equal versions as equal`() {
        assertEquals(0, Version.parse("1.2.3").compareTo(Version.parse("1.2.3")))
    }

    @Test
    fun `compareTo on nightly only considers the build number`() {
        val older = Version.parse("r10")
        val newer = Version.parse("r20")

        assertTrue(newer > older)
    }

    @Test
    fun `compareTo throws for debug versions`() {
        val debug = Version.parse("d1.0.0")

        assertThrows(IllegalStateException::class.java) {
            debug.compareTo(Version.parse("d1.0.0"))
        }
    }

    @Test
    fun `compareTo throws when comparing different version types`() {
        val stable = Version.parse("1.0.0")
        val nightly = Version.parse("r1")

        assertThrows(IllegalArgumentException::class.java) {
            stable.compareTo(nightly)
        }
    }
}
