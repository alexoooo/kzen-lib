package tech.kzen.lib.server.notation

import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.notation.format.YamlNotationParser
import tech.kzen.lib.common.notation.model.BundleNotation
import tech.kzen.lib.common.notation.model.NotationTree
import tech.kzen.lib.platform.IoUtils


abstract class NotationAggregateTest {
    //-----------------------------------------------------------------------------------------------------------------
    val yamlParser = YamlNotationParser()
    val testPath = BundlePath.parse("test.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    fun parseBundle(doc: String): BundleNotation {
        return yamlParser.parseBundle(IoUtils.stringToUtf8(doc))
    }


    fun parseTree(doc: String): NotationTree {
        val packageNotation = parseBundle(doc)
        return NotationTree(BundleTree(mapOf(
                testPath to packageNotation)))
    }


    fun location(name: String): ObjectLocation {
        return ObjectLocation(testPath, ObjectPath.parse(name))
    }

    fun attribute(attributePath: String): AttributePath {
        return AttributePath.parse(attributePath)
    }
}