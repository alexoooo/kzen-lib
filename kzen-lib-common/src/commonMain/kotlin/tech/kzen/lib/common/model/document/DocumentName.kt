package tech.kzen.lib.common.model.document

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class DocumentName(
        val value: String
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val namePattern = Regex("[a-zA-Z0-9_\\- ]+")

        fun matches(value: String): Boolean {
            return namePattern.matches(value)
        }

//        fun ofYaml(filename: String): DocumentName {
//            return DocumentName("$filename${NotationConventions.fileDocumentSuffix}")
//        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        check(value.isNotEmpty()) {
            "Document name can't be empty"
        }
        check(matches(value)) {
            "Document name not valid"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun withoutExtension(): String {
        return value.substringBeforeLast(".")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(value)
    }


    override fun toString(): String {
        return value
    }
}