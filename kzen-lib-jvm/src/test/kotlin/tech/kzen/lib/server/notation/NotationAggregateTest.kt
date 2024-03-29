package tech.kzen.lib.server.notation

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.platform.collect.persistentMapOf


abstract class NotationAggregateTest {
    //-----------------------------------------------------------------------------------------------------------------
    val yamlParser = YamlNotationParser()
    val testPath = DocumentPath.parse("test.yaml")
    val reducer = NotationReducer()


    //-----------------------------------------------------------------------------------------------------------------
    fun parseDocument(doc: String): DocumentObjectNotation {
        return yamlParser.parseDocumentObjects(doc)
    }


    fun parseGraph(doc: String): GraphNotation {
        val packageNotation = parseDocument(doc)
        return GraphNotation(DocumentPathMap(persistentMapOf(
                testPath to DocumentNotation(
                        packageNotation,
                        null))))
    }


    fun unparseDocument(graphNotation: GraphNotation): String {
        return unparseDocument(graphNotation.documents.map[testPath]!!.objects)
    }


    fun unparseDocument(documentNotation: DocumentObjectNotation): String {
        return yamlParser.unparseDocument(documentNotation, "")
    }


    fun location(objectPathAsString: String): ObjectLocation {
        return ObjectLocation(testPath, ObjectPath.parse(objectPathAsString))
    }

    fun attribute(attributePath: String): AttributePath {
        return AttributePath.parse(attributePath)
    }
}