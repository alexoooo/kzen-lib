package tech.kzen.lib.common.notation.model

import tech.kzen.lib.common.api.model.AttributePath
import tech.kzen.lib.common.api.model.AttributeSegment


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
): AttributeNotation() {
    init {
        // TODO: rename to OpaqueAttributeNotation and allow any type?
        when (value) {
            null,
            is String,
            is Boolean,
            is Number
            -> Unit

            else ->
                throw IllegalArgumentException("Scalar value expected: $value")
        }
    }

    override fun toString(): String {
        return value.toString()
    }
}


sealed class StructuredAttributeNotation: AttributeNotation() {
    abstract fun get(key: String): AttributeNotation?


    fun get(key: AttributeSegment): AttributeNotation? =
        get(key.asKey())


    fun get(notationPath: AttributePath): AttributeNotation? {
        var cursor = get(notationPath.attribute.value)

        var index = 0
        while (cursor != null && index < notationPath.nesting.segments.size) {
            if (cursor !is StructuredAttributeNotation) {
                return null
            }

            cursor = cursor.get(notationPath.nesting.segments[index].asString())

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


    fun insert(
            attributeNotation: AttributeNotation,
            positionIndex: PositionIndex
    ): ListAttributeNotation {
        val builder = values.toMutableList()
        builder.add(positionIndex.value, attributeNotation)
        return ListAttributeNotation(builder)
    }


    override fun toString(): String {
        return values.toString()
    }
}


data class MapAttributeNotation(
        val values: Map<AttributeSegment, AttributeNotation>
): StructuredAttributeNotation() {
    override fun get(key: String): AttributeNotation? {
        return values[AttributeSegment.ofKey(key)]
    }

    override fun toString(): String {
        return values.toString()
    }
}

