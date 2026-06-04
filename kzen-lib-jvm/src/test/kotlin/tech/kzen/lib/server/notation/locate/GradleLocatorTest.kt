package tech.kzen.lib.server.notation.locate

import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class GradleLocatorTest {
    @Test
    fun `Module root of code source is the module directory`() {
        // Works whether GradleLocator's code source is the classes dir or the jar under
        //  build/libs — both live under the module directory.
        val moduleRoot = GradleLocator.moduleRootOfCodeSource(GradleLocator::class.java)

        assertEquals("kzen-lib-jvm", moduleRoot.fileName.toString())
        assertTrue(Files.isDirectory(moduleRoot.resolve("src/main/resources/notation")))
    }
}
