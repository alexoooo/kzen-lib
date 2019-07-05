package tech.kzen.lib.common.structure.notation.model

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.platform.collect.PersistentList
import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.persistentListOf
import tech.kzen.lib.platform.collect.persistentMapOf


//---------------------------------------------------------------------------------------------------------------------
sealed class AttributeNotation {
    fun asString(): String? {
        return (this as? ScalarAttributeNotation)
                ?.value
    }

    fun asBoolean(): Boolean? {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val asString = (this as? ScalarAttributeNotation)
                ?.value
                ?: return null

        return when (asString) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }
}


//---------------------------------------------------------------------------------------------------------------------
data class ScalarAttributeNotation(
        val value: String
): AttributeNotation() {
    override fun toString(): String {
        return value
    }
}


sealed class StructuredAttributeNotation: AttributeNotation() {
    abstract fun get(key: String): AttributeNotation?


    fun get(key: AttributeSegment): AttributeNotation? =
        get(key.asKey())


    fun get(attributePath: AttributePath): AttributeNotation? {
        var cursor = get(attributePath.attribute.value)

        var index = 0
        while (cursor != null && index < attributePath.nesting.segments.size) {
            if (cursor !is StructuredAttributeNotation) {
                return null
            }

            cursor = cursor.get(attributePath.nesting.segments[index].asString())

            index++
        }

        return cursor
    }
}


data class ListAttributeNotation(
        val values: PersistentList<AttributeNotation>
): StructuredAttributeNotation() {
    companion object {
        val empty = ListAttributeNotation(persistentListOf())
    }


    override fun get(key: String): AttributeNotation? {
        val index = key.toInt()
        return values[index]
    }


    fun set(
            positionIndex: PositionIndex,
            attributeNotation: AttributeNotation
    ): ListAttributeNotation {
        return ListAttributeNotation(
                values.set(positionIndex.value, attributeNotation))
    }


    fun insert(
            positionIndex: PositionIndex,
            attributeNotation: AttributeNotation
    ): ListAttributeNotation {
        return ListAttributeNotation(
                values.add(positionIndex.value, attributeNotation))
    }


    fun remove(
            positionIndex: PositionIndex
    ): ListAttributeNotation {
        return ListAttributeNotation(
                values.removeAt(positionIndex.value))
    }


    override fun toString(): String {
        return values.toString()
    }
}


data class MapAttributeNotation(
        val values: PersistentMap<AttributeSegment, AttributeNotation>
): StructuredAttributeNotation() {
    companion object {
        val empty = MapAttributeNotation(persistentMapOf())
    }


    override fun get(key: String): AttributeNotation? {
        return values[AttributeSegment.ofKey(key)]
    }


    fun put(
            key: AttributeSegment,
            attributeNotation: AttributeNotation
    ): MapAttributeNotation {
        return MapAttributeNotation(
                values.put(key, attributeNotation))
    }


    fun insert(
            attributeNotation: AttributeNotation,
            key: AttributeSegment,
            positionIndex: PositionIndex
    ): MapAttributeNotation {
        check(key !in values) {
            "Already exists: $key"
        }
        check(positionIndex.value <= values.size) {
            "Position ($positionIndex) must be <= size (${values.size})"
        }

        return MapAttributeNotation(
                values.insert(key, attributeNotation, positionIndex.value))
    }


    fun remove(
            key: AttributeSegment
    ): MapAttributeNotation {
        return MapAttributeNotation(
                values.remove(key))
    }


    override fun toString(): String {
        return values.toString()
    }
}

