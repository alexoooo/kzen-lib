package tech.kzen.lib.common.model.structure.notation.codec

import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.platform.collect.toPersistentList


/**
 * A bidirectional binding between a typed value [T] and its [AttributeNotation] form. `parse` is the
 * read path a definer runs; `unparse` is the write path that used to be hand-duplicated across command
 * builders and default templates. Keeping both on one object makes the notation key layout a single
 * source of truth, so the read and write sides cannot drift.
 *
 * Combinators live on [NotationCodecs]; compose them (or wrap with [xmap]) rather than hand-walking
 * notation. See `CodecAttributeDefiner` for the definer that adapts a codec to the AttributeDefiner SPI.
 */
interface NotationCodec<T> {
    fun parse(notation: AttributeNotation): T
    fun unparse(value: T): AttributeNotation
}


//---------------------------------------------------------------------------------------------------------------------
/** Adapts a codec of [A] to a codec of [B] via a value-level isomorphism (e.g. wrap a `Map` in a spec). */
fun <A, B> NotationCodec<A>.xmap(to: (A) -> B, from: (B) -> A): NotationCodec<B> =
    object : NotationCodec<B> {
        override fun parse(notation: AttributeNotation): B =
            to(this@xmap.parse(notation))

        override fun unparse(value: B): AttributeNotation =
            this@xmap.unparse(from(value))
    }


//---------------------------------------------------------------------------------------------------------------------
// Field readers, used inside a [NotationCodecs.record] `decode` lambda.

/** Read a required named field; throws if absent (the record is malformed). */
fun <T> MapAttributeNotation.field(key: String, codec: NotationCodec<T>): T {
    val sub = this[key]
        ?: throw IllegalArgumentException("Missing field '$key': $this")
    return codec.parse(sub)
}

/** Read a named field, falling back to [default] when the field is absent. */
fun <T> MapAttributeNotation.field(key: String, codec: NotationCodec<T>, default: T): T {
    val sub = this[key]
        ?: return default
    return codec.parse(sub)
}

/** Read a named field, or `null` when absent. */
fun <T> MapAttributeNotation.fieldOrNull(key: String, codec: NotationCodec<T>): T? {
    val sub = this[key]
        ?: return null
    return codec.parse(sub)
}


/** Build a [MapAttributeNotation] from ordered key/notation pairs (the `record` write side). */
fun recordOf(entries: List<Pair<String, AttributeNotation>>): MapAttributeNotation {
    var result = MapAttributeNotation.empty
    for ((key, value) in entries) {
        result = result.put(AttributeSegment.ofKey(key), value)
    }
    return result
}

fun recordOf(vararg entries: Pair<String, AttributeNotation>): MapAttributeNotation =
    recordOf(entries.asList())


//---------------------------------------------------------------------------------------------------------------------
/** The combinator library. Leaf scalars, collections, records — enough to express notation specs declaratively. */
object NotationCodecs {
    //-----------------------------------------------------------------------------------------------------------------
    val scalar: NotationCodec<String> = object : NotationCodec<String> {
        override fun parse(notation: AttributeNotation): String =
            notation.asString()
                ?: throw IllegalArgumentException("Scalar expected: $notation")

        override fun unparse(value: String): AttributeNotation =
            ScalarAttributeNotation(value)
    }


    val boolean: NotationCodec<Boolean> = object : NotationCodec<Boolean> {
        override fun parse(notation: AttributeNotation): Boolean =
            notation.asBoolean()
                ?: throw IllegalArgumentException("Boolean expected: $notation")

        override fun unparse(value: Boolean): AttributeNotation =
            ScalarAttributeNotation(value.toString())
    }


    val int: NotationCodec<Int> = object : NotationCodec<Int> {
        override fun parse(notation: AttributeNotation): Int =
            notation.asInt()
                ?: throw IllegalArgumentException("Int expected: $notation")

        override fun unparse(value: Int): AttributeNotation =
            ScalarAttributeNotation(value.toString())
    }


    val long: NotationCodec<Long> = object : NotationCodec<Long> {
        override fun parse(notation: AttributeNotation): Long =
            notation.asLong()
                ?: throw IllegalArgumentException("Long expected: $notation")

        override fun unparse(value: Long): AttributeNotation =
            ScalarAttributeNotation(value.toString())
    }


