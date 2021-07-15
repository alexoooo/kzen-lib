package tech.kzen.lib.common.notation

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.platform.collect.persistentMapOf


abstract class StructuralNotationTest {
    //-----------------------------------------------------------------------------------------------------------------
    val yamlParser = YamlNotationParser()
    val testPath = DocumentPath.parse("test.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    fun parseDocumentObjects(doc: String): DocumentObjectNotation {
        return yamlParser.parseDocumentObjects(doc)
    }


    fun parseGraph(doc: String): GraphNotation {
        val notations = parseDocumentObjects(doc)
        return GraphNotation(DocumentPathMap(persistentMapOf(
                testPath to DocumentNotation(
                        notations,
                        null))))
    }


    fun unparseDocument(notationTree: GraphNotation): String {
        return unparseDocument(notationTree.documents.values[testPath]!!.objects)
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