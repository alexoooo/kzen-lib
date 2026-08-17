package tech.kzen.lib.server.notation

import com.google.common.io.MoreFiles
import com.google.common.io.RecursiveDeleteOption
import kotlinx.coroutines.runBlocking
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.AddResourceCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.CreateDocumentCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveResourceCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameDocumentRefactorCommand
import tech.kzen.lib.common.model.structure.resource.ResourceName
import tech.kzen.lib.common.model.structure.resource.ResourceNesting
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.media.MapNotationMedia
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.common.service.store.DirectGraphStore
import tech.kzen.lib.common.util.ImmutableByteArray
import tech.kzen.lib.server.notation.locate.FileNotationLocator
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class ResourceCrudTest {
    companion object {
        private val dirDocPath = DocumentPath(
                DocumentName("test"),
                DocumentNesting.empty,
                true)

        private val resourcePath = ResourcePath(
                ResourceName("blob.txt"), ResourceNesting.empty)

        private val resourceLocation = ResourceLocation(dirDocPath, resourcePath)


        private val renameDocPath = DocumentPath(
                DocumentName("test2"),
                DocumentNesting.empty,
                true)

        private val renamedResourceLocation = ResourceLocation(renameDocPath, resourcePath)
    }


    @Test
    fun `Add a resource`() {
        val media = MapNotationMedia()

        val repo = DirectGraphStore(
                media,
                YamlNotationParser(),
                NotationMetadataReader(),
                GraphDefiner,
                NotationReducer())

        val resource = runBlocking {
            repo.apply(CreateDocumentCommand(
                    dirDocPath,
                    DocumentObjectNotation.empty))

            repo.apply(AddResourceCommand(
                    resourceLocation,
                    ImmutableByteArray.wrap("foo".toByteArray())))

            media.readResource(resourceLocation)
        }

        assertEquals(resource, ImmutableByteArray.wrap("foo".toByteArray()))
    }


    @Test
    fun `Remove a resource`() {
        val media = MapNotationMedia()

        val repo = DirectGraphStore(
                media,
                YamlNotationParser(),
                NotationMetadataReader(),
                GraphDefiner,
                NotationReducer())

        val graphNotation = runBlocking {
            repo.apply(CreateDocumentCommand(
                    dirDocPath,
                    DocumentObjectNotation.empty))

            repo.apply(AddResourceCommand(
                    resourceLocation,
                    ImmutableByteArray.wrap("foo".toByteArray())))

            repo.apply(RemoveResourceCommand(resourceLocation))

            repo.graphNotation()
        }

        assertTrue(resourcePath !in graphNotation.documents[dirDocPath]!!.resources!!.digests)
    }


    @Test
    fun `Rename document with resource`() {
        val media = MapNotationMedia()

        val repo = DirectGraphStore(
                media,
                YamlNotationParser(),
                NotationMetadataReader(),
                GraphDefiner,
                NotationReducer())

        val resource = runBlocking {
            repo.apply(CreateDocumentCommand(
                    dirDocPath,
                    DocumentObjectNotation.empty))

            repo.apply(AddResourceCommand(
                    resourceLocation,
                    ImmutableByteArray.wrap("foo".toByteArray())))

            repo.apply(RenameDocumentRefactorCommand(
                    dirDocPath,
                    renameDocPath.name
            ))

            media.readResource(renamedResourceLocation)
        }

        assertEquals(resource, ImmutableByteArray.wrap("foo".toByteArray()))
    }


    @Test
    fun `Copy a resource to another document`() {
        // File-backed on purpose: copyResource's incremental scan-mirror upsert is FileNotationMedia-specific,
        // so the destination listing must be asserted through a warm (mirrored) scan.
        val root = Files.createTempDirectory("resource-copy-test")
        try {
            val media = FileNotationMedia(TempDirLocator(root))

            val sourceDocPath = DocumentPath.parse("source/~main.yaml")
            val destinationDocPath = DocumentPath.parse("destination/~main.yaml")
            val sourceLocation = ResourceLocation(sourceDocPath, resourcePath)
            val destinationLocation = ResourceLocation(destinationDocPath, resourcePath)
            val contents = ImmutableByteArray.wrap("foo".toByteArray())

            runBlocking {
                media.writeDocument(sourceDocPath, "")
                media.writeDocument(destinationDocPath, "")
                media.writeResource(sourceLocation, contents)

                // warm the scan mirrors so the copy goes through the incremental upsert, not a cold rescan
                media.scan()

                media.copyResource(sourceLocation, destinationLocation)

                assertEquals(contents, media.readResource(destinationLocation),
                    "destination content readable after copy")
                assertEquals(contents, media.readResource(sourceLocation),
                    "source intact after copy")

                val scan = media.scan()
                assertEquals(
                    contents.digest(),
                    scan.documents[destinationDocPath]!!.resources!!.digests[resourcePath],
                    "destination resource listed with content digest")
                assertEquals(
                    contents.digest(),
                    scan.documents[sourceDocPath]!!.resources!!.digests[resourcePath],
                    "source resource still listed with content digest")
            }
        }
        finally {
            MoreFiles.deleteRecursively(root, RecursiveDeleteOption.ALLOW_INSECURE)
        }
    }


    private class TempDirLocator(private val root: Path): FileNotationLocator {
        override fun scanRoots(): List<Path> {
            return listOf(root)
        }

        override fun locateExisting(location: DocumentPath): Path? {
            val resolved = root.resolve(location.asRelativeFile())
            return if (Files.exists(resolved)) resolved else null
        }

        override fun resolveNew(location: DocumentPath): Path {
            return root.resolve(location.asRelativeFile())
        }
    }
}