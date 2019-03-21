package tech.kzen.lib.common.api.model


data class DocumentName(
        val value: String
) {
    companion object {
        fun ofFilenameWithDefaultExtension(filename: String): DocumentName {
            return DocumentName("$filename.yaml")
        }
    }


    fun withoutExtension(): String {
        return value.substringBeforeLast(".")
    }


    override fun toString(): String {
        return value
    }
}