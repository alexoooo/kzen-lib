package tech.kzen.lib.common.structure.notation.format


object YamlUtils {
    //-----------------------------------------------------------------------------------------------------------------
    const val emptyListJson = "[]"
    const val emptyMapJson = "{}"


    val bareString = Regex("([0-9a-zA-Z_-]+)")


    //-----------------------------------------------------------------------------------------------------------------
    fun deparseString(parsed: String): String {
        if (parsed.isEmpty()) {
            return "\"\""
        }

        if (bareString.matchEntire(parsed) != null) {
            return parsed
        }

        val singleQuoteCount = parsed.count { it == '\'' }
        val doubleQuoteCount = parsed.count { it == '"' }

        val quotation: Char =
                if (singleQuoteCount >= doubleQuoteCount) {
                    '"'
                }
                else {
                    '\''
                }

        val escaped = escape(parsed, quotation)

        return "$quotation$escaped$quotation"
    }


    fun escape(unescaped: String, quotation: Char): String {
        val output = StringBuilder()

        for (i in 0 until unescaped.length) {
            val ch = unescaped[i]

            val escaped: String =
                when (ch) {
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


    //-----------------------------------------------------------------------------------------------------------------
    fun parseString(value: String): YamlString {
        val escaped =
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

        val raw = unescape(escaped)

        return YamlString(raw)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun unescape(escaped: String): String {
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
                                if (i + 4 > escaped.length) {
                                    throw IllegalArgumentException("Not enough unicode digits! ")
                                }

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
}