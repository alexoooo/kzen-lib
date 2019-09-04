package tech.kzen.lib.common.model.document

import tech.kzen.lib.common.structure.notation.NotationConventions
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class DocumentName(
        val value: String
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofYaml(filename: String): DocumentName {
            return DocumentName("$filename${NotationConventions.documentPathSuffix}")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        check(value.isNotEmpty()) {
            "Document name can't be empty"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun withoutExtension(): String {
        return value.substringBeforeLast(".")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(digester: Digest.Streaming) {
        digester.addUtf8(value)
    }


    override fun toString(): String {
        return value
    }
}