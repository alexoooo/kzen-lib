package tech.kzen.lib.common.api.model


data class AttributeName(
        val value: String
) {
    fun asAttributeNesting(): AttributePath {
        return AttributePath.ofAttribute(this)
    }


    override fun toString(): String {
        return value
    }
}