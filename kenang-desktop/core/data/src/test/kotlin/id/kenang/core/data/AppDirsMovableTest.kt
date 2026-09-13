package id.kenang.core.data

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every folder the app creates under the relocatable media root must be in
 * [AppDirs.MOVABLE], or the data-folder move (D-053) leaves it behind on the
 * old drive. "talking" was missed when Video Berbicara landed — caught by an
 * adversarial review of the output-folder change (2026-09-13).
 */
class AppDirsMovableTest {

    private val temp = File(System.getProperty("java.io.tmpdir"), "kenang-movable-${System.nanoTime()}")

    @AfterTest
    fun cleanUp() {
        AppDirs.useMediaRoot(null)
        temp.deleteRecursively()
    }

    @Test
    fun `every folder created under the media root is relocatable`() {
        AppDirs.useMediaRoot(temp)

        // Touch every media accessor the app uses to store files.
        val created = listOf(
            AppDirs.projects, AppDirs.tools, AppDirs.music,
            AppDirs.cache, AppDirs.motion, AppDirs.upscale, AppDirs.talking,
        )
        created.forEach { assertTrue(it.isDirectory, "accessor did not create ${it.name}") }

        val onDisk = temp.listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()
        val orphans = onDisk.filterNot { it in AppDirs.MOVABLE }
        assertTrue(
            orphans.isEmpty(),
            "these folders would be left behind by a data-folder move: $orphans",
        )
    }
}
