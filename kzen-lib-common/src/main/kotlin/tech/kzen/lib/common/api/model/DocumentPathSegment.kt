package tech.kzen.lib.common.api.model


data class DocumentPathSegment(
        val value: String
) {
    override fun toString(): String {
        return value
    }
}