package tech.kzen.lib.common.aggregate

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.obj.ObjectPathMap
import tech.kzen.lib.common.structure.notation.format.YamlNotationParser
import tech.kzen.lib.common.structure.notation.model.DocumentNotation
import tech.kzen.lib.common.structure.notation.model.GraphNotation
import tech.kzen.lib.common.structure.notation.model.ObjectNotation
import tech.kzen.lib.platform.IoUtils
import tech.kzen.lib.platform.collect.persistentMapOf


abstract class AggregateTest {
    //-----------------------------------------------------------------------------------------------------------------
    val yamlParser = YamlNotationParser()
    val testPath = DocumentPath.parse("test.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    fun parseDocumentObjects(doc: String): ObjectPathMap<ObjectNotation> {
        return yamlParser.parseDocumentObjects(IoUtils.utf8Encode(doc))
    }


    fun parseGraph(doc: String): GraphNotation {
        val packageNotation = parseDocumentObjects(doc)
        return GraphNotation(DocumentPathMap(persistentMapOf(
                testPath to DocumentNotation(
                        packageNotation,
                        null))))
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