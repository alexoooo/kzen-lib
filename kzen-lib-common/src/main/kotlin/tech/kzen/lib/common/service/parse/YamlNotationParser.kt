package tech.kzen.lib.common.service.parse


import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.obj.ObjectPathMap
import tech.kzen.lib.common.model.structure.notation.*
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.util.yaml.*
import tech.kzen.lib.platform.collect.toPersistentList
import tech.kzen.lib.platform.collect.toPersistentMap


class YamlNotationParser: NotationParser {
    //-----------------------------------------------------------------------------------------------------------------
    override fun parseDocumentObjects(
            document: String
    ): DocumentObjectNotation {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val node = YamlParser.parse(document)

        val topLevelMap =
                when (node) {
                    is YamlMap ->
                        node

                    is YamlString ->
                        if (node.value.isEmpty()) {
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
                    ?: throw IllegalArgumentException(
                            "Sub-map expected: ${e.key} - ${YamlParser.unparse(e.value)}")

            if (objectMap.values.isEmpty()) {
                continue
            }

            val objectPath = ObjectPath.parse(e.key)
            val objectNotation = parseObjectYaml(objectMap)
            objects[objectPath] = objectNotation
        }
        return DocumentObjectNotation(ObjectPathMap(objects.toPersistentMap()))
    }


    private fun parseObjectYaml(objectMap: YamlMap): ObjectNotation {
        val attributes = mutableMapOf<AttributeName, AttributeNotation>()

        for ((attributeNameKey, attributeNode) in objectMap.values) {
            val attribute = yamlToAttribute(attributeNode)

            val attributeName = AttributeName(attributeNameKey)
            attributes[attributeName] = attribute
        }

        return ObjectNotation(AttributeNameMap(attributes.toPersistentMap()))
    }


    override fun parseObject(value: String): ObjectNotation {
        val node = YamlParser.parse(value)

        require(node is YamlMap) { "Map expected: $node" }

        return parseObjectYaml(node)
    }


    override fun parseAttribute(value: String): AttributeNotation {
        val node = YamlParser.parse(value)
        return yamlToAttribute(node)
    }


    private fun yamlToAttribute(node: YamlNode): AttributeNotation {
        return when (node) {
            is YamlString ->
                ScalarAttributeNotation(node.value)

            is YamlList ->
                ListAttributeNotation(
                        node.values.map { i ->
                            yamlToAttribute(i)
                        }.toPersistentList())

            is YamlMap ->
                MapAttributeNotation(
                        node.values.map { e ->
                            AttributeSegment.ofKey(e.key) to yamlToAttribute(e.value)
                        }.toPersistentMap())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun unparseDocument(notation: DocumentObjectNotation, previousDocument: String): String {
        val buffer = StringBuilder()

        var first = true
        for ((objectPath, objectNotation) in notation.notations.values) {
            if (! first) {
                buffer.append("\n\n")
            }
            first = false

            val node = objectToYaml(objectNotation)
            val nodeLines = YamlParser.unparse(node).split("\n")

            val keyPrefix = YamlParser.unparse(YamlString(objectPath.asString()))
            buffer.append("$keyPrefix:")

            nodeLines.forEach { buffer.append("\n  $it") }
        }

        return buffer.toString()
    }


    private fun objectToYaml(objectNotation: ObjectNotation): YamlNode {
        return YamlMap(objectNotation.attributes.values.map {
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
        }
    }


    override fun unparseObject(objectNotation: ObjectNotation): String {
        val yaml = objectToYaml(objectNotation)
        return YamlParser.unparse(yaml)
    }


    override fun unparseAttribute(attributeNotation: AttributeNotation): String {
        val yaml = attributeToYaml(attributeNotation)
        return YamlParser.unparse(yaml)
    }
}
