package tech.kzen.lib.common.util.naming


/**
 * Splitting and escaping for a path delimiter that a segment is allowed to contain literally, escaped with a
 * backslash. Object nesting (`/`) and attribute paths (`.`) are the same machinery over a different
 * character: a delimiter separates only when the preceding character is not a backslash, and a delimiter at
 * index 0 always separates (there is no preceding character to escape it).
 */
class EscapedDelimiter(
    private val delimiter: Char
) {
    private val unescaped = delimiter.toString()
    private val escaped = "\\$delimiter"


    fun indexOf(encoded: String): Int {
        for (i in 1 until encoded.length) {
            if (encoded[i] == delimiter &&
                    encoded[i - 1] != '\\') {
                return i
            }
        }
        if (encoded.isNotEmpty() && encoded[0] == delimiter) {
            return 0
        }
        return -1
    }


    fun lastIndexOf(encoded: String): Int {
        for (i in encoded.length - 1 downTo 1) {
            if (encoded[i] == delimiter &&
                    encoded[i - 1] != '\\') {
                return i
            }
        }
        if (encoded.isNotEmpty() && encoded[0] == delimiter) {
            return 0
        }
        return -1
    }


    fun contains(encoded: String): Boolean {
        return indexOf(encoded) != -1
    }


    fun split(encoded: String): List<String> {
        val segments = mutableListOf<String>()

        var remaining = encoded

        while (true) {
            val nextIndex = indexOf(remaining)
            if (nextIndex == -1) {
                segments.add(remaining)
                break
            }

            segments.add(remaining.substring(0, nextIndex))

            remaining = remaining.substring(nextIndex + 1)
        }

        return segments
    }


    fun encode(value: String): String {
        return value.replace(unescaped, escaped)
    }


    fun decode(value: String): String {
        return value.replace(escaped, unescaped)
    }
}
