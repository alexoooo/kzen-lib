package tech.kzen.lib.common.util.yaml


/**
 * Similar to https://github.com/crdoconnor/strictyaml/ but with [] and {}
 */
object YamlParser {
    //-----------------------------------------------------------------------------------------------------------------
    const val fileExtension = "yaml"

    private const val emptyListJson = "[]"
    private const val emptyMapJson = "{}"


    private object Patterns {
        val lineBreak = Regex(
                "\r\n|\n")

        val decorator = Regex(
                "#(.*)")

        // https://stackoverflow.com/questions/32155133/regex-to-match-a-json-string
        // https://stackoverflow.com/questions/4264877/why-is-the-slash-an-escapable-character-in-json
        private const val bareStringPattern =
            "([0-9a-zA-Z_\\-/.][0-9a-zA-Z_\\-/. ]*[0-9a-zA-Z_\\-/.]|[0-9a-zA-Z_\\-/.]+)"
        private const val doubleQuotedString =
                "\"((?:[^\"]|\\(?:[\"/bfnrt]|u[0-9a-fA-F]{4})*)\""

        private const val entrySuffix = "\\s*:\\s*(.*)"

        val entryBare = Regex(
                "$bareStringPattern$entrySuffix")

        val entryDoubleQuoted = Regex(
                "$doubleQuotedString$entrySuffix")

        val item = Regex(
                "- .*")

        val bareString = Regex(
                bareStringPattern)
        val bareStringX = Regex(
            doubleQuotedString)
    }


    private enum class NotationStructure {
        Scalar,
        List,
        Map
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun parse(document: String): YamlNode {
        val lines = document.split(Patterns.lineBreak)
        return parse(lines)
    }


