package tech.kzen.lib.common.model.document

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class DocumentSegment(
        val value: String
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val segmentPattern = Regex("[a-zA-Z0-9_\\- ]+")
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        check(value.isNotEmpty()) {
            "Document segment can't be empty"
        }

        check(segmentPattern.matches(value)) {
            "Document segment uses invalid character: $value"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(digester: Digest.Streaming) {
        digester.addUtf8(value)
    }


    override fun toString(): String {
        return value
    }
}