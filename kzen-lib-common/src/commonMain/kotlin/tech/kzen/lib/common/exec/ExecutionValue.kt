package tech.kzen.lib.common.exec

import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.IoUtils


//---------------------------------------------------------------------------------------------------------------------
// TODO: use JsonElement from kotlinx.serialization?
@Suppress("ConstPropertyName")
sealed class ExecutionValue
    : Digestible
{
    companion object {
        private val className = ClassName("tech.kzen.auto.common.paradigm.common.model.ExecutionValue")

        @Suppress("unused")
        val typeMetadata = TypeMetadata.of(className)

        private const val typeKey = "type"
        private const val valueKey = "value"

        private const val nullType = "null"
        private const val textType = "text"
        private const val booleanType = "boolean"
        private const val numberType = "number"
        private const val longType = "long"
        private const val binaryType = "binary"
        private const val listType = "list"
        private const val mapType = "map"
        private const val jsonPrimitiveType = "json"


        fun of(value: Any?): ExecutionValue {
            return ofArbitrary(value)
                ?: TODO("Not supported (yet): $value")
        }


        @Suppress("MemberVisibilityCanBePrivate")
        fun ofArbitrary(value: Any?): ExecutionValue? {
            return when (value) {
                null ->
                    NullExecutionValue

                is String ->
                    TextExecutionValue(value)

                is Boolean ->
                    BooleanExecutionValue.of(value)

                is Long ->
                    LongExecutionValue(value)

                is Number ->
                    NumberExecutionValue(value.toDouble())

                is ByteArray ->
                    BinaryExecutionValue(value)

                is List<*> ->
                    ListExecutionValue(value.map {
                        ofArbitrary(it) ?: return null
                    })

                is Map<*, *> ->
                    MapExecutionValue(value.entries.map {
                        val key = it.key as? String
                            ?: return null

                        val subValue = ofArbitrary(it.value)
                            ?: return null

                        key to subValue
                    }.toMap())

                else -> null
            }
        }


        fun fromJsonCollection(asCollection: Map<String, Any>): ExecutionValue {
            return when (asCollection[typeKey]) {
                null ->
                    throw IllegalArgumentException("'${typeKey}' missing: $asCollection")

                nullType ->
                    NullExecutionValue

                textType ->
                    TextExecutionValue(asCollection[valueKey] as String)

                booleanType ->
                    BooleanExecutionValue.of(asCollection[valueKey] as Boolean)

                numberType -> {
                    when (val value = asCollection[valueKey]) {
                        is Double -> {
                            NumberExecutionValue(value)
                        }

                        is String -> {
                            // NB: handle Infinity
                            NumberExecutionValue(value.toDouble())
                        }

                        else -> {
                            throw IllegalArgumentException("'${typeKey}' not a number: $asCollection")
                        }
                    }
                }

                longType ->
                    LongExecutionValue(
                        (asCollection[valueKey] as String).toLong())

                binaryType ->
                    BinaryExecutionValue(
                        IoUtils.base64Decode(asCollection[valueKey] as String))

                listType ->
                    ListExecutionValue(
                        (asCollection[valueKey] as List<*>).map {
                            @Suppress("UNCHECKED_CAST")
                            fromJsonCollection(it as Map<String, Any>)
                        }
                    )

                mapType ->
                    MapExecutionValue(
                        (asCollection[valueKey] as Map<*, *>).map{
                            @Suppress("UNCHECKED_CAST")
                            it.key as String to fromJsonCollection(it.value as Map<String, Any>)
                        }.toMap()
                    )

                jsonPrimitiveType ->
                    fromJsonPrimitiveCollection(asCollection[valueKey] as Any)

                else ->
                    TODO("Not supported (yet): $asCollection")
            }
        }


        private fun fromJsonPrimitiveCollection(primitiveCollection: Any): ExecutionValue {
            return when (primitiveCollection) {
                is String ->
                    TextExecutionValue(primitiveCollection)

                is Boolean ->
                    BooleanExecutionValue.of(primitiveCollection)

                is Double ->
                    NumberExecutionValue(primitiveCollection)

                is List<*> ->
                    ListExecutionValue(primitiveCollection.map { fromJsonPrimitiveCollection(it as Any) })

                is Map<*, *> ->
                    MapExecutionValue(primitiveCollection
                        .map { it.key as String to fromJsonPrimitiveCollection(it.value as Any) }
                        .toMap())

                else ->
                    throw IllegalArgumentException("string JSON expected: $primitiveCollection")
            }
        }
    }


    fun get(): Any? {
        return when (this) {
            NullExecutionValue ->
                null

            is TextExecutionValue ->
                value

            is BooleanExecutionValue ->
                value

            is NumberExecutionValue ->
                value

            is LongExecutionValue ->
                value

            is BinaryExecutionValue ->
                value

            is ListExecutionValue ->
                values.map { it.get() }

            is MapExecutionValue ->
                values.mapValues { it.value.get() }
        }
    }


    fun toJsonCollection(): Map<String, Any> {
        if (isJsonPrimitive()) {
            return when (this) {
                is TextExecutionValue ->
                    typedValue(textType, value)

                is BooleanExecutionValue ->
                    typedValue(booleanType, value)

                is NumberExecutionValue ->
                    typedValue(numberType, value)

                else -> error("primitive expected: $this")
            }
        }

        if (isJsonPrimitiveCollection()) {
            return typedValue(jsonPrimitiveType, toJsonPrimitiveCollection())
        }

        return when (this) {
            NullExecutionValue -> mapOf(
                typeKey to nullType)

            is NumberExecutionValue ->
                typedValue(numberType, value)

            is LongExecutionValue ->
                typedValue(longType, value.toString())

            is BinaryExecutionValue ->
                typedValue(binaryType, IoUtils.base64Encode(value))

            is ListExecutionValue ->
                typedValue(listType, values.map { it.toJsonCollection() })

            is MapExecutionValue ->
                typedValue(mapType, values.mapValues { it.value.toJsonCollection() })

            is BooleanExecutionValue,
            is TextExecutionValue ->
                error("unexpected primitive: $this")
        }
    }


    private fun isJsonPrimitive(): Boolean {
        return this is TextExecutionValue ||
                this is BooleanExecutionValue ||
                this is NumberExecutionValue && value.isFinite()
    }


    private fun isJsonPrimitiveCollection(): Boolean {
        return isJsonPrimitive() ||
                this is ListExecutionValue && values.all { it.isJsonPrimitiveCollection() } ||
                this is MapExecutionValue && values.all { it.value.isJsonPrimitiveCollection() }
    }


    private fun toJsonPrimitiveCollection(): Any {
        return when (this) {
            is TextExecutionValue -> value
            is BooleanExecutionValue -> value
            is NumberExecutionValue -> value
            is ListExecutionValue -> values.map { it.toJsonPrimitiveCollection() }
            is MapExecutionValue -> values.mapValues { it.value.toJsonPrimitiveCollection() }
            else -> error("primitive expected: $this")
        }
    }


    private fun typedValue(type: String, value: Any): Map<String, Any> {
        return mapOf(
                typeKey to type,
                valueKey to value)
    }


    override fun digest(): Digest {
        val digest = Digest.Builder()
        digest(digest)
        return digest.digest()
    }


    override fun digest(sink: Digest.Sink) {
        when (this) {
            NullExecutionValue ->
                sink.addInt(0)

            is TextExecutionValue -> {
                sink.addInt(1)
                sink.addUtf8(value)
            }

            is BooleanExecutionValue -> {
                sink.addInt(2)
                sink.addBoolean(value)
            }

            is NumberExecutionValue -> {
                sink.addInt(3)
                sink.addDouble(value)
            }

            is LongExecutionValue -> {
                sink.addInt(4)
                sink.addLong(value)
            }

            is BinaryExecutionValue -> {
                sink.addInt(5)
                sink.addBytes(value)
            }

            is ListExecutionValue -> {
                sink.addInt(6)
                sink.addDigestibleList(values)
            }

            is MapExecutionValue -> {
                sink.addInt(7)
                sink.addInt(values.size)
                sink.addCollection(values.entries) {
                    addUtf8(it.key)
                    addDigestible(it.value)
                }
            }
        }
    }
}


