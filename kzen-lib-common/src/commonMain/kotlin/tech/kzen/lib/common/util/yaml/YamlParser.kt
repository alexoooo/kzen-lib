package tech.kzen.lib.common.util.yaml


/**
 * Similar to https://github.com/crdoconnor/strictyaml/ but with [] and {}
 *
 * Parsing is a single forward scan that builds a line index (indent + content offsets) over the
 * source `CharSequence`, then a recursive descent that walks the index by position without slicing.
 * Scalar lexing is hand-rolled char-by-char — no regex anywhere (parse or unparse).
 *
 * Parse is a lenient superset (legacy on-disk documents still load): bare scalars are rest-of-line
 * literal (Windows paths `C:\~\foo`, URLs, expressions), own-line `#` comments are modeled on the
 * following node, and `|` / `|-` block scalars are read. Unparse emits a strict subset that any
 * YAML 1.2 parser reads identically: bare, `'single'`, `"double"`, `|-`.
 */
object YamlParser {
    //-----------------------------------------------------------------------------------------------------------------
    const val fileExtension = "yaml"

    private const val emptyListMarker = "[]"
    private const val emptyMapMarker = "{}"

    private const val indentStep = 2

    // Value-mode forbidden leading characters (YAML plain-scalar indicators + quotes).
    private const val forbiddenBareFirst = ",[]{}#&*!|>'\"%@`"

    // Inline value characters that are YAML indicators we deliberately don't support — reject loudly
    // instead of silently mis-parsing.
    private const val unsupportedIndicators = "&*>%@`"


    //-----------------------------------------------------------------------------------------------------------------
    fun parse(document: String): YamlNode {
        val cursor = Cursor.of(document)
        return cursor.parseBlock(0)
    }


    // https://stackoverflow.com/questions/148857/what-is-the-opposite-of-parse
    // https://en.wiktionary.org/wiki/unparse
    fun unparse(yamlNode: YamlNode): String {
        val value = unparseValue(yamlNode)
        if (yamlNode.comments.isEmpty()) {
            return value
        }
        return renderComments(yamlNode.comments) + value
    }


