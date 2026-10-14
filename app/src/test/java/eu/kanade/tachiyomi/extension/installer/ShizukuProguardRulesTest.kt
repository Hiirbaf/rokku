package eu.kanade.tachiyomi.extension.installer

import eu.kanade.tachiyomi.extension.installer.shizuku.ShellInterface
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Shizuku starts [ShellInterface] reflectively in its own process, so R8 must keep its constructor
 * and `destroy()`. The rule names the class by string, and a rename or move would silently drop it,
 * breaking the installer only in minified builds.
 */
class ShizukuProguardRulesTest {

    private val rules = File("proguard-rules.pro").readText()

    private val shellInterfaceBlock = Regex(
        """-keepclassmembers\s+class\s+${Regex.escape(ShellInterface::class.java.name)}\s*\{([^}]*)}""",
    ).find(rules)?.groupValues?.get(1)

    @Test
    fun `proguard rules keep members of the current ShellInterface class`() {
        assertNotNull(
            shellInterfaceBlock,
            "No -keepclassmembers rule for ${ShellInterface::class.java.name} in proguard-rules.pro",
        )
    }

    @Test
    fun `ShellInterface constructor is kept for Shizuku`() {
        assertTrue(shellInterfaceBlock.orEmpty().contains(Regex("""public\s+<init>\(\)\s*;""")))
    }

    @Test
    fun `ShellInterface destroy is kept for Shizuku`() {
        assertTrue(shellInterfaceBlock.orEmpty().contains(Regex("""public\s+void\s+destroy\(\)\s*;""")))
    }
}
