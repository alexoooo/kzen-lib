package tech.kzen.lib.server

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.lib.common.context.ObjectGraphDefiner
import tech.kzen.lib.common.notation.NotationConventions
import tech.kzen.lib.server.notation.FileNotationMedia
import tech.kzen.lib.server.notation.locate.GradleLocator
import kotlin.test.assertTrue


class DirectoryScannerTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Scan jvm main resources`() {
        val locator = GradleLocator()
        val scanner = FileNotationMedia(locator)

        val paths = runBlocking {
            scanner.scan()
        }

        assertTrue(paths.contains(NotationConventions.kzenBasePath))
    }
}