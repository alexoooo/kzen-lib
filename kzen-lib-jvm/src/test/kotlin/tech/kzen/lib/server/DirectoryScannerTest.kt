package tech.kzen.lib.server

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.resource.ResourcePath
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.server.notation.FileNotationMedia
import tech.kzen.lib.server.notation.locate.GradleLocator
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class DirectoryScannerTest {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val dirDocDocumentPath = DocumentPath.parse("test/directory-document/~main.yaml")
        private val blobResourcePath = ResourcePath.parse("blob.txt")
        private val blobContent = "blob"
    }


    @Test
    fun `Scan jvm main resources`() {
        val locator = GradleLocator(true)
        val scanner = FileNotationMedia(locator)

        val paths = runBlocking {
            scanner.scan()
        }

        assertTrue(NotationConventions.kzenBasePath in paths.documents.values)
        assertTrue(dirDocDocumentPath in paths.documents.values)

        val dirDocScan = paths.documents[dirDocDocumentPath]!!
        val dirDocResources = dirDocScan.resources
        assertNotNull(dirDocResources)

        val blobDigest = dirDocResources.values[blobResourcePath]!!
        assertEquals(Digest.ofUtf8(blobContent), blobDigest)
    }
}