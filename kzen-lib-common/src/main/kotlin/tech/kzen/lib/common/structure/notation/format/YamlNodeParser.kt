package tech.kzen.lib.common.structure.notation.format

import tech.kzen.lib.platform.IoUtils


/**
 * Similar to: https://github.com/crdoconnor/strictyaml/
 */
object YamlNodeParser {
    //-----------------------------------------------------------------------------------------------------------------
    private object Patterns {
        val lineBreak = Regex(
                "\r\n|\n")

        val decorator = Regex(
                "#(.*)")

        // https://stackoverflow.com/questions/32155133/regex-to-match-a-json-string
        // https://stackoverflow.com/questions/4264877/why-is-the-slash-an-escapable-character-in-json
        private const val bareString = "([0-9a-zA-Z_\\-/.]+)"
        private const val doubleQuotedString =
                "\"((?:[^\\\"]|\\(?:[\"\\/bfnrt]|u[0-9a-fA-F]{4})*)\""

        private const val entrySuffix = "\\s*:\\s*(.*)"

        val entryBare = Regex(
                "$bareString$entrySuffix")

        val entryDoubleQuoted = Regex(
                "$doubleQuotedString$entrySuffix")

        val item = Regex(
                "- .*")

        const val emptyListJson = "[]"
        const val emptyMapJson = "{}"
    }


    private enum class NotationStructure {
        Scalar,
        List,
        Map
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun parse(body: ByteArray): YamlNode {
        val document = IoUtils.utf8Decode(body)
        return parse(document)
    }


    fun parse(document: String): YamlNode {
        val lines = document.split(Patterns.lineBreak)
        return parse(lines)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun parse(block: List<String>): YamlNode {
        val structure = identifyStructure(block)
//        println("&&&&&&&&&& structure: $structure - $block")

        return when (structure) {
            NotationStructure.Scalar ->
                parseScalar(block)

            NotationStructure.List ->
                parseList(block)

            NotationStructure.Map ->
                parseMap(block)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun parseScalar(block: List<String>): YamlScalar {
        if (block.isEmpty()) {
            return YamlString.empty
        }

        val value: String =
                if (block.size == 1) {
                    block[0]
                }
                else {
                    val nonCommentLines = block.filter {
                        ! it.isEmpty() &&
                                Patterns.decorator.matchEntire(it) == null
                    }

                    if (nonCommentLines.isEmpty()) {
                        return YamlString.empty
                    }

                    check(nonCommentLines.size == 1) { "Scalar expected: $nonCommentLines" }

                    nonCommentLines[0]
                }

        if (value.equals("null", true) || value.isEmpty()) {
            return YamlNull
        }

        if (value.equals("true", true)) {
            return YamlTrue
        }

        if (value.equals("false", true)) {
            return YamlFalse
        }

        val asLong = value.toLongOrNull()
        if (asLong != null) {
            return YamlLong(asLong)
        }

        val asDouble = value.toDoubleOrNull()
        if (asDouble != null) {
            return YamlDouble(asDouble)
        }

        return YamlUtils.parseString(value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun parseList(block: List<String>): YamlList {
//        println("^^^ parseList: $block")

        if (block.size == 1 && block[0] == Patterns.emptyListJson) {
            return YamlList(listOf())
        }

        val items = splitListItems(block)

        val nodes = items.map { parseListItem(it) }

        return YamlList(nodes)
    }


    private fun parseListItem(block: List<String>): YamlNode {
        val withoutIndent: List<String> =
                block.map { it.substring(2) }

        return parse(withoutIndent)
    }


    private fun splitListItems(block: List<String>): List<List<String>> {
        return splitElements(block) {
            Patterns.item.matches(it)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun parseMap(block: List<String>): YamlMap {
        if (block.size == 1 && block[0] == Patterns.emptyMapJson) {
            return YamlMap(mapOf())
        }

        val entries = splitMapEntries(block)
//        println("parseMap - entries: $entries")

        val values = mutableMapOf<String, YamlNode>()
        for (entry in entries) {
            val parsedEntry = parseMapEntry(entry)
            values[parsedEntry.first] = parsedEntry.second
        }

        return YamlMap(values)
    }


    private fun parseMapEntry(block: List<String>): Pair<String, YamlNode> {
//        println("^&^&^ parseMapEntry: $block")

//        val startLife = block.find { Patterns.entry.matches(it) }
        val startLife = block.find { matchEntireEntry(it) != null }
                ?: throw IllegalArgumentException("Key-value pair not found: $block")

        val startMatch = matchEntireEntry(startLife)!!

        val key = startMatch.groupValues[1]
        val startSuffix = startMatch.groupValues[2]

        val valueBuffer = mutableListOf<String>()
        valueBuffer.add(startSuffix)

        var prefixLength = -1
        for (i in 1 until block.size) {
            if (prefixLength == -1) {
                prefixLength = prefixLength(block[i])
            }
            else {
                check(prefixLength(block[i]) == prefixLength) {
                    "Prefix mis-match ($prefixLength): ${block[i]}"
                }
            }

            valueBuffer.add(block[i].substring(prefixLength))
        }

        val value = parse(valueBuffer)
        return key to value
    }


    private fun splitMapEntries(block: List<String>): List<List<String>> {
        return splitElements(block) {
            matchEntireEntry(it) != null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun matchEntireEntry(line: String): MatchResult? {
        return Patterns.entryBare.matchEntire(line)
                ?: Patterns.entryDoubleQuoted.matchEntire(line)

//        return Patterns.entryBare.matchEntire(line)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun identifyStructure(block: List<String>): NotationStructure {
        for (line in block) {
            if (matchEntireEntry(line) != null || line == Patterns.emptyMapJson) {
                return NotationStructure.Map
            }
            if (Patterns.item.matchEntire(line) != null || line == Patterns.emptyListJson) {
                return NotationStructure.List
            }
        }

        return NotationStructure.Scalar
    }


    private fun prefixLength(line: String): Int {
        return if (line.startsWith("  ")) {
            2
        }
        else {
            0
        }
    }


    private fun splitElements(
            block: List<String>,
            startOfElement: (String) -> Boolean
    ): List<List<String>> {
        val buffer = mutableListOf<String>()

        val declarations = mutableListOf<List<String>>()

        for (line in block) {
//            println("splitting: $line")
            if (line.isEmpty() || Patterns.decorator.matchEntire(line) != null) {
//                println("skipped decorator")
                continue
            }

            val match = startOfElement.invoke(line)

            if (match && ! buffer.isEmpty()) {
                declarations.add(buffer.toList())
                buffer.clear()
            }

            buffer.add(line)
        }

        if (! buffer.isEmpty()) {
            declarations.add(buffer.toList())
        }

        return declarations
    }
}