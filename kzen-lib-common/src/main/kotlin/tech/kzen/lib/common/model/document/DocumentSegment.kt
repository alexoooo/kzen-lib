package tech.kzen.lib.common.model.document


data class DocumentSegment(
        val value: String
) {
    override fun toString(): String {
        return value
    }
}