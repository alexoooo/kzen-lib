package tech.kzen.lib.server

import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.server.notation.locate.GradleLocator
import tech.kzen.lib.server.notation.scan.DirectoryNotationScanner
import kotlin.test.assertTrue


class DirectoryScannerTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Scan jvm main resources`() {
        val scanner = DirectoryNotationScanner(GradleLocator())

        val paths = runBlocking {
            scanner.scan()
        }

        assertTrue(paths.contains(ProjectPath("notation/base/kzen-base.yaml")))
    }
}