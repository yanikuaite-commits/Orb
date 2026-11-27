package dev.orb.app

import dev.orb.app.agent.FilePaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FilePathsTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun root(): File = temp.newFolder("sdcard")

    @Test
    fun resolvesRelativeAndSdcardPaths() {
        val root = root()
        assertEquals(File(root, "Download").canonicalFile, FilePaths.resolve(root, "Download"))
        assertEquals(File(root, "Download").canonicalFile, FilePaths.resolve(root, "/sdcard/Download"))
        assertEquals(File(root, "Download").canonicalFile, FilePaths.resolve(root, "sdcard/Download"))
        assertEquals(root.canonicalFile, FilePaths.resolve(root, "/sdcard"))
    }

    @Test
    fun blocksAndroidTree() {
        val root = root()
        assertNull(FilePaths.resolve(root, "/sdcard/Android"))
        assertNull(FilePaths.resolve(root, "/sdcard/Android/data"))
        assertNull(FilePaths.resolve(root, "Android/media"))
        assertTrue(FilePaths.isBlocked(root, File(root, "Android/obb")))
    }

    @Test
    fun rejectsTraversalAndSystemPaths() {
        val root = root()
        assertNull(FilePaths.resolve(root, "../escape"))
        assertNull(FilePaths.resolve(root, "/sdcard/../escape"))
        assertNull(FilePaths.resolve(root, "/system/etc"))
        assertNull(FilePaths.resolve(root, "/data/data/dev.orb.app"))
    }

    @Test
    fun normalizesInnerTraversal() {
        val root = root()
        assertEquals(
            File(root, "Documents").canonicalFile,
            FilePaths.resolve(root, "/sdcard/Download/../Documents"),
        )
    }

    @Test
    fun safeChildStopsZipSlip() {
        val root = root()
        assertNull(FilePaths.safeChild(root, "../evil.txt"))
        assertNull(FilePaths.safeChild(root, "/absolute.txt"))
        assertNotNull(FilePaths.safeChild(root, "folder/file.txt"))
    }

    @Test
    fun displayUsesSdcardPrefix() {
        val root = root()
        assertEquals("/sdcard/Download/a.txt", FilePaths.display(root, File(root, "Download/a.txt")))
        assertEquals("/sdcard", FilePaths.display(root, root))
    }
}
