package tech.kzen.lib.common.api.model


sealed class AttributeSegment {
    abstract fun asString(): String
}


data class ListIndexAttributeSegment(
        val index: Int
): AttributeSegment() {
    override fun asString(): String {
        return index.toString()
    }
}


data class MapKeyAttributeSegment(
        val key: String
): AttributeSegment() {
    override fun asString(): String {
        return key
    }
}