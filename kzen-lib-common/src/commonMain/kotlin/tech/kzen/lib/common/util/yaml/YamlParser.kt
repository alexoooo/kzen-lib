package tech.kzen.lib.common.util.yaml


/**
 * Similar to https://github.com/crdoconnor/strictyaml/ but with [] and {}
 *
 * Parsing is a single forward scan that builds a line index (indent + content offsets) over the
 * source `CharSequence`, then a recursive descent that walks the index by position without slicing.
 * Scalar lexing is hand-rolled char-by-char — no regex on the parse path.
 */
object YamlParser {
    //-----------------------------------------------------------------------------------------------------------------
    const val fileExtension = "yaml"

    private const val emptyListMarker = "[]"
    private const val emptyMapMarker = "{}"

    private const val indentStep = 2


    private object Patterns {
        // Retained for unparseString only — the parse path is regex-free.
        // https://stackoverflow.com/questions/32155133/regex-to-match-a-json-string
        // https://stackoverflow.com/questions/4264877/why-is-the-slash-an-escapable-character-in-json
        val bareString = Regex(
            "([0-9a-zA-Z_\\-/.][0-9a-zA-Z_\\-/. ]*[0-9a-zA-Z_\\-/.]|[0-9a-zA-Z_\\-/.]+)")
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun parse(document: String): YamlNode {
        val cursor = Cursor.of(document)
        return cursor.parseBlock(0)
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
    private class Cursor private constructor(
        private val source: String,
        private val indents: IntArray,
        private val starts: IntArray,
        private val ends: IntArray,
        private val lineCount: Int
    ) {
        companion object {
            fun of(document: String): Cursor {
                val initialCapacity = estimateCapacity(document.length)
                var indents = IntArray(initialCapacity)
                var starts = IntArray(initialCapacity)
                var ends = IntArray(initialCapacity)
                var count = 0

                val n = document.length
                var i = 0
                while (i < n) {
                    val lineStart = i
                    while (i < n && document[i] == ' ') {
                        i++
                    }
                    val contentStart = i
                    while (i < n && document[i] != '\n' && document[i] != '\r') {
                        i++
                    }
                    val contentEnd = i
                    if (i < n) {
                        if (document[i] == '\r' && i + 1 < n && document[i + 1] == '\n') {
                            i += 2
                        }
                        else {
                            i++
                        }
                    }
                    if (contentStart == contentEnd) {
                        continue
                    }
                    if (document[contentStart] == '#') {
                        continue
                    }
                    if (count == indents.size) {
                        val grown = indents.size * 2
                        indents = indents.copyOf(grown)
                        starts = starts.copyOf(grown)
                        ends = ends.copyOf(grown)
                    }
                    indents[count] = contentStart - lineStart
                    starts[count] = contentStart
                    ends[count] = contentEnd
                    count++
                }

                return Cursor(document, indents, starts, ends, count)
            }


            private fun estimateCapacity(documentLength: Int): Int {
                return when {
                    documentLength < 64 -> 4
                    documentLength < 1024 -> 32
                    else -> minOf(1024, documentLength / 24)
                }
            }
        }


        //-------------------------------------------------------------------------------------------------------------
        private var lineIdx = 0

        // At most one synthetic "virtual line" can be pending — used by parseList/parseMap to
        // inject inline content (after `- ` or `key:`) without allocating substrings.
        private var synIndent = -1
        private var synStart = 0
        private var synEnd = 0

        // Output of matchMapEntryShape — avoids per-call IntArray allocation in the hot loop.
        private var matchKeyStart = 0
        private var matchKeyEnd = 0
        private var matchValueStart = 0


        private fun peekIndent(): Int =
            when {
                synIndent >= 0 -> synIndent
                lineIdx < lineCount -> indents[lineIdx]
                else -> Int.MAX_VALUE
            }

        private fun peekStart(): Int =
            if (synIndent >= 0) synStart else starts[lineIdx]

        private fun peekEnd(): Int =
            if (synIndent >= 0) synEnd else ends[lineIdx]

        private fun advance() {
            if (synIndent >= 0) {
                synIndent = -1
            }
            else {
                lineIdx++
            }
        }

        private fun pushSynthetic(indent: Int, start: Int, end: Int) {
            check(synIndent < 0) { "Synthetic already pushed" }
            synIndent = indent
            synStart = start
            synEnd = end
        }


        //-------------------------------------------------------------------------------------------------------------
        fun parseBlock(baseline: Int): YamlNode {
            val curIndent = peekIndent()
            if (curIndent == Int.MAX_VALUE || curIndent < baseline) {
                return YamlString.empty
            }

            val s = peekStart()
            val e = peekEnd()
            val length = e - s

            if (length == 2 && source[s] == '[' && source[s + 1] == ']') {
                advance()
                return YamlList(listOf())
            }
            if (length == 2 && source[s] == '{' && source[s + 1] == '}') {
                advance()
                return YamlMap(mapOf())
            }
            if (length >= 2 && source[s] == '-' && source[s + 1] == ' ') {
                return parseList(baseline)
            }
            if (matchMapEntryShape()) {
                return parseMap(baseline)
            }

            val scalar = parseScalarContent(s, e)
            advance()
            return scalar
        }


        private fun parseList(baseline: Int): YamlList {
            val items = mutableListOf<YamlNode>()
            while (peekIndent() == baseline && startsWithListMarker()) {
                val inlineStart = peekStart() + 2  // skip "- "
                val inlineEnd = peekEnd()
                advance()

                if (inlineStart < inlineEnd) {
                    pushSynthetic(baseline + indentStep, inlineStart, inlineEnd)
                }
                items.add(parseBlock(baseline + indentStep))
            }
            return YamlList(items)
        }


        private fun parseMap(baseline: Int): YamlMap {
            val entries = mutableMapOf<String, YamlNode>()
            while (peekIndent() == baseline && matchMapEntryShape()) {
                val keyStart = matchKeyStart
                val keyEnd = matchKeyEnd
                val valueStart = matchValueStart
                val lineEnd = peekEnd()
                val key = decodeKey(keyStart, keyEnd)
                advance()

                val value: YamlNode =
                    if (valueStart < lineEnd) {
                        pushSynthetic(baseline + indentStep, valueStart, lineEnd)
                        parseBlock(baseline + indentStep)
                    }
                    else if (peekIndent() == baseline && startsWithListMarker()) {
                        // Inline-list form: the value's `- ` markers share the key's indent.
                        parseBlock(baseline)
                    }
                    else {
                        parseBlock(baseline + indentStep)
                    }

                entries[key] = value
            }
            return YamlMap(entries)
        }


        //-------------------------------------------------------------------------------------------------------------
        private fun startsWithListMarker(): Boolean {
            val s = peekStart()
            val e = peekEnd()
            return e - s >= 2 && source[s] == '-' && source[s + 1] == ' '
        }


        // Sets matchKeyStart / matchKeyEnd / matchValueStart and returns true if the current peek
        // is a `key:` map entry shape. The key may be bare, single-quoted, or double-quoted.
        private fun matchMapEntryShape(): Boolean {
            val s = peekStart()
            val e = peekEnd()
            if (s >= e) {
                return false
            }
            return when (source[s]) {
                '"' -> matchQuotedEntry(s, e, '"')
                '\'' -> matchQuotedEntry(s, e, '\'')
                else -> matchBareEntry(s, e)
            }
        }


        private fun matchBareEntry(s: Int, e: Int): Boolean {
            if (!isBareStartChar(source[s])) {
                return false
            }
            var lastNonSpace = s
            var i = s + 1
            while (i < e) {
                val c = source[i]
                if (c == ':') {
                    break
                }
                if (!isBareMidChar(c)) {
                    return false
                }
                if (c != ' ') {
                    lastNonSpace = i
                }
                i++
            }
            var j = lastNonSpace + 1
            while (j < e && source[j] == ' ') {
                j++
            }
            if (j >= e || source[j] != ':') {
                return false
            }
            var v = j + 1
            while (v < e && (source[v] == ' ' || source[v] == '\t')) {
                v++
            }
            matchKeyStart = s
            matchKeyEnd = lastNonSpace + 1
            matchValueStart = v
            return true
        }


        private fun matchQuotedEntry(s: Int, e: Int, quote: Char): Boolean {
            var i = s + 1
            while (i < e) {
                val c = source[i]
                when (c) {
                    '\\' -> {
                        if (i + 1 >= e) {
                            return false
                        }
                        i += 2
                    }
                    quote -> {
                        var j = i + 1
                        while (j < e && source[j] == ' ') {
                            j++
                        }
                        if (j >= e || source[j] != ':') {
                            return false
                        }
                        var v = j + 1
                        while (v < e && (source[v] == ' ' || source[v] == '\t')) {
                            v++
                        }
                        matchKeyStart = s
                        matchKeyEnd = i + 1
                        matchValueStart = v
                        return true
                    }
                    else -> i++
                }
            }
            return false
        }


        //-------------------------------------------------------------------------------------------------------------
        private fun parseScalarContent(s: Int, e: Int): YamlString {
            if (s >= e) {
                return YamlString.empty
            }
            return when (source[s]) {
                '"' -> parseQuotedScalar(s, e, '"')
                '\'' -> parseQuotedScalar(s, e, '\'')
                else -> YamlString(unescape(source, s, e))
            }
        }


        private fun parseQuotedScalar(s: Int, e: Int, quote: Char): YamlString {
            var lastQuote = -1
            var j = e - 1
            while (j > s) {
                if (source[j] == quote) {
                    lastQuote = j
                    break
                }
                j--
            }
            require(lastQuote > s) {
                "Missing closing ${if (quote == '"') "double" else "single"} quote: ${source.substring(s, e)}"
            }

            val lastHash = lastIndexOf(source, '#', s, e)
            val truncated: Int =
                if (lastHash > lastQuote) {
                    lastQuote + 1
                }
                else {
                    e
                }

            var endTrim = truncated
            while (endTrim > s && source[endTrim - 1] == ' ') {
                endTrim--
            }
            require(endTrim > s && source[endTrim - 1] == quote) {
                "Can't parse String: ${source.substring(s, e)}"
            }
            return YamlString(unescape(source, s + 1, endTrim - 1))
        }


        private fun decodeKey(s: Int, e: Int): String {
            if (s >= e) {
                return ""
            }
            return when (source[s]) {
                '"', '\'' -> unescape(source, s + 1, e - 1)
                else -> unescape(source, s, e)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun isBareStartChar(c: Char): Boolean =
        c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z' ||
            c == '_' || c == '-' || c == '/' || c == '.'


    private fun isBareMidChar(c: Char): Boolean =
        isBareStartChar(c) || c == ' '


    private fun lastIndexOf(s: CharSequence, ch: Char, from: Int, to: Int): Int {
        var i = to - 1
        while (i >= from) {
            if (s[i] == ch) {
                return i
            }
            i--
        }
        return -1
    }


    // https://gist.github.com/jjfiv/2ac5c081e088779f49aa
    private fun unescape(source: CharSequence, from: Int, to: Int): String {
        var i = from
        while (i < to) {
            if (source[i] == '\\') {
                break
            }
            i++
        }
        if (i == to) {
            return source.substring(from, to)
        }

        val builder = StringBuilder(to - from)
        builder.append(source, from, i)

        while (i < to) {
            val ch = source[i++]
            if (ch == '\\' && i < to) {
                val esc = source[i++]
                when (esc) {
                    '\\', '/', '"', '\'' -> builder.append(esc)
                    'n' -> builder.append('\n')
                    'r' -> builder.append('\r')
                    't' -> builder.append('\t')
                    'b' -> builder.append('\b')
                    'f' -> builder.append("\\u000C")
                    'u' -> {
                        require(i + 4 <= to) { "Not enough unicode digits! " }
                        builder.append(parseHex4(source, i).toChar())
                        i += 4
                    }
                    else -> throw IllegalArgumentException(
                        "Illegal escape at $i: ${source.subSequence(from, to)}")
                }
            }
            else {
                builder.append(ch)
            }
        }
        return builder.toString()
    }


    private fun parseHex4(s: CharSequence, from: Int): Int {
        var value = 0
        for (k in 0 until 4) {
            val c = s[from + k]
            val digit = when (c) {
                in '0'..'9' -> c.code - '0'.code
                in 'a'..'f' -> 10 + (c.code - 'a'.code)
                in 'A'..'F' -> 10 + (c.code - 'A'.code)
                else -> throw IllegalArgumentException("Bad character in unicode escape")
            }
            value = (value shl 4) or digit
        }
        return value
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

                '' -> "\\f"

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

                in 128.toChar() .. '￿' -> {
                    val hex = ch.code.toString(16)
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
            return emptyListMarker
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
            return emptyMapMarker
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
