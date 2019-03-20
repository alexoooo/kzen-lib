package tech.kzen.lib.common.api.model


data class DocumentName(
        private val asString: String
) {
    companion object {
        fun parse(asString: String): DocumentName {
            return DocumentName(asString)
        }
    }


    fun asString(): String {
        return AttributePath.encodeDelimiter(asString)
    }


    override fun toString(): String {
        return asString
    }
}