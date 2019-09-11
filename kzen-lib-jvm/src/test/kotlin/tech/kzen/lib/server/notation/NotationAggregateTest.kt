package tech.kzen.lib.server.notation

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.obj.ObjectPathMap
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.platform.collect.persistentMapOf


abstract class NotationAggregateTest {
    //-----------------------------------------------------------------------------------------------------------------
    val yamlParser = YamlNotationParser()
    val testPath = DocumentPath.parse("test.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    fun parseDocument(doc: String): ObjectPathMap<ObjectNotation> {
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
        return unparseDocument(graphNotation.documents.values[testPath]!!)
    }


    fun unparseDocument(documentNotation: DocumentNotation): String {
        return yamlParser.unparseDocument(documentNotation, "")
    }


    fun location(objectPathAsString: String): ObjectLocation {
        return ObjectLocation(testPath, ObjectPath.parse(objectPathAsString))
    }

    fun attribute(attributePath: String): AttributePath {
        return AttributePath.parse(attributePath)
    }
}