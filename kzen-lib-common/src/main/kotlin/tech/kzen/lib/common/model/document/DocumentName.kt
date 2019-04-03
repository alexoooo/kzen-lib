package tech.kzen.lib.common.model.document

import tech.kzen.lib.common.structure.notation.format.YamlUtils


data class DocumentName(
        val value: String
) {
    companion object {
        fun ofFilenameWithDefaultExtension(filename: String): DocumentName {
            return DocumentName("$filename.${YamlUtils.fileExtension}")
        }
    }


    fun withoutExtension(): String {
        return value.substringBeforeLast(".")
    }


    override fun toString(): String {
        return value
    }
}