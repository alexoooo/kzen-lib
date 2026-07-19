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
        @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
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
        if (previousDocument.isBlank()) {
            return fullUnparseDocument(notation)
        }

        return try {
            formatPreservingUnparseDocument(notation, previousDocument)
        }
        catch (e: Exception) {
            fullUnparseDocument(notation)
        }
    }


    private fun fullUnparseDocument(notation: DocumentObjectNotation): String {
        return notation.notations.map.entries.joinToString("\n\n") {
            houseSerializeObject(it.key, it.value)
        }
    }


    // Serialize one object in the canonical house format: `key:` followed by its 2-space-indented attribute body.
    private fun houseSerializeObject(objectPath: ObjectPath, objectNotation: ObjectNotation): String {
        val node = objectToYaml(objectNotation)
        val nodeLines = YamlParser.unparse(node).split("\n")
        val keyPrefix = YamlParser.unparseKey(objectPath.asString())
        val body = nodeLines.joinToString("") { if (it.isEmpty()) "\n" else "\n  $it" }
        return "$keyPrefix:$body"
    }


    // Rewrite the document while preserving the verbatim text (comments, blank lines, formatting) of every object
    // whose parsed notation is unchanged from previousDocument. Only changed/added objects are re-serialized in the
    // house format; leading document comments are preserved; objects are re-emitted in the NEW notation's order.
    // Comments INSIDE a changed object are lost (accepted), and inter-object blank spacing normalizes to one line.
    private fun formatPreservingUnparseDocument(
        notation: DocumentObjectNotation,
        previousDocument: String
    ): String {
        val template = splitPreviousDocument(previousDocument)

        val blocks = mutableListOf<String>()

        val prefix = trimBlankEdges(template.prefix)
        if (prefix.isNotEmpty()) {
            blocks.add(prefix)
        }

        for ((objectPath, objectNotation) in notation.notations.map) {
            val previous = template.byPath[objectPath]
            if (previous != null && previous.objectNotation == objectNotation) {
                blocks.add(trimBlankEdges(previous.text))
            }
            else {
                blocks.add(houseSerializeObject(objectPath, objectNotation))
            }
        }

        return blocks.filter { it.isNotEmpty() }.joinToString("\n\n")
    }


    private class PreviousDocumentTemplate(
        val prefix: String,
        val byPath: Map<ObjectPath, PreviousObjectSegment>
    )


    private class PreviousObjectSegment(
        val text: String,
        val objectNotation: ObjectNotation
    )


    // Split previousDocument into per-top-level-object text segments. A boundary is a column-0, non-blank,
    // non-comment line (an object key). Each object's segment carries its own leading blank/comment run (comments
    // attach to the following object, matching the YAML parser); everything before the first object is the prefix.
    // Each segment is parsed on its own so path <-> text <-> notation come from a single source; a segment that
    // does not parse to exactly one object (empty body, malformed) is simply not preserved.
    private fun splitPreviousDocument(previousDocument: String): PreviousDocumentTemplate {
        val lines = previousDocument.split("\n").map { it.removeSuffix("\r") }

        val boundaryIndices = lines.indices.filter { isObjectBoundaryLine(lines[it]) }
        if (boundaryIndices.isEmpty()) {
            return PreviousDocumentTemplate(previousDocument, mapOf())
        }

        fun leadingRunStart(boundaryIndex: Int): Int {
            var i = boundaryIndex
            while (i > 0 && (lines[i - 1].isBlank() || isCommentLine(lines[i - 1]))) {
                i--
            }
            return i
        }

        val firstBoundary = boundaryIndices.first()
        val prefix = lines.subList(0, firstBoundary).joinToString("\n")

        val byPath = LinkedHashMap<ObjectPath, PreviousObjectSegment>()
        for ((position, boundaryIndex) in boundaryIndices.withIndex()) {
            val segmentStart =
                if (position == 0) {
                    boundaryIndex
                }
                else {
                    leadingRunStart(boundaryIndex)
                }
            val segmentEnd =
                if (position + 1 < boundaryIndices.size) {
                    leadingRunStart(boundaryIndices[position + 1])
                }
                else {
                    lines.size
                }

            val segmentText = lines.subList(segmentStart, segmentEnd).joinToString("\n")

            val parsed =
                try {
                    parseDocumentObjects(segmentText).notations.map.entries.singleOrNull()
                }
                catch (e: Exception) {
                    null
                }
                ?: continue

            byPath[parsed.key] = PreviousObjectSegment(segmentText, parsed.value)
        }

        return PreviousDocumentTemplate(prefix, byPath)
    }


    private fun isObjectBoundaryLine(line: String): Boolean {
        return line.isNotEmpty() && !line[0].isWhitespace() && !line.startsWith("#")
    }


    private fun isCommentLine(line: String): Boolean {
        return line.trimStart().startsWith("#")
    }


    private fun trimBlankEdges(text: String): String {
        val lines = text.split("\n")
        var start = 0
        var end = lines.size
        while (start < end && lines[start].isBlank()) {
            start++
        }
        while (end > start && lines[end - 1].isBlank()) {
            end--
        }
        return lines.subList(start, end).joinToString("\n")
    }


    private fun objectToYaml(objectNotation: ObjectNotation): YamlNode {
        return YamlMap(objectNotation.attributes.map.map {
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
                YamlMap(attributeNotation.map.map { e ->
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
