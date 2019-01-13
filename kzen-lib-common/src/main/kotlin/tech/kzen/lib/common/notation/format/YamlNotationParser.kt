package tech.kzen.lib.common.notation.format


import tech.kzen.lib.common.notation.NotationConventions
import tech.kzen.lib.common.notation.io.NotationParser
import tech.kzen.lib.common.notation.model.*
import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.platform.IoUtils


class YamlNotationParser: NotationParser {
    //-----------------------------------------------------------------------------------------------------------------
    override fun parsePackage(body: ByteArray): BundleNotation {
        val node = YamlNodeParser.parse(body)
//        println("#!@#!@#!@#!@#!@ node = $node")

        @Suppress("IfThenToElvis")
        val topLevelMap =
                when (node) {
                    is YamlMap ->
                        node

                    is YamlScalar -> when {
                        node is YamlString ->
                            YamlMap(mapOf(NotationConventions.isAttribute to node))

                        // NB: empty document
                        node.value == null ->
                            YamlMap(mapOf())

                        else ->
                            throw IllegalArgumentException("Top-level non-string scalar: ${node.value}")
                    }
                    else ->
                        throw IllegalArgumentException("Top-level map expected: $node")
                }

        val objects = mutableMapOf<ObjectPath, ObjectNotation>()
        for (e in topLevelMap.values) {
            val objectMap = e.value as YamlMap
            val objectPath = ObjectPath.parse(e.key)
            val objectNotation = parseObjectYaml(objectMap)
            objects[objectPath] = objectNotation
        }
        return BundleNotation(BundleMap(objects))
    }


    private fun parseObjectYaml(objectMap: YamlMap): ObjectNotation {
        val parameters = mutableMapOf<AttributeName, AttributeNotation>()

        for (p in objectMap.values) {
            val parameterNode = objectMap.values[p.key]!!
            val parameter = yamlToParameter(parameterNode)

            val attributeName = AttributeName(p.key)
            parameters[attributeName] = parameter
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


    override fun parseParameter(value: String): AttributeNotation {
        val node = YamlNodeParser.parse(value)
        return yamlToParameter(node)
    }


    private fun yamlToParameter(node: YamlNode): AttributeNotation {
        return when (node) {
            is YamlScalar ->
                ScalarAttributeNotation(node.value)

            is YamlList ->
                ListAttributeNotation(
                        node.values.map { i -> yamlToParameter(i) })

            is YamlMap ->
                MapAttributeNotation(
                        node.values.map { e ->
                            AttributeSegment.ofKey(e.key) to yamlToParameter(e.value)
                        }.toMap())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun deparsePackage(notation: BundleNotation, previousBody: ByteArray): ByteArray {
//        println("&%^&%^&%^ -- de-parsing - $notation")

        val buffer = StringBuilder()

        var first = true
        for (entry in notation.objects.values) {
            if (! first) {
                buffer.append("\n\n")
            }
            first = false

            val node = objectToYaml(entry.value)
            val nodeLines = node.asString().split("\n")

//            println("&%^&%^&%^ -- de-parsing - ${entry.key} -> $node || ${node.asString()}")

            // TODO: consolidate
            val keyPrefix = YamlString(entry.key.name.value).asString()
            buffer.append("$keyPrefix:")

            nodeLines.forEach { buffer.append("\n  $it") }
        }
//        println("&%^&%^&%^ -- de-parsing done - $buffer")

        return IoUtils.stringToUtf8(buffer.toString())
    }


    private fun objectToYaml(objectNotation: ObjectNotation): YamlNode {
        return YamlMap(objectNotation.attributes.map {
            it.key.value to parameterToYaml(it.value)
        }.toMap())
    }


    private fun parameterToYaml(parameterNotation: AttributeNotation): YamlNode {
        return when (parameterNotation) {
            is ScalarAttributeNotation ->
                YamlNode.ofObject(parameterNotation.value)

            is ListAttributeNotation ->
                YamlList(parameterNotation.values.map { parameterToYaml(it) })

            is MapAttributeNotation ->
                YamlMap(parameterNotation.values.map { e ->
                    e.key.asKey() to parameterToYaml(e.value)
                }.toMap())

//            else ->
//                throw UnsupportedOperationException("Unexpected type: $parameterNotation")
        }
    }


    override fun deparseObject(objectNotation: ObjectNotation): String {
        val yaml = objectToYaml(objectNotation)
        return yaml.asString()
    }

    override fun deparseParameter(parameterNotation: AttributeNotation): String {
        val yaml = parameterToYaml(parameterNotation)
        return yaml.asString()
    }
}
