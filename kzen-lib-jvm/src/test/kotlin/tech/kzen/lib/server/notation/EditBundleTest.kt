package tech.kzen.lib.server.notation

import org.junit.Test
import tech.kzen.lib.common.notation.edit.CreateBundleCommand
import tech.kzen.lib.common.notation.edit.DeletePackageCommand
import tech.kzen.lib.common.notation.edit.NotationAggregate
import tech.kzen.lib.common.notation.model.NotationTree
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class EditBundleTest: NotationAggregateTest() {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Create bundle`() {
        val project = NotationAggregate(NotationTree.empty)

        project.apply(CreateBundleCommand(testPath))

        val packageNotation = project.state.bundleNotations.values[testPath]!!
        assertEquals(0, packageNotation.objects.values.size)
    }


    @Test
    fun `Delete bundle`() {
        val notation = parseTree("")

        val project = NotationAggregate(notation)

        project.apply(DeletePackageCommand(testPath))

        assertTrue(project.state.bundleNotations.values.isEmpty())
    }
}