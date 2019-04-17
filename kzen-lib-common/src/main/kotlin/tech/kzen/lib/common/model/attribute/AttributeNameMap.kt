package tech.kzen.lib.common.model.attribute

import tech.kzen.lib.platform.collect.PersistentMap


data class AttributeNameMap<T>(
        val values: PersistentMap<AttributeName, T>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val empty = AttributeNameMap<Any>(PersistentMap())

        @Suppress("UNCHECKED_CAST")
        fun <T> of(): AttributeNameMap<T> {
            return empty as AttributeNameMap<T>
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    operator fun get(attributeName: AttributeName): T {
        return values[attributeName]
                ?: throw IllegalArgumentException("Not found: $attributeName")
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun put(attributeName: AttributeName, value: T): AttributeNameMap<T> {
        return AttributeNameMap(values.put(attributeName, value))
    }
}