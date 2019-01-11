package tech.kzen.lib.common.api.model


data class AttributeName(
        val value: String
) {
    fun asAttributeNesting(): AttributeNesting {
        return AttributeNesting.ofAttribute(this)
    }
}