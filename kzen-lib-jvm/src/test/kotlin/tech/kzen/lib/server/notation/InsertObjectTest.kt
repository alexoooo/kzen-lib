package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.api.model.AttributePath
import tech.kzen.lib.common.api.model.ObjectName
import tech.kzen.lib.common.api.model.ObjectPath
import tech.kzen.lib.common.notation.edit.InsertObjectInListAttributeCommand
import tech.kzen.lib.common.notation.edit.NotationAggregate
import tech.kzen.lib.common.notation.model.ObjectNotation
import tech.kzen.lib.common.notation.model.PositionIndex
import kotlin.test.assertEquals


class InsertObjectTest: NotationAggregateTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Insert into existing list`() {
        val notation = parseTree("""
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
                ObjectNotation.ofParent("DoubleValue")
        ))

        val bundleNotation = project.state.bundleNotations.values[testPath]!!

        assertEquals(1, bundleNotation.indexOf(ObjectPath.parse("InsertInto.foo/Inserted")).value)

        assertEquals("InsertInto.foo/Inserted",
                project.state.getString(
                        location("InsertInto"),
                        attribute("foo.1")
                ))
    }
}