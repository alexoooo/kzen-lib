package tech.kzen.lib.common.notation.read.yaml


object YamlNodeParser {
    //-----------------------------------------------------------------------------------------------------------------
    private object Patterns {
        val lineBreak = Regex(
                "\r\n|\n")

//        val entryName = Regex(
//                "(\\w+):\\s*")

        val decorator = Regex(
                "$|#(.*)")

        val entry = Regex(
                "([0-9a-zA-Z_-]+)\\s*:\\s*(.*)")

        val item = Regex(
                "- .*")

    }


    private enum class NotationStructure {
        Scalar,
        List,
        Map
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun parse(body: ByteArray): YamlNode {
        // https://stackoverflow.com/a/49468129/1941359
        val document = body.joinToString("") {"${it.toChar()}"}

        // import kotlinx.serialization.stringFromUtf8Bytes
        // val document = stringFromUtf8Bytes(body)

        return parse(document)
    }


    fun parse(document: String): YamlNode {
        val lines = document.split(YamlNodeParser.Patterns.lineBreak)
        return parse(lines)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun parse(block: List<String>): YamlNode {
        val structure = identifyStructure(block)

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
    private fun parseScalar(block: List<String>) : YamlScalar {
        check(block.size == 1)
        val value = block[0]

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

        return parseString(value)
    }


    private fun parseString(value: String): YamlString {
        val decoded =
                if (value.isEmpty()) {
                    ""
                }
                else if (! value.contains('"') &&
                        ! value.contains('\'')) {
                    value
                }
                else if (value.startsWith('"')) {
                    if (! value.endsWith('"')) {
                        throw IllegalArgumentException("Can't parse String: $value")
                    }

                    value.substring(1, value.length - 1)
                }
                else if (value.startsWith('\'')) {
                    if (! value.endsWith('\'')) {
                        throw IllegalArgumentException("Can't parse String: $value")
                    }

                    value.substring(1, value.length - 1)
                }
                else {
                    throw IllegalArgumentException("Can't parse String: $value")
                }

        return YamlString(decoded)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun parseList(block: List<String>): YamlList {
        TODO()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun parseMap(block: List<String>): YamlMap {
        val entries = splitMapEntries(block)

        val values = mutableMapOf<String, YamlNode>()
        for (entry in entries) {
            val parsedEntry = parseMapEntry(entry)
            values[parsedEntry.first] = parsedEntry.second
        }

        return YamlMap(values)
    }


    private fun parseMapEntry(block: List<String>): Pair<String, YamlNode> {
//        check(block.size == 1)

        val match = Patterns.entry.matchEntire(block[0])!!

        val key = match.groupValues[1]

        val valueBlock =
                if (block.size == 1) {
                    val value = match.groupValues[2]
                    listOf(value)
                }
                else {
                    val buffer = mutableListOf<String>()
                    buffer.add(match.groupValues[2])

                    for (i in 1 until block.size) {
                        check(block[i].startsWith("  "))
                        buffer.add(block[i].substring(2))
                    }

                    buffer
                }

        val value = parse(valueBlock)
        return key to value
    }


    private fun splitMapEntries(block: List<String>): List<List<String>> {
        val buffer = mutableListOf<String>()

        val declarations = mutableListOf<List<String>>()

        for (line in block) {
//            println("splitting: $line")
            if (Patterns.decorator.matchEntire(line) != null) {
//                println("skipped decorator")
                continue
            }

            val match = Patterns.entry.matchEntire(line)

            if (match != null && ! buffer.isEmpty()) {
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


    //-----------------------------------------------------------------------------------------------------------------
    private fun identifyStructure(block: List<String>): NotationStructure {
        for (line in block) {
            if (YamlNodeParser.Patterns.entry.matchEntire(line) != null) {
                return NotationStructure.Map
            }
            if (YamlNodeParser.Patterns.item.matchEntire(line) != null) {
                return NotationStructure.List
            }
        }

        return NotationStructure.Scalar
    }
}