    val double: NotationCodec<Double> = object : NotationCodec<Double> {
        override fun parse(notation: AttributeNotation): Double =
            notation.asDouble()
                ?: throw IllegalArgumentException("Double expected: $notation")

        override fun unparse(value: Double): AttributeNotation =
            ScalarAttributeNotation(value.toString())
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** A scalar codec whose text maps to/from [T] (e.g. a `HeaderLabel`'s string form). */
    fun <T> scalarMapped(from: (String) -> T, to: (T) -> String): NotationCodec<T> =
        object : NotationCodec<T> {
            override fun parse(notation: AttributeNotation): T {
                val text = notation.asString()
                    ?: throw IllegalArgumentException("Scalar expected: $notation")
                return from(text)
            }

            override fun unparse(value: T): AttributeNotation =
                ScalarAttributeNotation(to(value))
        }


    /** A scalar codec for an enum, keyed by [Enum.name]. */
    inline fun <reified E : Enum<E>> enum(): NotationCodec<E> {
        val values = enumValues<E>()
        return object : NotationCodec<E> {
            override fun parse(notation: AttributeNotation): E {
                val name = notation.asString()
                    ?: throw IllegalArgumentException("Scalar expected: $notation")
                return values.firstOrNull { it.name == name }
                    ?: throw IllegalArgumentException(
                        "Unknown value '$name', expected one of ${values.map { it.name }}")
            }

            override fun unparse(value: E): AttributeNotation =
                ScalarAttributeNotation(value.name)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** A [ListAttributeNotation] of [element]s, order preserved. */
    fun <T> list(element: NotationCodec<T>): NotationCodec<List<T>> =
        object : NotationCodec<List<T>> {
            override fun parse(notation: AttributeNotation): List<T> {
                val listNotation = notation as? ListAttributeNotation
                    ?: throw IllegalArgumentException("List expected: $notation")
                return listNotation.values.map { element.parse(it) }
            }

            override fun unparse(value: List<T>): AttributeNotation =
                ListAttributeNotation(value.map { element.unparse(it) }.toPersistentList())
        }


    /** A [ListAttributeNotation] collected into an insertion-ordered `Set`. */
    fun <T> set(element: NotationCodec<T>): NotationCodec<Set<T>> =
        object : NotationCodec<Set<T>> {
            override fun parse(notation: AttributeNotation): Set<T> {
                val listNotation = notation as? ListAttributeNotation
                    ?: throw IllegalArgumentException("List expected: $notation")
                return listNotation.values.map { element.parse(it) }.toSet()
            }

            override fun unparse(value: Set<T>): AttributeNotation =
                ListAttributeNotation(value.map { element.unparse(it) }.toPersistentList())
        }


    /** A [MapAttributeNotation] whose keys map to/from [K] and values decode via [valueCodec]; order preserved. */
    fun <K, V> map(
        keyFromString: (String) -> K,
        keyToString: (K) -> String,
        valueCodec: NotationCodec<V>
    ): NotationCodec<Map<K, V>> =
        object : NotationCodec<Map<K, V>> {
            override fun parse(notation: AttributeNotation): Map<K, V> {
                val mapNotation = notation as? MapAttributeNotation
                    ?: throw IllegalArgumentException("Map expected: $notation")
                val result = LinkedHashMap<K, V>()
                for ((segment, sub) in mapNotation.map) {
                    result[keyFromString(segment.asKey())] = valueCodec.parse(sub)
                }
                return result
            }

            override fun unparse(value: Map<K, V>): AttributeNotation {
                var result = MapAttributeNotation.empty
                for ((key, sub) in value) {
                    result = result.put(AttributeSegment.ofKey(keyToString(key)), valueCodec.unparse(sub))
                }
                return result
            }
        }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * A record: a [MapAttributeNotation] of heterogeneous named fields. `decode` reads fields (via
     * [field] / [fieldOrNull]); `encode` lists them back in write order (via each field codec's `unparse`).
     */
    fun <T> record(
        decode: (MapAttributeNotation) -> T,
        encode: (T) -> List<Pair<String, AttributeNotation>>
    ): NotationCodec<T> =
        object : NotationCodec<T> {
            override fun parse(notation: AttributeNotation): T {
                val mapNotation = notation as? MapAttributeNotation
                    ?: throw IllegalArgumentException("Map expected: $notation")
                return decode(mapNotation)
            }

            override fun unparse(value: T): AttributeNotation =
                recordOf(encode(value))
        }
}
