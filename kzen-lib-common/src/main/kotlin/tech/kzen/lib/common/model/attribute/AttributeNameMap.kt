package tech.kzen.lib.common.model.attribute


data class AttributeNameMap<T>(
        val values: Map<AttributeName, T>
) {
    companion object {
        private val empty = AttributeNameMap<Any>(emptyMap())

        @Suppress("UNCHECKED_CAST")
        fun <T> of(): AttributeNameMap<T> {
            return empty as AttributeNameMap<T>
        }
    }
}