package eu.kanade.tachiyomi.util.system

import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import rikka.shizuku.ShizukuProvider

class ShizukuDetectionTest {

    private val packageManager = mockk<PackageManager>()

    @Test
    fun `detected when the Shizuku permission is declared by any package`() {
        every { packageManager.getPermissionInfo(ShizukuProvider.PERMISSION, 0) } returns mockk<PermissionInfo>()

        assertTrue(isShizukuAvailable(packageManager, isSui = { false }))
    }

    @Test
    fun `not detected when neither the permission nor Sui is present`() {
        every { packageManager.getPermissionInfo(ShizukuProvider.PERMISSION, 0) } throws
            mockk<PackageManager.NameNotFoundException>()

        assertFalse(isShizukuAvailable(packageManager, isSui = { false }))
    }

    @Test
    fun `detected through Sui when the permission is missing`() {
        every { packageManager.getPermissionInfo(ShizukuProvider.PERMISSION, 0) } throws
            mockk<PackageManager.NameNotFoundException>()

        assertTrue(isShizukuAvailable(packageManager, isSui = { true }))
    }

    @Test
    fun `detection never depends on the Shizuku package name`() {
        // Forks that hide themselves (e.g. Shizuku-Next stealth mode) install under another package
        every { packageManager.getPermissionInfo(ShizukuProvider.PERMISSION, 0) } returns mockk<PermissionInfo>()
        every { packageManager.getApplicationInfo(any<String>(), any<Int>()) } throws
            mockk<PackageManager.NameNotFoundException>()

        assertTrue(isShizukuAvailable(packageManager, isSui = { false }))
    }
}