    // Re-lex a map key back to its cleanest emittable form. Public so YamlNotationParser can render
    // object-path keys the same way.
    fun unparseKey(key: String): String {
        if (isBareKey(key)) {
            return key
        }
        if (isSingleQuotable(key)) {
            return "'$key'"
        }
        return "\"${escapeDouble(key)}\""
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
                        // Blank line — stays unindexed (its position is still recoverable as a raw slice
                        // for block scalars). Comment lines ARE indexed (unlike blanks) so the recursive
                        // descent can attach them and so a `#` inside a block-scalar body is literal.
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

        private fun isSynthetic(): Boolean =
            synIndent >= 0


        //-------------------------------------------------------------------------------------------------------------
        // Comment-line helpers (real indexed lines only; synthetic lines are never comments).
        private fun isCommentAt(idx: Int): Boolean =
            source[starts[idx]] == '#'

        private fun currentIsComment(): Boolean =
            ! isSynthetic() && lineIdx < lineCount && isCommentAt(lineIdx)

        private fun lineRawStart(idx: Int): Int =
            starts[idx] - indents[idx]

        // Non-consuming lookahead: index of the first non-comment line at/after lineIdx (or lineCount).
        private fun firstNonCommentIdx(): Int {
            var idx = lineIdx
            while (idx < lineCount && isCommentAt(idx)) {
                idx++
            }
            return idx
        }

        // Consume a run of comment lines, returning the stripped texts (no leading `#` + one space).
        private fun drainCommentRun(): List<String> {
            val result = mutableListOf<String>()
            while (lineIdx < lineCount && isCommentAt(lineIdx)) {
                result.add(stripComment(starts[lineIdx], ends[lineIdx]))
                lineIdx++
            }
            return result
        }

        private fun stripComment(s: Int, e: Int): String {
            var i = s + 1  // skip '#'
            if (i < e && source[i] == ' ') {
                i++
            }
            return source.substring(i, e)
        }


        //-------------------------------------------------------------------------------------------------------------
        fun parseBlock(baseline: Int): YamlNode {
            val curIndent = peekIndent()
            if (curIndent == Int.MAX_VALUE || curIndent < baseline) {
                return YamlString.empty
            }

            if (currentIsComment()) {
                val nextIdx = firstNonCommentIdx()
                if (nextIdx >= lineCount || indents[nextIdx] < baseline) {
                    // Only comments remain (then a dedent / EOF) — nothing at this level; leave the
                    // comment run for an outer scope to attach.
                    return YamlString.empty
                }
                if (indents[nextIdx] == baseline) {
                    if (isListMarkerAt(nextIdx)) {
                        return parseList(baseline)
                    }
                    if (isMapEntryAt(nextIdx)) {
                        return parseMap(baseline)
                    }
                }
                // Scalar preceded by comments — drain here and attach.
                val comments = drainCommentRun()
                val node = parseBlockCore(baseline)
                return node.withComments(comments + node.comments)
            }

            return parseBlockCore(baseline)
        }


        private fun parseBlockCore(baseline: Int): YamlNode {
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
            if (source[s] == '|') {
                // Standalone block scalar on its own line (real line). Body must be more indented than
                // this line; the indicator line is consumed here, the body by parseBlockScalar.
                val indicatorIndent = peekIndent()
                advance()
                return parseBlockScalar(s, e, e, indicatorIndent + 1)
            }

            val scalar = parseScalarContent(s, e)
            advance()
            return scalar
        }


        private fun parseList(baseline: Int): YamlList {
            val items = mutableListOf<YamlNode>()
            while (true) {
                var pendingComments: List<String> = listOf()
                if (currentIsComment()) {
                    val nextIdx = firstNonCommentIdx()
                    if (nextIdx < lineCount && indents[nextIdx] == baseline && isListMarkerAt(nextIdx)) {
                        pendingComments = drainCommentRun()
                    }
                    else {
                        break
                    }
                }
                if (peekIndent() != baseline || ! startsWithListMarker()) {
                    break
                }

                val inlineStart = peekStart() + 2  // skip "- "
                val inlineEnd = peekEnd()
                advance()

                val item: YamlNode =
                    if (inlineStart < inlineEnd) {
                        if (source[inlineStart] == '|') {
                            parseBlockScalar(inlineStart, inlineEnd, inlineEnd, baseline + 1)
                        }
                        else {
                            pushSynthetic(baseline + indentStep, inlineStart, inlineEnd)
                            parseBlock(baseline + indentStep)
                        }
                    }
                    else {
                        parseBlock(baseline + indentStep)
                    }

                items.add(
                    if (pendingComments.isEmpty()) item
                    else item.withComments(pendingComments + item.comments))
            }
            return YamlList(items)
        }


        private fun parseMap(baseline: Int): YamlMap {
            val entries = mutableMapOf<String, YamlNode>()
            while (true) {
                var pendingComments: List<String> = listOf()
                if (currentIsComment()) {
                    val nextIdx = firstNonCommentIdx()
                    if (nextIdx < lineCount && indents[nextIdx] == baseline && isMapEntryAt(nextIdx)) {
                        pendingComments = drainCommentRun()
                    }
                    else {
                        break
                    }
                }
                if (peekIndent() != baseline || ! matchMapEntryShape()) {
                    break
                }

                val keyStart = matchKeyStart
                val keyEnd = matchKeyEnd
                val valueStart = matchValueStart
                val lineEnd = peekEnd()
                val key = decodeKey(keyStart, keyEnd)
                advance()

                val value = parseEntryValue(valueStart, lineEnd, baseline)

                entries[key] =
                    if (pendingComments.isEmpty()) value
                    else value.withComments(pendingComments + value.comments)
            }
            return YamlMap(entries)
        }


        private fun parseEntryValue(valueStart: Int, lineEnd: Int, baseline: Int): YamlNode {
            return if (valueStart < lineEnd) {
                parseInlineValue(valueStart, lineEnd, baseline)
            }
            else if (peekIndent() == baseline && startsWithListMarker()) {
                // Inline-list form: the value's `- ` markers share the key's indent.
                parseBlock(baseline)
            }
            else {
                parseBlock(baseline + indentStep)
            }
        }


        // The value written after `key: ` on the same physical line. Unlike the legacy path this never
        // re-runs entry matching, so `test: a: b` is the scalar `a: b` and never nests.
        private fun parseInlineValue(valueStart: Int, lineEnd: Int, baseline: Int): YamlNode {
            val c = source[valueStart]
            return when {
                c == '"' || c == '\'' ->
                    parseQuotedScalar(valueStart, lineEnd, c)

                c == '|' ->
                    parseBlockScalar(valueStart, lineEnd, lineEnd, baseline + 1)

                c == '[' || c == '{' ->
                    parseInlineEmptyMarker(valueStart, lineEnd)

                c in unsupportedIndicators ->
                    throw IllegalArgumentException(
                        "Unsupported YAML indicator '$c': ${source.substring(valueStart, lineEnd)}")

                c == '-' && valueStart + 1 < lineEnd && source[valueStart + 1] == ' ' -> {
                    // Legacy inline-list form: `key: - x`
                    pushSynthetic(baseline + indentStep, valueStart, lineEnd)
                    parseBlock(baseline + indentStep)
                }

                c == '#' ->
                    // `key: # comment` — no inline value (comment unmodeled, consistent with end-of-line).
                    YamlString.empty

                else ->
                    parseBareRestOfLine(valueStart, lineEnd)
            }
        }


        // Bare rest-of-line: literal, no unescape. Strips a trailing ` #...` comment (space/tab before
        // the `#`) and trailing whitespace.
        private fun parseBareRestOfLine(valueStart: Int, lineEnd: Int): YamlString {
            var end = lineEnd
            var i = valueStart
            while (i < lineEnd) {
                if (source[i] == '#' && i > valueStart &&
                        (source[i - 1] == ' ' || source[i - 1] == '\t')) {
                    end = i
                    break
                }
                i++
            }
            while (end > valueStart && (source[end - 1] == ' ' || source[end - 1] == '\t')) {
                end--
            }
            return YamlString(source.substring(valueStart, end))
        }


        private fun parseInlineEmptyMarker(valueStart: Int, lineEnd: Int): YamlNode {
            var end = lineEnd
            var i = valueStart
            while (i < lineEnd) {
                if (source[i] == '#' && i > valueStart &&
                        (source[i - 1] == ' ' || source[i - 1] == '\t')) {
                    end = i
                    break
                }
                i++
            }
            while (end > valueStart && (source[end - 1] == ' ' || source[end - 1] == '\t')) {
                end--
            }
            return when (source.substring(valueStart, end)) {
                emptyListMarker -> YamlList(listOf())
                emptyMapMarker -> YamlMap(mapOf())
                else -> throw IllegalArgumentException(
                    "Unsupported inline collection (only [] and {} allowed): " +
                            source.substring(valueStart, lineEnd))
            }
        }


        //-------------------------------------------------------------------------------------------------------------
        // Block scalar (`|` clip / `|-` strip). The indicator content is [indStart, indEnd); the body is
        // the raw document slice after indLineEnd's line ending, so significant (unindexed) blank lines
        // are preserved. On entry lineIdx points at the first following indexed line; consumed body
        // lines are those with indent >= the first body line's indent.
        private fun parseBlockScalar(indStart: Int, indEnd: Int, indLineEnd: Int, bodyMinIndent: Int): YamlString {
            var p = indStart + 1  // skip '|'
            var strip = false
            if (p < indEnd && source[p] == '-') {
                strip = true
                p++
            }
            while (p < indEnd && (source[p] == ' ' || source[p] == '\t')) {
                p++
            }
            if (p < indEnd && source[p] != '#') {
                throw IllegalArgumentException(
                    "Unsupported block scalar indicator: ${source.substring(indStart, indEnd)}")
            }

            val firstBody = lineIdx
            if (firstBody >= lineCount || indents[firstBody] < bodyMinIndent) {
                // No body — empty string (clip of empty adds no newline).
                return YamlString("")
            }

            val blockIndent = indents[firstBody]
            var endIdx = firstBody
            while (endIdx < lineCount && indents[endIdx] >= blockIndent) {
                endIdx++
            }

            val bodyRawStart = skipLineEnd(indLineEnd)
            val bodyRawEnd = if (endIdx < lineCount) lineRawStart(endIdx) else source.length
            lineIdx = endIdx

            return YamlString(buildBlockBody(bodyRawStart, bodyRawEnd, blockIndent, strip))
        }


        private fun buildBlockBody(rawStart: Int, rawEnd: Int, blockIndent: Int, strip: Boolean): String {
            val lines = mutableListOf<String>()
            var i = rawStart
            while (i < rawEnd) {
                val lineStart = i
                while (i < rawEnd && source[i] != '\n' && source[i] != '\r') {
                    i++
                }
                val lineEnd = i
                if (i < rawEnd) {
                    if (source[i] == '\r' && i + 1 < rawEnd && source[i + 1] == '\n') {
                        i += 2
                    }
                    else {
                        i++
                    }
                }

                if (isBlankRange(lineStart, lineEnd)) {
                    lines.add("")
                }
                else {
                    var drop = lineStart
                    var dropped = 0
                    while (dropped < blockIndent && drop < lineEnd && source[drop] == ' ') {
                        drop++
                        dropped++
                    }
                    lines.add(source.substring(drop, lineEnd))
                }
            }

            var result = lines.joinToString("\n")
            var endTrim = result.length
            while (endTrim > 0 && result[endTrim - 1] == '\n') {
                endTrim--
            }
            result = result.substring(0, endTrim)
            if (! strip) {
                result += "\n"  // clip: keep a single trailing newline
            }
            return result
        }


        private fun isBlankRange(from: Int, to: Int): Boolean {
            var i = from
            while (i < to) {
                val c = source[i]
                if (c != ' ' && c != '\t') {
                    return false
                }
                i++
            }
            return true
        }


        // Position just after the line ending at pos (pos points at the newline char or EOF).
        private fun skipLineEnd(pos: Int): Int {
            if (pos >= source.length) {
                return source.length
            }
            return when (source[pos]) {
                '\r' -> if (pos + 1 < source.length && source[pos + 1] == '\n') pos + 2 else pos + 1
                '\n' -> pos + 1
                else -> pos
            }
        }


        //-------------------------------------------------------------------------------------------------------------
        private fun startsWithListMarker(): Boolean {
            val s = peekStart()
            val e = peekEnd()
            return e - s >= 2 && source[s] == '-' && source[s + 1] == ' '
        }


        private fun isListMarkerAt(idx: Int): Boolean {
            val s = starts[idx]
            val e = ends[idx]
            return e - s >= 2 && source[s] == '-' && source[s + 1] == ' '
        }


        // Sets matchKeyStart / matchKeyEnd / matchValueStart and returns true if the current peek is a
        // `key: ` map entry shape. The key may be bare, single-quoted, or double-quoted.
        private fun matchMapEntryShape(): Boolean =
            matchEntryShape(peekStart(), peekEnd())


        // Pure lookahead variant over a specific real line (also sets the match fields, harmlessly —
        // the caller re-runs matchMapEntryShape on the current peek before reading them).
        private fun isMapEntryAt(idx: Int): Boolean =
            matchEntryShape(starts[idx], ends[idx])


        private fun matchEntryShape(s: Int, e: Int): Boolean {
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
            if (! isBareStartChar(source[s])) {
                return false
            }
            var lastNonSpace = s
            var i = s + 1
            while (i < e) {
                val c = source[i]
                if (c == ':') {
                    break
                }
                if (! isBareMidChar(c)) {
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
            // Require space-or-EOL after the colon, else this is a plain scalar (`C:\foo`), not an entry.
            if (j + 1 != e && source[j + 1] != ' ' && source[j + 1] != '\t') {
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
                        // Legacy backslash escape inside a quoted key.
                        if (i + 1 >= e) {
                            return false
                        }
                        i += 2
                    }
                    quote -> {
                        if (quote == '\'' && i + 1 < e && source[i + 1] == '\'') {
                            // Standard single-quote doubling — an escaped quote, not the close.
                            i += 2
                        }
                        else {
                            var j = i + 1
                            while (j < e && source[j] == ' ') {
                                j++
                            }
                            if (j >= e || source[j] != ':') {
                                return false
                            }
                            if (j + 1 != e && source[j + 1] != ' ' && source[j + 1] != '\t') {
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
                    }
                    else -> i++
                }
            }
            return false
        }


        //-------------------------------------------------------------------------------------------------------------
        private fun parseScalarContent(s: Int, e: Int): YamlNode {
            if (s >= e) {
                return YamlString.empty
            }
            val c = source[s]
            return when {
                c == '"' || c == '\'' ->
                    parseQuotedScalar(s, e, c)

                c == '[' || c == '{' ->
                    parseInlineEmptyMarker(s, e)

                c == '|' || c in unsupportedIndicators ->
                    throw IllegalArgumentException(
                        "Unsupported YAML indicator '$c': ${source.substring(s, e)}")

                else ->
                    parseBareRestOfLine(s, e)
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
            val content =
                if (quote == '"') {
                    unescapeDouble(source, s + 1, endTrim - 1)
                }
                else {
                    unescapeSingle(source, s + 1, endTrim - 1)
                }
            return YamlString(content)
        }


        private fun decodeKey(s: Int, e: Int): String {
            if (s >= e) {
                return ""
            }
            return when (source[s]) {
                '"' -> unescapeDouble(source, s + 1, e - 1)
                '\'' -> unescapeSingle(source, s + 1, e - 1)
                else -> source.substring(s, e)  // bare: charset excludes '\', so no unescape needed
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
    private fun unescapeDouble(source: CharSequence, from: Int, to: Int): String {
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
                    'f' -> builder.append('')
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


    // Single-quoted: standard `''` -> `'` doubling PLUS the legacy backslash-escape table (older emitters
    // wrote e.g. '"C:\\~\\data"'). The new emitter never produces a backslash in single quotes, so this
    // parse-only leniency is self-extinguishing (design decision 5).
    private fun unescapeSingle(source: CharSequence, from: Int, to: Int): String {
        var i = from
        while (i < to) {
            val c = source[i]
            if (c == '\\' || c == '\'') {
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
            val ch = source[i]
            if (ch == '\'' && i + 1 < to && source[i + 1] == '\'') {
                builder.append('\'')
                i += 2
            }
            else if (ch == '\\' && i + 1 < to) {
                val esc = source[i + 1]
                i += 2
                when (esc) {
                    '\\', '/', '"', '\'' -> builder.append(esc)
                    'n' -> builder.append('\n')
                    'r' -> builder.append('\r')
                    't' -> builder.append('\t')
                    'b' -> builder.append('\b')
                    'f' -> builder.append('')
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
                i++
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


    // Double-quote-only escaper. `'` is emitted plain (single quotes need no escaping in a double-quoted
    // scalar); chars < 0x20 or >= 0x7F (incl. DEL and non-ASCII) become \uXXXX.
    private fun escapeDouble(unescaped: String): String {
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
                '"' -> "\\\""

                else ->
                    if (ch.code < 0x20 || ch.code >= 0x7F) {
                        val hex = ch.code.toString(16)
                        val prefixed = "000$hex"
                        val padded = prefixed.substring(prefixed.length - 4)
                        "\\u$padded"
                    }
                    else {
                        "$ch"
                    }
            }

            output.append(escaped)
        }

        return output.toString()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun unparseValue(yamlNode: YamlNode): String {
        return when (yamlNode) {
            is YamlString -> unparseString(yamlNode)
            is YamlList -> unparseList(yamlNode)
            is YamlMap -> unparseMap(yamlNode)
        }
    }


    private fun renderComments(comments: List<String>): String {
        val builder = StringBuilder()
        for (comment in comments) {
            builder.append(renderCommentLine(comment)).append('\n')
        }
        return builder.toString()
    }


    private fun renderCommentLine(text: String): String =
        // A banner ("####" parsed to "###") re-emits verbatim; other text gets the conventional "# ".
        if (text.startsWith("#")) "#$text" else "# $text"


    // Value string modes, cleanest first: bare, 'single', |- block, "double".
    private fun unparseString(yamlString: YamlString): String {
        val v = yamlString.value
        if (v.isEmpty()) {
            return "\"\""
        }
        if (isBareValue(v)) {
            return v
        }
        if (isSingleQuotable(v)) {
            return "'$v'"
        }
        if (isBlockRepresentable(v) && (v.contains('\n') || v.contains('\\') || v.contains('"'))) {
            return unparseBlockScalar(v)
        }
        return "\"${escapeDouble(v)}\""
    }


    private fun unparseBlockScalar(v: String): String {
        val body = v.split("\n").joinToString("\n") { if (it.isEmpty()) "" else "  $it" }
        return "|-\n$body"
    }


    private fun isBareValue(v: String): Boolean {
        if (v.isEmpty()) {
            return false
        }
        val first = v[0]
        if (first in forbiddenBareFirst || first == ' ' || first == '\t') {
            return false
        }
        if ((first == '-' || first == '?' || first == ':') && (v.length == 1 || v[1] == ' ')) {
            return false
        }
        if (v.last() == ' ' || v.last() == ':') {
            return false
        }
        for (c in v) {
            if (c.code < 0x20 || c.code > 0x7E) {
                return false
            }
        }
        if (v.contains(": ") || v.contains(" #")) {
            return false
        }
        return true
    }


    private fun isSingleQuotable(v: String): Boolean {
        if (v.isEmpty()) {
            return false
        }
        for (c in v) {
            if (c == '\'' || c == '\\' || c.code < 0x20 || c.code > 0x7E) {
                return false
            }
        }
        return true
    }


    private fun isBlockRepresentable(v: String): Boolean {
        if (v.isEmpty() || v[0] == '\n' || v[0] == ' ' || v.last() == '\n') {
            return false
        }
        for (c in v) {
            if (c != '\n' && (c.code < 0x20 || c.code > 0x7E)) {
                return false
            }
        }
        for (line in v.split("\n")) {
            if (line.isNotEmpty() && (line.last() == ' ' || line.last() == '\t')) {
                return false
            }
        }
        return true
    }


    // Keys keep the restricted bare shape (the old Patterns.bareString charset) so an emitted key re-lexes
    // to itself: [0-9a-zA-Z_-/.] with interior (non-edge) spaces allowed.
    private fun isBareKey(key: String): Boolean {
        if (key.isEmpty()) {
            return false
        }
        for (i in key.indices) {
            val c = key[i]
            val ok = isBareStartChar(c) || (c == ' ' && i != 0 && i != key.length - 1)
            if (! ok) {
                return false
            }
        }
        return true
    }


    private fun unparseList(yamlList: YamlList): String {
        if (yamlList.values.isEmpty()) {
            return emptyListMarker
        }

        return yamlList.values.joinToString("\n") { item ->
            val lines = unparseValue(item).split("\n")

            val buffer = StringBuilder()
            buffer.append("- ").append(lines[0])
            for (i in 1 until lines.size) {
                buffer.append('\n')
                if (lines[i].isNotEmpty()) {
                    buffer.append("  ").append(lines[i])
                }
            }

            if (item.comments.isEmpty()) {
                buffer.toString()
            }
            else {
                renderComments(item.comments) + buffer.toString()
            }
        }
    }


    private fun unparseMap(yamlMap: YamlMap): String {
        if (yamlMap.values.isEmpty()) {
            return emptyMapMarker
        }

        return yamlMap.values.map { entry ->
            val value = entry.value
            val lines = unparseValue(value).split("\n")
            val keyPrefix = unparseKey(entry.key)

            val entryText =
                if (value is YamlString || (value as YamlStructure).isEmpty()) {
                    "$keyPrefix: ${lines[0]}" +
                            lines.subList(1, lines.size).joinToString("") {
                                if (it.isEmpty()) "\n" else "\n  $it"
                            }
                }
                else {
                    "$keyPrefix:\n" + lines.joinToString("\n") { if (it.isEmpty()) "" else "  $it" }
                }

            if (value.comments.isEmpty()) {
                entryText
            }
            else {
                renderComments(value.comments) + entryText
            }
        }.joinToString("\n")
    }
}
