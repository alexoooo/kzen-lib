package tech.kzen.lib.server

import org.junit.Test
import tech.kzen.lib.common.edit.EditParameterCommand
import tech.kzen.lib.common.edit.ProjectAggregate
import tech.kzen.lib.common.notation.format.YamlNotationParser
import tech.kzen.lib.common.notation.model.PackageNotation
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.model.ScalarParameterNotation
import tech.kzen.lib.common.util.IoUtils
import kotlin.test.assertEquals


class ProjectAggregateTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val yamlParser = YamlNotationParser()


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `Edit simple param`() {
        val notation = parseProject("""
Foo:
  hello: "bar"
""")

        val project = ProjectAggregate(notation)

        val event = project.apply(EditParameterCommand(
                "Foo",
                "hello",
                ScalarParameterNotation("world")))

        val value = event.state.getString("Foo", "hello")
        assertEquals("world", value)
    }



    //-----------------------------------------------------------------------------------------------------------------
    private fun parsePackage(doc: String): PackageNotation {
        return yamlParser.parse(IoUtils.stringToUtf8(doc))
    }


    private fun parseProject(doc: String): ProjectNotation {
        val packageNotation = parsePackage(doc)
        return ProjectNotation(mapOf(
                ProjectPath("main.yaml") to packageNotation))
    }
}