    // https://stackoverflow.com/questions/148857/what-is-the-opposite-of-parse
    // https://en.wiktionary.org/wiki/unparse
    fun unparse(yamlNode: YamlNode): String {
        return when (yamlNode) {
            is YamlString ->
                unparseString(yamlNode)

            is YamlList ->
                unparseList(yamlNode)

            is YamlMap ->
                unparseMap(yamlNode)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun parse(block: List<String>): YamlNode {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val structure = identifyStructure(block)

        return when (structure) {
            NotationStructure.Scalar ->
                parseStringBlock(block)

            NotationStructure.List ->
                parseList(block)

            NotationStructure.Map ->
                parseMap(block)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun parseStringBlock(block: List<String>): YamlString {
        if (block.isEmpty()) {
            return YamlString.empty
        }

        val value: String =
                if (block.size == 1) {
                    block[0]
                }
                else {
                    val nonCommentLines = block.filter {
                        it.isNotEmpty() &&
                                Patterns.decorator.matchEntire(it) == null
                    }

                    if (nonCommentLines.isEmpty()) {
                        return YamlString.empty
                    }

                    check(nonCommentLines.size == 1) { "Scalar expected: $nonCommentLines" }

                    nonCommentLines[0]
                }

        return parseString(value)
    }


    private fun parseString(value: String): YamlString {
        val escaped =
                if (value.isEmpty()) {
                    ""
                }
                else if (! value.contains('"') &&
                        ! value.contains('\'')) {
                    value
                }
                else if (value.startsWith('"')) {
                    val lastQuoteIndex = value.lastIndexOf('"')
                    require(lastQuoteIndex != 0) { "Missing closing double quote: $value" }

                    val withoutComment = removeTrailingQuotedStringComment(value, lastQuoteIndex)
                    require(withoutComment.endsWith('"')) { "Can't parse String: $value" }

                    withoutComment.substring(1, withoutComment.length - 1)
                }
                else if (value.startsWith('\'')) {
                    val lastQuoteIndex = value.lastIndexOf('\'')
                    require(lastQuoteIndex != 0) { "Missing closing single quote: $value" }

                    val withoutComment = removeTrailingQuotedStringComment(value, lastQuoteIndex)
                    require(withoutComment.endsWith('\'')) { "Can't parse String: $value" }

                    withoutComment.substring(1, withoutComment.length - 1)
                }
                else {
                    throw IllegalArgumentException("Can't parse String: $value")
                }

        val raw = unescapeString(escaped)

        return YamlString(raw)
    }


    private fun removeTrailingQuotedStringComment(value: String, lastQuoteIndex: Int): String {
        val lastCommentIndex = value.lastIndexOf('#')
        if (lastCommentIndex < lastQuoteIndex) {
            return value.trim()
        }
        return value.substring(0 .. lastQuoteIndex).trim()
    }


    private fun unescapeString(escaped: String): String {
        if (! escaped.contains('\\')) {
            return escaped
        }

        // https://gist.github.com/jjfiv/2ac5c081e088779f49aa
        val builder = StringBuilder()

        var i = 0
        while (i < escaped.length) {
            // consume letter or backslash
            val delimiter = escaped[i++]

            if (delimiter == '\\' && i < escaped.length) {
                // consume first after backslash
                @Suppress("MoveVariableDeclarationIntoWhen")
                val ch = escaped[i++]

                val decoded: Any =
                        when (ch) {
                            '\\', '/', '"', '\'' ->
                                ch

                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            'b' -> '\b'
                            'f' -> "\\u000C"

                            'u' -> {
                                // expect 4 digits
                                require(i + 4 <= escaped.length) { "Not enough unicode digits! " }

                                val digits: String = escaped.substring(i, i + 4)
                                val parsed: Int = digits.toIntOrNull(16)
                                        ?: throw IllegalArgumentException("Bad character in unicode escape")

                                val asChar = parsed.toChar()

//                            StringBuilder().append(asChar).toString()
                                asChar.toString()
                            }

                            else ->
                                throw IllegalArgumentException("Illegal escape at $i: $escaped")
                        }

                builder.append(decoded)
            }
            else {
                // it's not a backslash, or it's the last character
                builder.append(delimiter)
            }
        }
        return builder.toString()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun parseList(block: List<String>): YamlList {
        if (block.size == 1 && block[0] == emptyListJson) {
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
        if (block.size == 1 && block[0] == emptyMapJson) {
            return YamlMap(mapOf())
        }

        val entries = splitMapEntries(block)

        val values = mutableMapOf<String, YamlNode>()
        for (entry in entries) {
            val parsedEntry = parseMapEntry(entry)
            values[parsedEntry.first] = parsedEntry.second
        }

        return YamlMap(values)
    }


    private fun parseMapEntry(block: List<String>): Pair<String, YamlNode> {
        val startLine = block.find { matchEntireEntry(it) != null }
                ?: throw IllegalArgumentException("Key-value pair not found: $block")

        val startMatch = matchEntireEntry(startLine)!!

        val key = unescapeString(startMatch.groupValues[1])
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
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun identifyStructure(block: List<String>): NotationStructure {
        for (line in block) {
            if (Patterns.item.matchEntire(line) != null || line == emptyListJson) {
                return NotationStructure.List
            }
            if (matchEntireEntry(line) != null || line == emptyMapJson) {
                return NotationStructure.Map
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
            if (line.isEmpty() || Patterns.decorator.matchEntire(line) != null) {
                continue
            }

            val match = startOfElement.invoke(line)

            if (match && buffer.isNotEmpty()) {
                declarations.add(buffer.toList())
                buffer.clear()
            }

            buffer.add(line)
        }

        if (buffer.isNotEmpty()) {
            declarations.add(buffer.toList())
        }

        return declarations
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun unparseString(yamlString: YamlString): String {
        if (yamlString.value.isEmpty()) {
            return "\"\""
        }

        if (Patterns.bareString.matchEntire(yamlString.value) != null) {
            return yamlString.value
        }

        val singleQuoteCount = yamlString.value.count { it == '\'' }
        val doubleQuoteCount = yamlString.value.count { it == '"' }

        val quotation: Char =
                if (singleQuoteCount >= doubleQuoteCount) {
                    '"'
                }
                else {
                    '\''
                }

        val escaped = escape(yamlString.value, quotation)

        return "$quotation$escaped$quotation"
    }


    private fun escape(unescaped: String, quotation: Char): String {
        val output = StringBuilder()

        for (ch in unescaped) {
            val escaped: String = when (ch) {
                0.toChar() ->
                    throw IllegalArgumentException("Zero char not allowed")

                '\r' -> "\\r"
                '\n' -> "\\n"
                '\t' -> "\\t"
                '\\' -> "\\\\"
                '\b' -> "\\b"

                '\u000C' -> "\\f"

                '"' ->
                    if (quotation == '"') {
                        "\\\""
                    }
                    else {
                        "\""
                    }

                '\'' ->
                    if (quotation == '\'') {
                        "\\'"
                    }
                    else {
                        "'"
                    }

                in 128.toChar() .. '\uffff' -> {
                    val hex = ch.toInt().toString(16)
                    val prefixed = "000$hex"
                    val padded = prefixed.substring(prefixed.length - 4)
                    "\\u$padded"
                }

                else ->
                    "$ch"
            }

            output.append(escaped)
        }

        return output.toString()
    }


    private fun unparseList(yamlList: YamlList): String {
        if (yamlList.values.isEmpty()) {
            return emptyListJson
        }

        return yamlList.values.joinToString("\n") {
            val lines = unparse(it).split("\n")

            val buffer = mutableListOf<String>()

            buffer.add("- ${lines[0]}")

            for (i in 1 until lines.size) {
                buffer.add("  ${lines[i]}")
            }

            buffer.joinToString("\n")
        }
    }


    private fun unparseMap(yamlMap: YamlMap): String {
        if (yamlMap.values.isEmpty()) {
            return emptyMapJson
        }

        return yamlMap.values.map { entry ->
            val lines = unparse(entry.value).split("\n")

            val keyPrefix = unparseString(YamlString(entry.key))

            if (entry.value is YamlString ||
                    (entry.value as YamlStructure).isEmpty()) {
                "$keyPrefix: ${lines[0]}" +
                        lines.subList(1, lines.size).joinToString("") { "\n   $it" }
            }
            else {
                "$keyPrefix:\n" + lines.joinToString("\n") { "  $it" }
            }
        }.joinToString("\n")
    }
}