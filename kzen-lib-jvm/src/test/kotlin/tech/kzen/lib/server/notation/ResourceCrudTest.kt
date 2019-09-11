package tech.kzen.lib.server.notation

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.AddResourceCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.CreateDocumentCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveResourceCommand
import tech.kzen.lib.common.model.structure.resource.ResourceContent
import tech.kzen.lib.common.model.structure.resource.ResourceName
import tech.kzen.lib.common.model.structure.resource.ResourceNesting
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.context.NotationRepository
import tech.kzen.lib.common.service.media.MapNotationMedia
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.parse.YamlNotationParser
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
    }


    @Test
    fun `Add a resource`() {
        val media = MapNotationMedia()

        val repo = NotationRepository(
                media,
                YamlNotationParser(),
                NotationMetadataReader(),
                GraphDefiner())

        val resource = runBlocking {
            repo.apply(CreateDocumentCommand(
                    dirDocPath,
                    DocumentNotation.emptyWithResources))

            repo.apply(AddResourceCommand(
                    resourceLocation,
                    ResourceContent("foo".toByteArray())))

            media.readResource(resourceLocation)
        }

        assertTrue(resource.contentEquals("foo".toByteArray()))
    }


    @Test
    fun `Remove a resource`() {
        val media = MapNotationMedia()

        val repo = NotationRepository(
                media,
                YamlNotationParser(),
                NotationMetadataReader(),
                GraphDefiner())

        val graphNotation = runBlocking {
            repo.apply(CreateDocumentCommand(
                    dirDocPath,
                    DocumentNotation.emptyWithResources))

            repo.apply(AddResourceCommand(
                    resourceLocation,
                    ResourceContent("foo".toByteArray())))

            repo.apply(RemoveResourceCommand(resourceLocation))

            repo.notation()
        }

        assertTrue(resourcePath !in graphNotation.documents[dirDocPath]!!.resources!!.values)
    }
}