package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertObjectInListAttributeCommand
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

        val transition = reducer.applyStructural(
            notation,
            InsertObjectInListAttributeCommand(
                location("InsertInto"),
                AttributePath.parse("foo"),
                PositionRelation.at(1),
                ObjectName("Inserted"),
                PositionRelation.at(1),
                ObjectNotation.ofParent(ObjectName("DoubleValue"))))

        val documentNotation = transition.graphNotation.documents[testPath]!!

        assertEquals(1, documentNotation.indexOf(
            ObjectPath.parse("InsertInto.foo/Inserted")
        ).value)

        assertEquals("InsertInto.foo/Inserted",
            transition.graphNotation.getString(
                location("InsertInto"),
                attribute("foo.1")
            ))

        val unparsed = unparseDocument(documentNotation.objects)
        assertEquals("""
InsertInto:
  foo:
    - Bar
    - InsertInto.foo/Inserted

InsertInto.foo/Inserted:
  is: DoubleValue
""".trimIndent(), unparsed)
    }
}