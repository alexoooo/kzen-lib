package tech.kzen.lib.common.notation.read.yaml


import tech.kzen.lib.common.notation.model.*
import tech.kzen.lib.common.notation.read.flat.parser.NotationParser


class YamlNotationParser : NotationParser {
    //-----------------------------------------------------------------------------------------------------------------
    override fun parse(body: ByteArray): PackageNotation {
        val node = YamlNodeParser.parse(body)

        val topLevelMap = node as YamlMap

        val objects = mutableMapOf<String, ObjectNotation>()
        for (e in topLevelMap.values) {
            val objectMap = e.value as YamlMap

            val parameters = mutableMapOf<String, ParameterNotation>()
            for (p in objectMap.values) {
                val parameterNode = objectMap.values[p.key]!!
                val parameter = yamlToParameter(parameterNode)
                parameters[p.key] = parameter
            }

            objects[e.key] = ObjectNotation(parameters)
        }

        return PackageNotation(objects)
    }


    private fun yamlToParameter(node: YamlNode): ParameterNotation {
        return when (node) {
            is YamlScalar ->
                ScalarParameterNotation(node.value)

            is YamlList ->
                ListParameterNotation(
                        node.values.map { i -> yamlToParameter(i) })

            is YamlMap ->
                MapParameterNotation(
                        node.values.mapValues { e -> yamlToParameter(e.value)})
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun deparse(notation: PackageNotation, previousBody: ByteArray): ByteArray {
        TODO()
    }
}
