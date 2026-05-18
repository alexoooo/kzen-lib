package tech.kzen.lib.common.model.structure.metadata.tag

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class ObjectTag(
    val value: String
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val tagPattern = Regex("[a-zA-Z][a-zA-Z0-9_-]*")

        fun matches(value: String): Boolean {
            return tagPattern.matches(value)
        }

        fun parse(asString: String): ObjectTag {
            return ObjectTag(asString)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        check(matches(value)) { "Tag not valid: '$value'" }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asString(): String {
        return value
    }


    override fun toString(): String {
        return value
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(value)
    }
}
