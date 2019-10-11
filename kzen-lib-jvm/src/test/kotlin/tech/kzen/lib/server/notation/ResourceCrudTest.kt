package tech.kzen.lib.server.notation

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ResourceLocation
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
                GraphDefiner(),
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
                GraphDefiner(),
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
                GraphDefiner(),
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
}