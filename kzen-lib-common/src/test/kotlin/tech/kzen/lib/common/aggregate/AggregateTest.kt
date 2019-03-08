package tech.kzen.lib.common.aggregate

import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.structure.notation.format.YamlNotationParser
import tech.kzen.lib.common.structure.notation.model.BundleNotation
import tech.kzen.lib.common.structure.notation.model.GraphNotation
import tech.kzen.lib.platform.IoUtils


abstract class AggregateTest {
    //-----------------------------------------------------------------------------------------------------------------
    val yamlParser = YamlNotationParser()
    val testPath = BundlePath.parse("test.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    fun parseBundle(doc: String): BundleNotation {
        return yamlParser.parseBundle(IoUtils.utf8Encode(doc))
    }


    fun parseGraph(doc: String): GraphNotation {
        val packageNotation = parseBundle(doc)
        return GraphNotation(BundleTree(mapOf(
                testPath to packageNotation)))
    }


    fun deparseBundle(notationTree: GraphNotation): String {
        return deparseBundle(notationTree.bundles.values[testPath]!!)
    }


    fun deparseBundle(bundleNotation: BundleNotation): String {
        return IoUtils.utf8Decode(
                yamlParser.deparseBundle(bundleNotation, ByteArray(0)))
    }


    fun location(objectPathAsString: String): ObjectLocation {
        return ObjectLocation(testPath, ObjectPath.parse(objectPathAsString))
    }


    fun attribute(attributePath: String): AttributePath {
        return AttributePath.parse(attributePath)
    }
}