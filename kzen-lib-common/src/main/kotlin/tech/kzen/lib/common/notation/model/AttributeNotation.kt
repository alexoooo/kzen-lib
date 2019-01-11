package tech.kzen.lib.common.notation.model

import tech.kzen.lib.common.api.model.AttributeNesting
import tech.kzen.lib.common.api.model.ListIndexAttributeSegment
import tech.kzen.lib.common.api.model.MapKeyAttributeSegment


//---------------------------------------------------------------------------------------------------------------------
sealed class AttributeNotation {
    fun asString(): String? {
        return (this as? ScalarAttributeNotation)
                ?.value
                as? String
    }

    fun asBoolean(): Boolean? {
        return (this as? ScalarAttributeNotation)
                ?.value
                as? Boolean
    }
}


//---------------------------------------------------------------------------------------------------------------------
data class ScalarAttributeNotation(
        val value: Any?
): AttributeNotation()


sealed class StructuredAttributeNotation: AttributeNotation() {
    abstract fun get(key: String): AttributeNotation?


    fun get(key: MapKeyAttributeSegment): AttributeNotation? =
        get(key.key)

    fun get(index: ListIndexAttributeSegment): AttributeNotation? =
        get(index.index.toString())


    fun get(notationPath: AttributeNesting): AttributeNotation? {
        check(! notationPath.segments.isEmpty())

        var cursor = get(notationPath.attribute.value)

        var index = 0
        while (cursor != null && index < notationPath.segments.size) {
            if (cursor !is StructuredAttributeNotation) {
                return null
            }

            cursor = cursor.get(notationPath.segments[index].asString())

            index++
        }

        return cursor
    }
}


data class ListAttributeNotation(
        val values: List<AttributeNotation>
): StructuredAttributeNotation() {
    override fun get(key: String): AttributeNotation? {
        val index = key.toInt()
        return values[index]
    }
}


data class MapAttributeNotation(
        val values: Map<MapKeyAttributeSegment, AttributeNotation>
): StructuredAttributeNotation() {
    override fun get(key: String): AttributeNotation? {
        return values[MapKeyAttributeSegment(key)]
    }
}

