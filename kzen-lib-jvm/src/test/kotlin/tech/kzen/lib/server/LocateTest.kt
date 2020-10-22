package tech.kzen.lib.server

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.locate.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.media.MapNotationMedia
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.common.service.store.DirectGraphStore
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
//        val repo = NotationRepository(
        val repo = DirectGraphStore(
                media, yamlParser, metadataReader, GraphDefiner(), NotationReducer())

        val notation = runBlocking {
            media.writeDocument(aPath, """
LocateName:
  value: "a"
""")
            media.writeDocument(bPath, """
LocateName:
  value: "b"
""")
            repo.graphNotation()
        }

        assertEquals(
                locateNameLocationA,
                notation.coalesce.locate(locateNameReference, aHost))

        assertEquals(
                locateNameLocationB,
                notation.coalesce.locate(locateNameReference, bHost))
    }
}
