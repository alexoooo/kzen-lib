package tech.kzen.lib.common.notation.read.yaml


import tech.kzen.lib.common.notation.model.*
import tech.kzen.lib.common.notation.read.flat.parser.NotationParser


class YamlNotationParser : NotationParser {
//    private object Patterns {
//        val lineBreak = Regex(
//                "\r\n|\n")
//
//        val namePath = Regex(
////                "(\\w(\\w|\\d)+(\\.\\w(\\w|\\d))*):\\s*")
//                "(\\w+):\\s*")
//
//        val decorator = Regex(
//                "$|#(.*)")
////                "|.*")
//
//        val entry = Regex(
////                "\\s+(\\w(\\w|\\d)+):\\s*(.*?)\\s*")
////                "\\s+(@?(?:\\w|\\d)+):\\s*(.*?)\\s*")
//                "([0-9a-zA-Z_-]+):.*")
//
//        val item = Regex(
//                "- .*")
//
//    }

    override fun parse(body: ByteArray): PackageNotation {
        val node = YamlNodeParser.parse(body)

        val topLevelMap = node as YamlMap

        val objects = mutableMapOf<String, ObjectNotation>()
        for (e in topLevelMap.values) {
            val objectMap = e.value as YamlMap

            val parameters = mutableMapOf<String, ParameterNotation>()
            for (p in objectMap.values) {
                val parameterNode = objectMap.values[p.key]!!
                val parameter = yamlToParameter(parameterNode)
                parameters[p.key] = parameter
            }

            objects[e.key] = ObjectNotation(parameters)
        }

        return PackageNotation(objects)

//        val declarations = splitDeclarations(lines)
//        println("declarations: $declarations")
////        val declarations = listOf(lines)
//
//        val stumps = declarations.map { parseDeclaration(it) }
//
//        return PackageNotation(stumps)
    }


    private fun yamlToParameter(node: YamlNode): ParameterNotation {
        return when (node) {
            is YamlScalar ->
                ScalarParameterNotation(node.value)

            is YamlList ->
                ListParameterNotation(
                        node.values.map { i -> yamlToParameter(i) })

            is YamlMap ->
                MapParameterNotation(
                        node.values.mapValues { e -> yamlToParameter(e.value)})
        }
    }
}