//---------------------------------------------------------------------------------------------------------------------
object NullExecutionValue: ExecutionValue() {
    override fun toString(): String {
        return "null"
    }
}


//---------------------------------------------------------------------------------------------------------------------
sealed class ScalarExecutionValue: ExecutionValue()


data class TextExecutionValue(
    val value: String
): ScalarExecutionValue() {
    override fun toString(): String {
        return "\"${value.replace("\"", "\\\"")}\""
    }
}


data class BooleanExecutionValue(
    val value: Boolean
): ScalarExecutionValue() {
    companion object {
        private val trueInstance = BooleanExecutionValue(true)
        private val falseInstance = BooleanExecutionValue(false)

        fun of(value: Boolean): BooleanExecutionValue {
            return when (value) {
                true -> trueInstance
                false -> falseInstance
            }
        }
    }

    override fun toString(): String {
        return value.toString()
    }
}


data class NumberExecutionValue(
    val value: Double
): ScalarExecutionValue() {
    override fun toString(): String {
        return value.toString()
    }
}


data class LongExecutionValue(
    val value: Long
): ScalarExecutionValue() {
    override fun toString(): String {
        return value.toString()
    }
}


data class BinaryExecutionValue(
    val value: ByteArray
): ScalarExecutionValue() {
    private val cache: MutableMap<String, Any> = mutableMapOf()

    @Suppress("UNCHECKED_CAST")
    fun <T> cache(key: String, valueProvider: () -> T): T {
        val existing = cache[key]
        if (existing != null) {
            return existing as T
        }

        val added = valueProvider()
        cache[key] = added as Any

        return added
    }


    fun asBase64(): String {
        return cache("base64") { IoUtils.base64Encode(value) }
    }


    override fun toString(): String {
        return asBase64()
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as BinaryExecutionValue

        return value.contentEquals(other.value)
    }


    override fun hashCode(): Int {
        return value.contentHashCode()
    }
}


//---------------------------------------------------------------------------------------------------------------------
sealed class StructuredExecutionValue: ExecutionValue()


data class ListExecutionValue(
    val values: List<ExecutionValue>
): StructuredExecutionValue() {
    operator fun get(index: Int): ExecutionValue {
        return values[index]
    }

    override fun toString(): String {
        return values.toString()
    }
}


data class MapExecutionValue(
    val values: Map<String, ExecutionValue>
): StructuredExecutionValue() {
    operator fun get(key: String): ExecutionValue? {
        return values[key]
    }

    override fun toString(): String {
        return values.toString()
    }
}