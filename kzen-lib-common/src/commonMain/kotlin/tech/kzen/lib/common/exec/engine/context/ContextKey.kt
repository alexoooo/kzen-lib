package tech.kzen.lib.common.exec.engine.context


/**
 * Where an ambient binding lives in a frame's registry: a [family] plus an optional [qualifier] naming one
 * member of it (`sut` versus `sut:main`).
 *
 * [asString] renders the plain-string form the raw / plugin API registers under, so a typed reader and a raw
 * one address the same registration — the interop logic-spec §6 makes load-bearing. [parse] is its exact
 * inverse and rejects anything [asString] could not have produced, so a key that round-trips is the only kind
 * that exists.
 */
data class ContextKey(
    val family: ContextFamily,
    val qualifier: String? = null
) {
    init {
        if (qualifier != null) {
            require(qualifier.isNotEmpty()) {
                "Context qualifier must not be empty — omit it to name the family itself: $family"
            }
            require(qualifierDelimiter !in qualifier) {
                "Context qualifier must not contain '$qualifierDelimiter': $qualifier"
            }
        }
    }


    fun asString(): String {
        return when (qualifier) {
            null -> family.value
            else -> "${family.value}$qualifierDelimiter$qualifier"
        }
    }


    override fun toString(): String {
        return asString()
    }


    companion object {
        const val qualifierDelimiter = ':'


        fun of(family: String, qualifier: String? = null): ContextKey {
            return ContextKey(ContextFamily(family), qualifier)
        }


        fun parse(value: String): ContextKey {
            val delimiterIndex = value.indexOf(qualifierDelimiter)
            if (delimiterIndex == -1) {
                return ContextKey(ContextFamily(value))
            }
            return ContextKey(
                ContextFamily(value.substring(0, delimiterIndex)),
                value.substring(delimiterIndex + 1))
        }


        /**
         * [parse], or null for a string no key could be spelled as. What the plain-string READ surfaces use:
         * every key in a registry got there through [parse], so a string that cannot be one addresses nothing,
         * and answering "nothing is bound there" is more truthful than throwing at a reader.
         */
        fun parseOrNull(value: String): ContextKey? {
            return try {
                parse(value)
            }
            catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
