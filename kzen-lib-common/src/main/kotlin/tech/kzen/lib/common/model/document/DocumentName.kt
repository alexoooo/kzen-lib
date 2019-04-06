package tech.kzen.lib.common.model.document

import tech.kzen.lib.common.structure.notation.NotationConventions


data class DocumentName(
        val value: String
) {
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
    override fun toString(): String {
        return value
    }
}