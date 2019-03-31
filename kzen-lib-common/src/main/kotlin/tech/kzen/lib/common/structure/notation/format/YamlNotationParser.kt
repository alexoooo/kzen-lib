package tech.kzen.lib.common.structure.notation.format


import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.AttributeSegment
import tech.kzen.lib.common.api.model.DocumentMap
import tech.kzen.lib.common.api.model.ObjectPath
import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.structure.notation.io.NotationParser
import tech.kzen.lib.common.structure.notation.model.*
import tech.kzen.lib.platform.IoUtils


class YamlNotationParser: NotationParser {
    //-----------------------------------------------------------------------------------------------------------------
    override fun parseDocument(body: ByteArray): DocumentNotation {
        val node = YamlNodeParser.parse(body)
//        println("#!@#!@#!@#!@#!@ node = $node")

        @Suppress("IfThenToElvis")
        val topLevelMap =
                when (node) {
                    is YamlMap ->
                        node

//                    is YamlScalar -> when {
                    is YamlString ->
                        if (node.value.isEmpty()/* || node.value == "null"*/) {
                            YamlMap(mapOf())
                        }
                        else {
                            YamlMap(mapOf(NotationConventions.isKey to node))
                        }

                    else ->
                        throw IllegalArgumentException("Top-level map expected: $node")
                }

        val objects = mutableMapOf<ObjectPath, ObjectNotation>()
        for (e in topLevelMap.values) {
            val objectMap = e.value
                    as? YamlMap
                    ?: throw IllegalArgumentException("Sub-map expected: ${e.key} - ${e.value.asString()}")

            if (objectMap.values.isEmpty()) {
                continue
            }

            val objectPath = ObjectPath.parse(e.key)
            val objectNotation = parseObjectYaml(objectMap)
            objects[objectPath] = objectNotation
        }
        return DocumentNotation(DocumentMap(objects))
    }


    private fun parseObjectYaml(objectMap: YamlMap): ObjectNotation {
        val attributes = mutableMapOf<AttributeName, AttributeNotation>()

        for (p in objectMap.values) {
            val attributeNode = objectMap.values[p.key]!!
            val attribute = yamlToAttribute(attributeNode)

            val attributeName = AttributeName(p.key)
            attributes[attributeName] = attribute
        }

        return ObjectNotation(attributes)
    }


    override fun parseObject(value: String): ObjectNotation {
        val node = YamlNodeParser.parse(value)

        if (node !is YamlMap) {
            throw IllegalArgumentException("Map expected: $node")
        }

        return parseObjectYaml(node)
    }


    override fun parseAttribute(value: String): AttributeNotation {
        val node = YamlNodeParser.parse(value)
        return yamlToAttribute(node)
    }


    private fun yamlToAttribute(node: YamlNode): AttributeNotation {
        return when (node) {
//            is YamlScalar ->
//                ScalarAttributeNotation(node.value)
            is YamlString ->
                ScalarAttributeNotation(node.value)

            is YamlList ->
                ListAttributeNotation(
                        node.values.map { i -> yamlToAttribute(i) })

            is YamlMap ->
                MapAttributeNotation(
                        node.values.map { e ->
                            AttributeSegment.ofKey(e.key) to yamlToAttribute(e.value)
                        }.toMap())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun deparseDocument(notation: DocumentNotation, previousBody: ByteArray): ByteArray {
//        println("&%^&%^&%^ -- de-parsing - $notation")

        val buffer = StringBuilder()

        var first = true
        for ((objectPath, objectNotation) in notation.objects.values) {
            if (! first) {
                buffer.append("\n\n")
            }
            first = false

            val node = objectToYaml(objectNotation)
            val nodeLines = node.asString().split("\n")

//            println("&%^&%^&%^ -- de-parsing - ${entry.key} -> $node || ${node.asString()}")

            val keyPrefix = YamlString(objectPath.asString()).asString()
            buffer.append("$keyPrefix:")

            nodeLines.forEach { buffer.append("\n  $it") }
        }
//        println("&%^&%^&%^ -- de-parsing done - $buffer")

        return IoUtils.utf8Encode(buffer.toString())
    }


    private fun objectToYaml(objectNotation: ObjectNotation): YamlNode {
        return YamlMap(objectNotation.attributes.map {
            it.key.value to attributeToYaml(it.value)
        }.toMap())
    }


    private fun attributeToYaml(attributeNotation: AttributeNotation): YamlNode {
        return when (attributeNotation) {
            is ScalarAttributeNotation ->
                YamlNode.ofObject(attributeNotation.value)

            is ListAttributeNotation ->
                YamlList(attributeNotation.values.map { attributeToYaml(it) })

            is MapAttributeNotation ->
                YamlMap(attributeNotation.values.map { e ->
                    e.key.asKey() to attributeToYaml(e.value)
                }.toMap())

//            else ->
//                throw UnsupportedOperationException("Unexpected type: $parameterNotation")
        }
    }


    override fun deparseObject(objectNotation: ObjectNotation): String {
        val yaml = objectToYaml(objectNotation)
        return yaml.asString()
    }

    override fun deparseAttribute(attributeNotation: AttributeNotation): String {
        val yaml = attributeToYaml(attributeNotation)
        return yaml.asString()
    }
}
