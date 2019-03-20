package tech.kzen.lib.server.notation

import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.structure.notation.format.YamlNotationParser
import tech.kzen.lib.common.structure.notation.model.DocumentNotation
import tech.kzen.lib.common.structure.notation.model.GraphNotation
import tech.kzen.lib.platform.IoUtils


abstract class NotationAggregateTest {
    //-----------------------------------------------------------------------------------------------------------------
    val yamlParser = YamlNotationParser()
    val testPath = DocumentPath.parse("test.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    fun parseDocument(doc: String): DocumentNotation {
        return yamlParser.parseDocument(IoUtils.utf8Encode(doc))
    }


    fun parseGraph(doc: String): GraphNotation {
        val packageNotation = parseDocument(doc)
        return GraphNotation(DocumentTree(mapOf(
                testPath to packageNotation)))
    }


    fun deparseDocument(notationTree: GraphNotation): String {
        return deparseDocument(notationTree.documents.values[testPath]!!)
    }


    fun deparseDocument(documentNotation: DocumentNotation): String {
        return IoUtils.utf8Decode(
                yamlParser.deparseDocument(documentNotation, ByteArray(0)))
    }


    fun location(objectPathAsString: String): ObjectLocation {
        return ObjectLocation(testPath, ObjectPath.parse(objectPathAsString))
    }

    fun attribute(attributePath: String): AttributePath {
        return AttributePath.parse(attributePath)
    }
}