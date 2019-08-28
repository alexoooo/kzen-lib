package tech.kzen.lib.server

import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.locate.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.structure.metadata.read.NotationMetadataReader
import tech.kzen.lib.common.structure.notation.format.YamlNotationParser
import tech.kzen.lib.common.structure.notation.io.common.MapNotationMedia
import tech.kzen.lib.common.structure.notation.repo.NotationRepository
import tech.kzen.lib.platform.IoUtils
import tech.kzen.lib.server.objects.autowire.ObjectGroup
import tech.kzen.lib.server.objects.autowire.StrongHolder
import tech.kzen.lib.server.objects.autowire.WeakHolder
import tech.kzen.lib.server.util.GraphTestUtils
import kotlin.test.assertEquals


class LocateTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val yamlParser = YamlNotationParser()
    private val metadataReader = NotationMetadataReader()
    private val aPath = DocumentPath.parse("a.yaml")
    private val bPath = DocumentPath.parse("b.yaml")
    private val aHost = ObjectReferenceHost(aPath, null, null)
    private val bHost = ObjectReferenceHost(bPath, null, null)
    private val locateNameReference = ObjectReference.parse("LocateName")
    private val locateNameObjectPath = ObjectPath(locateNameReference.name, ObjectNesting.root)
    private val locateNameLocationA = ObjectLocation(aPath, locateNameObjectPath)
    private val locateNameLocationB = ObjectLocation(bPath, locateNameObjectPath)


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `locate in document`() {
        val media = MapNotationMedia()
        val repo = NotationRepository(media, yamlParser, metadataReader)

        val notation = runBlocking {
            media.write(aPath, IoUtils.utf8Encode("""
LocateName:
  value: "a"
"""))
            media.write(bPath, IoUtils.utf8Encode("""
LocateName:
  value: "b"
"""))
            repo.notation()
        }

        assertEquals(
                locateNameLocationA,
                notation.coalesce.locate(locateNameReference, aHost))

        assertEquals(
                locateNameLocationB,
                notation.coalesce.locate(locateNameReference, bHost))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun location(name: String): ObjectLocation {
        return ObjectLocation(
                DocumentPath.parse("test/autowired.yaml"),
                ObjectPath.parse(name))
    }
}
