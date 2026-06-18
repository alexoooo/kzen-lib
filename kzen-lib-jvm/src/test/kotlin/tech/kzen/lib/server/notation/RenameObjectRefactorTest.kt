package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceName
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.cqrs.RenameObjectRefactorCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.server.util.JvmGraphTestUtils
import kotlin.test.assertEquals


class RenameObjectRefactorTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val testPath = DocumentPath.parse("test/refactor-test.yaml")
    private val reducer = NotationReducer


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Rename should update references`() {
        val notationTree = JvmGraphTestUtils.readNotation()
        val graphDefinitionAttempt = JvmGraphTestUtils.graphDefinition(notationTree)

        val transition = reducer.applySemantic(
            graphDefinitionAttempt,
            RenameObjectRefactorCommand(
                location("main.addends/OldName"), ObjectName("NewName")))

        val documentNotation = transition.graphNotation.documents.map[testPath]!!

        assertEquals(1, documentNotation.indexOf(ObjectPath.parse("main.addends/NewName")).value)
        assertEquals(2, documentNotation.indexOf(ObjectPath.parse("main.addends/SameName")).value)

        assertEquals("main.addends/NewName",
            transition.graphNotation.getString(location("main"),
                AttributePath.parse("addends.0")))
    }


    @Test
    fun `Rename to weird name`() {
        val weirdNameValue = "/"
        val weirdName = ObjectName(weirdNameValue)
        val weirdPath = ObjectPath(weirdName, ObjectNesting.parse("main.addends"))

        val notationTree = JvmGraphTestUtils.readNotation()
        val graphDefinitionAttempt = JvmGraphTestUtils.graphDefinition(notationTree)

        val transition = reducer.applySemantic(
            graphDefinitionAttempt,
            RenameObjectRefactorCommand(
                location("main.addends/OldName"), weirdName))

        val documentNotation = transition.graphNotation.documents.map[testPath]!!

        assertEquals(location("main.addends/\\/"),
            transition.graphNotation.coalesce.locate(ObjectReference(
                ObjectReferenceName.of(weirdName), weirdPath.nesting, null)))

        assertEquals(1, documentNotation.indexOf(weirdPath).value)

        assertEquals(weirdPath.asString(),
            transition.graphNotation.getString(
                location("main"), AttributePath.parse("addends.0")))
    }


    @Test
    fun `Rename container should renamed nested objects`() {
        val notationTree = JvmGraphTestUtils.readNotation()
        val graphDefinitionAttempt = JvmGraphTestUtils.graphDefinition(notationTree)

        val transition = reducer.applySemantic(
            graphDefinitionAttempt,
            RenameObjectRefactorCommand(
                location("main"), ObjectName("foo")))

        val documentNotation = transition.graphNotation.documents.map[testPath]!!

        assertEquals(1, documentNotation.indexOf(ObjectPath.parse("foo.addends/OldName")).value)
        assertEquals(2, documentNotation.indexOf(ObjectPath.parse("foo.addends/SameName")).value)

        assertEquals("foo.addends/OldName",
            transition.graphNotation.getString(location("foo"),
                AttributePath.parse("addends.0")))
    }


    @Test
    fun `Rename container should preserve grandchild nesting`() {
        val nestedPath = DocumentPath.parse("test/refactor-nested-test.yaml")

        val notationTree = JvmGraphTestUtils.readNotation()
        val graphDefinitionAttempt = JvmGraphTestUtils.graphDefinition(notationTree)

        val transition = reducer.applySemantic(
            graphDefinitionAttempt,
            RenameObjectRefactorCommand(
                ObjectLocation(nestedPath, ObjectPath.parse("main.children/Outer")),
                ObjectName("Renamed")))

        val documentNotation = transition.graphNotation.documents.map[nestedPath]!!
        val objectPaths = documentNotation.objects.notations.map.keys

        // The middle child renames, and crucially the grandchild keeps its full nesting under it — before the
        // fix the Middle segment was dropped, re-parenting Leaf directly under Renamed.
        assertEquals(true, objectPaths.contains(
            ObjectPath.parse("main.children/Renamed.children/Middle")))
        assertEquals(true, objectPaths.contains(
            ObjectPath.parse("main.children/Renamed.children/Middle.children/Leaf")))
        assertEquals(false, objectPaths.contains(
            ObjectPath.parse("main.children/Renamed.children/Leaf")))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Rename should update references in partial object`() {
        val notationTree = JvmGraphTestUtils.readNotation()
        val graphDefinitionAttempt = JvmGraphTestUtils.graphDefinition(notationTree)

        val transition = reducer.applySemantic(
            graphDefinitionAttempt,
            RenameObjectRefactorCommand(
                location("main.addends/OldName"), ObjectName("NewName")))

        val documentNotation = transition.graphNotation.documents.map[testPath]!!

        val partialDivisionDividend =
            documentNotation.objects.notations[ObjectPath.parse("PartialDivisionDividend")]!!

        val dividendDependency = partialDivisionDividend.get(AttributeName("dividend"))?.asString()!!

        assertEquals("main.addends/NewName", dividendDependency)

        val partialDivisionDivisor =
            documentNotation.objects.notations[ObjectPath.parse("PartialDivisionDivisor")]!!

        val divisorDependency = partialDivisionDivisor.get(AttributeName("divisor"))?.asString()!!

        assertEquals("main.addends/NewName", divisorDependency)
    }


    @Test
    fun `Rename should update is references`() {
        val notationTree = JvmGraphTestUtils.readNotation()
        val graphDefinitionAttempt = JvmGraphTestUtils.graphDefinition(notationTree)

        val transition = reducer.applySemantic(
            graphDefinitionAttempt,
            RenameObjectRefactorCommand(
                location("DivideOperation"), ObjectName("RenamedDivideOperation")))

        assertEquals("RenamedDivideOperation",
            transition.graphNotation.getString(
                location("CompleteDivision"), AttributePath.parse("is")))

        assertEquals("RenamedDivideOperation",
            transition.graphNotation.getString(
                location("PartialDivisionDividend"), AttributePath.parse("is")))

        assertEquals("RenamedDivideOperation",
            transition.graphNotation.getString(
                location("PartialDivisionDivisor"), AttributePath.parse("is")))

        assertEquals("RenamedDivideOperation",
            transition.graphNotation.getString(
                location("MetaIsHolder"),
                AttributePath.parse("meta.embeddedDivider.is")))

        assertEquals("RenamedDivideOperation",
            transition.graphNotation.getString(
                location("MetaIsHolder"),
                AttributePath.parse("meta.scalarDivider")))
    }


    @Test
    fun `Rename should update is references in a list`() {
        val notationTree = JvmGraphTestUtils.readNotation()
        val graphDefinitionAttempt = JvmGraphTestUtils.graphDefinition(notationTree)

        val transition = reducer.applySemantic(
            graphDefinitionAttempt,
            RenameObjectRefactorCommand(
                location("DivideOperation"), ObjectName("RenamedDivideOperation")))

        // NB: the renamed parent is the first element of the multiple-inheritance 'is' list
        assertEquals("RenamedDivideOperation",
            transition.graphNotation.getString(
                location("MultipleInheritanceChild"), AttributePath.parse("is.0")))
    }


    @Test
    fun `Rename should update references to partial object`() {
        val notationTree = JvmGraphTestUtils.readNotation()
        val graphDefinitionAttempt = JvmGraphTestUtils.graphDefinition(notationTree)

        val transition = reducer.applySemantic(
            graphDefinitionAttempt,
            RenameObjectRefactorCommand(
                location("PartialDivisionDividend"), ObjectName("NewName")))

        val documentNotation = transition.graphNotation.documents.map[testPath]!!

        val divisionOfPartial =
            documentNotation.objects.notations[ObjectPath.parse("DivisionOfPartial")]!!

        val dividendDependency = divisionOfPartial.get(AttributeName("dividend"))?.asString()!!

        assertEquals("NewName", dividendDependency)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Rename object referenced only by a synthetic nested list should not fail`() {
        val notationTree = JvmGraphTestUtils.readNotation()
        val graphDefinitionAttempt = JvmGraphTestUtils.graphDefinition(notationTree)

        val autowiredPath = DocumentPath.parse("test/autowired.yaml")

        // 'Second' is nested under NestedHolder.children, which is an auto-wired NestedList with no notation
        // backing. Before the fix the refactor tried to rewrite that synthetic list reference and threw
        // "Not found: NestedHolder - children"; now it is skipped (the list re-derives from object paths).
        val transition = reducer.applySemantic(
            graphDefinitionAttempt,
            RenameObjectRefactorCommand(
                ObjectLocation(autowiredPath, ObjectPath.parse("NestedHolder.children/Second")),
                ObjectName("Renamed")))

        val documentNotation = transition.graphNotation.documents.map[autowiredPath]!!
        assertEquals(false, documentNotation.objects.notations.map
            .containsKey(ObjectPath.parse("NestedHolder.children/Second")))
        assertEquals(true, documentNotation.objects.notations.map
            .containsKey(ObjectPath.parse("NestedHolder.children/Renamed")))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun location(objectPath: String): ObjectLocation {
        return ObjectLocation(
            testPath,
            ObjectPath.parse(objectPath))
    }
}