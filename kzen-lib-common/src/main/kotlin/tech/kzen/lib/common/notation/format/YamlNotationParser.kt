package tech.kzen.lib.common.notation.format


import tech.kzen.lib.common.notation.io.NotationParser
import tech.kzen.lib.common.notation.model.*
import tech.kzen.lib.common.util.IoUtils


class YamlNotationParser : NotationParser {
    //-----------------------------------------------------------------------------------------------------------------
    override fun parsePackage(body: ByteArray): PackageNotation {
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
                                ParameterConventions.isParameter to node))
                    }
                    else if (node.value == null) {
                        // NB: empty document
                        YamlMap(mapOf())
                    }
                    else {
                        throw IllegalArgumentException("Top-level non-string scalar: ${node.value}")
                    }
                }
                else {
                    throw IllegalArgumentException("Top-level map expected: $node")
                }

        val objects = mutableMapOf<String, ObjectNotation>()
        for (e in topLevelMap.values) {
            val objectMap = e.value as YamlMap
            objects[e.key] = parseObjectYaml(objectMap)
        }

        return PackageNotation(objects)
    }


    private fun parseObjectYaml(objectMap: YamlMap): ObjectNotation {
        val parameters = mutableMapOf<String, ParameterNotation>()

        for (p in objectMap.values) {
            val parameterNode = objectMap.values[p.key]!!
            val parameter = yamlToParameter(parameterNode)
            parameters[p.key] = parameter
        }

        return ObjectNotation(parameters)
    }


    override fun parseObject(value: String): ObjectNotation {
        val node = YamlNodeParser.parse(value)

        if (node !is YamlMap) {
            throw IllegalArgumentException("Map expected: $node")
        }

        return parseObjectYaml(node)
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
    override fun deparsePackage(notation: PackageNotation, previousBody: ByteArray): ByteArray {
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


    override fun deparseObject(objectNotation: ObjectNotation): String {
        val yaml = objectToYaml(objectNotation)
        return yaml.asString()
    }

    override fun deparseParameter(parameterNotation: ParameterNotation): String {
        val yaml = parameterToYaml(parameterNotation)
        return yaml.asString()
    }
}
