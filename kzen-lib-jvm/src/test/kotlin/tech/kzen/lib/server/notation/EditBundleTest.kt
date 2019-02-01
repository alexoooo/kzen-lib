package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.notation.edit.CreateBundleCommand
import tech.kzen.lib.common.notation.edit.DeleteBundleCommand
import tech.kzen.lib.common.notation.edit.NotationAggregate
import tech.kzen.lib.common.notation.model.GraphNotation
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class EditBundleTest: NotationAggregateTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Create bundle`() {
        val project = NotationAggregate(GraphNotation.empty)

        project.apply(CreateBundleCommand(testPath))

        val packageNotation = project.state.bundles.values[testPath]!!
        assertEquals(0, packageNotation.objects.values.size)
    }


    @Test
    fun `Delete bundle`() {
        val notation = parseGraph("")

        val project = NotationAggregate(notation)

        project.apply(DeleteBundleCommand(testPath))

        assertTrue(project.state.bundles.values.isEmpty())
    }
}