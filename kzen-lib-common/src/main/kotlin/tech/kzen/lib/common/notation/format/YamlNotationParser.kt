package tech.kzen.lib.common.notation.format


import tech.kzen.lib.common.notation.model.*
import tech.kzen.lib.common.notation.io.flat.parser.NotationParser
import tech.kzen.lib.common.util.IoUtils


class YamlNotationParser : NotationParser {
    //-----------------------------------------------------------------------------------------------------------------
    override fun parse(body: ByteArray): PackageNotation {
        val node = YamlNodeParser.parse(body)
//        println("#!@#!@#!@#!@#!@ node = $node")

        @Suppress("IfThenToElvis")
        val topLevelMap =
                if (node is YamlMap) {
                    node
                }
                else if (node is YamlScalar) {
                    if (node is YamlString) {
                        YamlMap(mapOf(
                                "is" to node))
                    }
                    else {
                        // NB: losing information
                        YamlMap(mapOf())
                    }
                }
                else {
                    throw IllegalArgumentException("Top-level map expected: $node")
                }

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


    override fun parseParameter(value: String): ParameterNotation {
        val node = YamlNodeParser.parse(value)
        return yamlToParameter(node)
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
//        println("&%^&%^&%^ -- de-parsing - $notation")

        val buffer = StringBuilder()

        var first = true
        for (entry in notation.objects) {
            if (! first) {
                buffer.append("\n")
            }
            first = false

            val node = objectToYaml(entry.value)
            val nodeLines = node.asString().split("\n")

//            println("&%^&%^&%^ -- de-parsing - ${entry.key} -> $node || ${node.asString()}")

            buffer.append("${entry.key}:")
            nodeLines.forEach { buffer.append("\n  $it") }
        }
//        println("&%^&%^&%^ -- de-parsing done - $buffer")

        return IoUtils.stringToUtf8(buffer.toString())
    }


    private fun objectToYaml(objectNotation: ObjectNotation): YamlNode {
        return YamlMap(objectNotation.parameters.mapValues {
            parameterToYaml(it.value)
        })
    }


    private fun parameterToYaml(parameterNotation: ParameterNotation): YamlNode {
        return when (parameterNotation) {
            is ScalarParameterNotation ->
                YamlNode.ofObject(parameterNotation.value)

            is MapParameterNotation ->
                YamlMap(parameterNotation.values.mapValues { parameterToYaml(it.value) })

            is ListParameterNotation ->
                YamlList(parameterNotation.values.map { parameterToYaml(it) })

            else ->
                throw UnsupportedOperationException("Unexpected type: $parameterNotation")
        }
    }
}
