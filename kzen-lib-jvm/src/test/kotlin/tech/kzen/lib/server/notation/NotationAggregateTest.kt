package tech.kzen.lib.server.notation

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
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
        return GraphNotation(DocumentPathMap(mapOf(
                testPath to packageNotation)))
    }


    fun deparseDocument(graphNotation: GraphNotation): String {
        return deparseDocument(graphNotation.documents.values[testPath]!!)
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