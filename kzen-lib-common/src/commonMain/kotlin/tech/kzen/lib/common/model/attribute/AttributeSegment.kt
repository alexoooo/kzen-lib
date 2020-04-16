package tech.kzen.lib.common.model.attribute

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


@Suppress("DataClassPrivateConstructor")
data class AttributeSegment private constructor(
        private val asString: String
):
        Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun parse(asString: String): AttributeSegment {
            return ofKey(AttributePath.decodeDelimiter(asString))
        }

        fun ofKey(key: String): AttributeSegment {
            return AttributeSegment(key)
        }

        fun ofIndex(index: Int): AttributeSegment {
            check(index >= 0) { "Must not be negative: $index" }
            return AttributeSegment(index.toString())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asKey(): String {
        return asString
    }

    fun asIndex(): Int? {
        return asString.toIntOrNull()
    }


    fun asString(): String {
        return AttributePath.encodeDelimiter(asString)
    }


    override fun digest(builder: Digest.Builder) {
        builder.addUtf8(asString)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return asString
    }
}