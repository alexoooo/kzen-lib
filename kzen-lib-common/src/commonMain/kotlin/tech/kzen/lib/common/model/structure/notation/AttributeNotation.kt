package tech.kzen.lib.common.model.structure.notation

import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible
import tech.kzen.lib.platform.collect.*


//---------------------------------------------------------------------------------------------------------------------
sealed class AttributeNotation: Digestible {
    open fun asString(): String? {
        return (this as? ScalarAttributeNotation)
                ?.value
    }

    fun asBoolean(): Boolean? {
        val asString = asString()
                ?: return null

        return when (asString) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

    fun asInt(): Int? {
        return asString()?.toIntOrNull()
    }

    fun asLong(): Long? {
        return asString()?.toLongOrNull()
    }

    fun asDouble(): Double? {
        return asString()?.toDoubleOrNull()
    }


    abstract fun get(attributeNesting: AttributeNesting): AttributeNotation?

    abstract fun merge(previous: AttributeNotation): AttributeNotation
}


//---------------------------------------------------------------------------------------------------------------------
data class ScalarAttributeNotation(
    val value: String
): AttributeNotation() {
    override fun asString(): String {
        return value
    }


    override fun get(attributeNesting: AttributeNesting): AttributeNotation? {
        return when {
            attributeNesting.segments.isEmpty() ->
                this

            else ->
                null
        }
    }


    override fun merge(previous: AttributeNotation): AttributeNotation {
        return this
    }


    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(value)
    }


    override fun toString(): String {
        return value
    }
}


//---------------------------------------------------------------------------------------------------------------------
sealed class StructuredAttributeNotation: AttributeNotation() {
    abstract fun get(key: String): AttributeNotation?
    abstract fun get(key: AttributeSegment): AttributeNotation?


    override fun get(attributeNesting: AttributeNesting): AttributeNotation? {
        if (attributeNesting.segments.isEmpty()) {
            return this
        }

        var cursor = get(attributeNesting.segments[0])

        var index = 1
        while (cursor != null && index < attributeNesting.segments.size) {
            if (cursor !is StructuredAttributeNotation) {
                return null
            }

            cursor = cursor.get(attributeNesting.segments[index])

            index++
        }

        return cursor
    }


    abstract fun isEmpty(): Boolean


    fun upsert(
        attributeNesting: AttributeNesting,
        value: AttributeNotation
    ): StructuredAttributeNotation {
        val nextPathSegment = attributeNesting.segments[0]

        val nextValue =
            if (attributeNesting.segments.size == 1) {
                value
            }
            else {
                val subNotation = get(nextPathSegment) as? StructuredAttributeNotation
                    ?: throw IllegalArgumentException("Not found: $attributeNesting - $this")

                subNotation.upsert(
                    attributeNesting.shift(),
                    value)
            }

        return when (this) {
            is ListAttributeNotation -> {
                val index = nextPathSegment.asIndex()!!
                set(PositionIndex(index), nextValue)
            }

            is MapAttributeNotation -> {
                put(nextPathSegment, nextValue)
            }
        }
    }
}


//---------------------------------------------------------------------------------------------------------------------
data class ListAttributeNotation(
    val values: PersistentList<AttributeNotation>
): StructuredAttributeNotation() {
    companion object {
        val empty = ListAttributeNotation(persistentListOf())
    }


    private var digest: Digest? = null


    override fun get(key: String): AttributeNotation? {
        val index = key.toIntOrNull()

        return when {
            index == null || index < 0  || index >= values.size ->
                null

            else ->
                values[index]
        }
    }


    override fun get(key: AttributeSegment): AttributeNotation? {
        return get(key.asKey())
    }


    override fun isEmpty(): Boolean {
        return values.isEmpty()
    }


    override fun merge(previous: AttributeNotation): AttributeNotation {
        return this
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


    fun insertAll(
        positionIndex: PositionIndex,
        attributeNotations: List<AttributeNotation>
    ): ListAttributeNotation {
        return ListAttributeNotation(
                values.addAll(positionIndex.value, attributeNotations))
    }


    fun remove(
        positionIndex: PositionIndex
    ): ListAttributeNotation {
        return ListAttributeNotation(
                values.removeAt(positionIndex.value))
    }


    override fun digest(sink: Digest.Sink) {
        sink.addDigest(digest())
    }


    override fun digest(): Digest {
        if (digest == null) {
            val builder = Digest.Builder()
            builder.addDigestibleList(values)
            digest = builder.digest()
        }
        return digest!!
    }


    override fun toString(): String {
        return values.toString()
    }
}


//---------------------------------------------------------------------------------------------------------------------
data class MapAttributeNotation(
    val values: PersistentMap<AttributeSegment, AttributeNotation>
): StructuredAttributeNotation() {
    companion object {
        val empty = MapAttributeNotation(persistentMapOf())
    }


    private var digest: Digest? = null


    override fun get(key: AttributeSegment): AttributeNotation? {
        return values[key]
    }


    override fun get(key: String): AttributeNotation? {
        return values[AttributeSegment.ofKey(key)]
    }


    override fun isEmpty(): Boolean {
        return values.isEmpty()
    }


    override fun merge(previous: AttributeNotation): AttributeNotation {
        if (previous !is MapAttributeNotation) {
            return this
        }

        val notationIntersection =
            values.keys.partition { it in previous.values }

        val ancestorIntersection =
            previous.values.keys.partition { it in values }

        val uniqueNotation =
            values.filterKeys { it !in previous.values } +
                    previous.values.filterKeys { it !in values }

        val intersectionKeys =
            notationIntersection.first.union(ancestorIntersection.first)

        val intersectionNotation =
            intersectionKeys.map { key ->
                val notationValue = get(key)!!
                val ancestorValue = previous.get(key)!!
                key to notationValue.merge(ancestorValue)
            }

        val allNotation = uniqueNotation + intersectionNotation

        return MapAttributeNotation(
            allNotation.toPersistentMap())
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


    override fun digest(sink: Digest.Sink) {
        sink.addDigest(digest())
    }


    override fun digest(): Digest {
        if (digest == null) {
            val builder = Digest.Builder()
            builder.addDigestibleOrderedMap(values)
            digest = builder.digest()
        }
        return digest!!
    }


    override fun toString(): String {
        return values.toString()
    }
}

