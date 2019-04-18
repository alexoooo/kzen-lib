package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.structure.notation.edit.InsertObjectInListAttributeCommand
import tech.kzen.lib.common.structure.notation.edit.NotationAggregate
import tech.kzen.lib.common.structure.notation.model.ObjectNotation
import tech.kzen.lib.common.structure.notation.model.PositionIndex
import kotlin.test.assertEquals


class InsertObjectTest: NotationAggregateTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Insert into existing list`() {
        val notation = parseGraph("""
InsertInto:
  foo:
  - Bar
""")

        val project = NotationAggregate(notation)

        project.apply(InsertObjectInListAttributeCommand(
                location("InsertInto"),
                AttributePath.parse("foo"),
                PositionIndex(1),
                ObjectName("Inserted"),
                PositionIndex(1),
                ObjectNotation.ofParent(ObjectName("DoubleValue"))
        ))

        val documentNotation = project.state.documents.get(testPath)!!

        assertEquals(1, documentNotation.indexOf(
                ObjectPath.parse("InsertInto.foo/Inserted")
        ).value)

        assertEquals("InsertInto.foo/Inserted",
                project.state.getString(
                        location("InsertInto"),
                        attribute("foo.1")
                ))

        val deparsed = deparseDocument(documentNotation)
        assertEquals("""
InsertInto:
  foo:
    - Bar
    - "InsertInto.foo/Inserted"

"InsertInto.foo/Inserted":
  is: DoubleValue
""".trimIndent(), deparsed)
    }
}