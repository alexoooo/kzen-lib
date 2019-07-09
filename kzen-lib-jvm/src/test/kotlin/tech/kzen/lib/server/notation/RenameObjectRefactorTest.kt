package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.structure.notation.edit.NotationAggregate
import tech.kzen.lib.common.structure.notation.edit.RenameObjectRefactorCommand
import tech.kzen.lib.common.structure.notation.format.YamlNotationParser
import tech.kzen.lib.common.structure.notation.model.DocumentNotation
import tech.kzen.lib.platform.IoUtils
import tech.kzen.lib.server.util.GraphTestUtils
import kotlin.test.assertEquals


class RenameObjectRefactorTest {
    //-----------------------------------------------------------------------------------------------------------------
//    private val yamlParser = YamlNotationParser()
    private val testPath = DocumentPath.parse("test/refactor-test.yaml")

    
//    fun deparseDocument(documentNotation: DocumentNotation): String {
//        return IoUtils.utf8Decode(
//                yamlParser.deparseDocument(documentNotation, ByteArray(0)))
//    }
    

    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Rename should update references`() {
        val notationTree = GraphTestUtils.readNotation()
        val graphDefinition = GraphTestUtils.grapDefinition(notationTree)

        val aggregate = NotationAggregate(notationTree)

        aggregate.apply(
                RenameObjectRefactorCommand(
                        location("main.addends/OldName"), ObjectName("NewName")),
                graphDefinition)

        val documentNotation = aggregate.state.documents.values[testPath]!!

        assertEquals(1, documentNotation.indexOf(ObjectPath.parse("main.addends/NewName")).value)
        assertEquals(2, documentNotation.indexOf(ObjectPath.parse("main.addends/SameName")).value)

        assertEquals("main.addends/NewName",
                aggregate.state.getString(location("main"),
                        AttributePath.parse("addends.0")))
    }


    @Test
    fun `Rename to weird name`() {
        val weirdNameValue = "/"
        val weirdName = ObjectName(weirdNameValue)
        val weirdPath = ObjectPath(weirdName, ObjectNesting.parse("main.addends"))

        val notationTree = GraphTestUtils.readNotation()
        val graphDefinition = GraphTestUtils.grapDefinition(notationTree)

        val aggregate = NotationAggregate(notationTree)

        aggregate.apply(
                RenameObjectRefactorCommand(
                        location("main.addends/OldName"), weirdName),
                graphDefinition)

        val documentNotation = aggregate.state.documents.values[testPath]!!

        assertEquals(location("main.addends/\\/"),
                aggregate.state.coalesce.locate(ObjectReference(weirdName, null, null)))

        assertEquals(1, documentNotation.indexOf(weirdPath).value)

        assertEquals(weirdPath.asString(),
                aggregate.state.getString(location("main"), AttributePath.parse("addends.0")))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun location(objectPath: String): ObjectLocation {
        return ObjectLocation(
                testPath,
                ObjectPath.parse(objectPath))
    }
}