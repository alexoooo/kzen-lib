package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.BooleanExecutionValue
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.NumberExecutionValue
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.NativeTypeToken
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.VariantId
import java.lang.reflect.Array as ReflectArray
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.isAccessible


/** Lazy, identity-preserving JVM view. Reflection plans and first-resolved child positions are cached. */
internal class NativeObjectValueAccess private constructor(
    rootValue: Any?,
    rootContract: DataContract,
    private val registry: DefaultDataAdapterRegistry
): JvmNativeValueAccess {
    companion object {
        fun value(
            value: Any?,
            contract: DataContract,
            registry: DefaultDataAdapterRegistry
        ): DataValue {
            val access = NativeObjectValueAccess(value, contract, registry)
            return DataValue(access, DataNode(0))
        }
    }


    private val nodes = mutableListOf(NodeEntry(
        rootContract,
        rootValue,
        emptyList(),
        nativeToken(rootValue)))


    override fun contract(node: DataNode): DataContract = get(node).contract

    override fun state(node: DataNode): DataState =
        if (get(node).value == null) DataState.Null else DataState.Present


    override fun activeVariant(node: DataNode): VariantId =
        invalid(node, "Native baseline does not expose an untagged union")

    override fun selected(node: DataNode): DataNode =
        invalid(node, "Native baseline does not expose an untagged union")


    override fun field(node: DataNode, field: FieldId): DataNode {
        val entry = present(node, "field")
        val type = entry.contract.structural as? DataType.Record
            ?: invalid(node, "field is valid only for record nodes")
        type.fields.firstOrNull { it.id == field }
            ?: invalid(node, "Unknown record field '$field'")
        return entry.fields.getOrPut(field) {
            val reader = NativePropertyPlans.reader(entry.value!!::class, field)
            val childValue = try {
                reader(entry.value)
            }
            catch (e: ReflectiveOperationException) {
                throw DataAccessException(DataProblem(
                    DataProblem.invalidValue,
                    "Unable to read native field '$field': ${e.message}",
                    entry.path + DataPathSegment.Field(field)))
            }
            val expected = entry.contract.child(DataPathSegment.Field(field))
            add(childValue, actualChildContract(childValue, expected),
                entry.path + DataPathSegment.Field(field))
        }
    }


    override fun entry(node: DataNode, key: ScalarExecutionValue): DataNode {
        val parent = present(node, "entry")
        val type = parent.contract.structural as? DataType.Mapping
            ?: invalid(node, "entry is valid only for mapping nodes")
        val map = parent.value as? Map<*, *>
            ?: invalid(node, "Native mapping node no longer contains a Map")
        val found = map.entries.firstOrNull { (candidate, _) ->
            candidate != null && scalarValue(candidate, type.key as DataType.Scalar) == key
        } ?: invalid(node, "Unknown mapping key '$key'")
        return parent.entries.getOrPut(key) {
            val expected = parent.contract.child(DataPathSegment.MappingValue)
            add(found.value, actualChildContract(found.value, expected),
                parent.path + DataPathSegment.Entry((type.key as DataType.Scalar).kind, key))
        }
    }


    override fun element(node: DataNode, index: Int): DataNode {
        val parent = present(node, "element")
        parent.contract.structural as? DataType.Listing
            ?: invalid(node, "element is valid only for listing nodes")
        val size = sequenceSize(parent.value!!)
        if (index !in 0 until size) invalid(node, "Listing index out of bounds: $index")
        return parent.elements.getOrPut(index) {
            val childValue = sequenceElement(parent.value, index)
            val expected = parent.contract.child(DataPathSegment.ListingElement)
            add(childValue, actualChildContract(childValue, expected),
                parent.path + DataPathSegment.Element(index))
        }
    }


    override fun size(node: DataNode): Int {
        val entry = present(node, "size")
        return when (entry.contract.structural) {
            is DataType.Listing -> sequenceSize(entry.value!!)
            is DataType.Mapping -> (entry.value as? Map<*, *>)?.size
                ?: invalid(node, "Native mapping node no longer contains a Map")
            else -> invalid(node, "size is valid only for listing and mapping nodes")
        }
    }


    override fun keyAt(node: DataNode, index: Int): ScalarExecutionValue {
        val entry = present(node, "keyAt")
        val type = entry.contract.structural as? DataType.Mapping
            ?: invalid(node, "keyAt is valid only for mapping nodes")
        val map = entry.value as? Map<*, *>
            ?: invalid(node, "Native mapping node no longer contains a Map")
        if (index !in 0 until map.size) invalid(node, "Mapping index out of bounds: $index")
        val key = map.keys.elementAt(index)
            ?: invalid(node, "Mapping key became null after publication")
        return scalarValue(key, type.key as DataType.Scalar)
    }


    override fun scalar(node: DataNode): ScalarExecutionValue {
        val entry = present(node, "scalar")
        val type = entry.contract.structural as? DataType.Scalar
            ?: invalid(node, "scalar is valid only for scalar nodes")
        return scalarValue(entry.value!!, type)
    }


    override fun readBoolean(node: DataNode): Boolean {
        val value = present(node, "readBoolean").value
        return value as? Boolean ?: invalid(node, "Boolean scalar required")
    }


    override fun readLong(node: DataNode): Long {
        val value = present(node, "readLong").value
        return when (value) {
            is Byte -> value.toLong()
            is Short -> value.toLong()
            is Int -> value.toLong()
            is Long -> value
            is UByte -> value.toLong()
            is UShort -> value.toLong()
            is UInt -> value.toLong()
            is ULong -> value.takeIf { it <= Long.MAX_VALUE.toULong() }?.toLong()
                ?: invalid(node, "Unsigned integer cannot be represented exactly as Long")
            is BigInteger -> try { value.longValueExact() }
                catch (_: ArithmeticException) { invalid(node, "Integer cannot be represented exactly as Long") }
            else -> invalid(node, "Integer scalar required")
        }
    }


    override fun readDouble(node: DataNode): Double {
        val value = present(node, "readDouble").value
        val projected = when (value) {
            is Float -> value.toDouble()
            is Double -> value
            is Byte -> value.toDouble()
            is Short -> value.toDouble()
            is Int -> value.toDouble()
            is Long -> value.toDouble().takeIf { it.toLong() == value }
            else -> null
        }
        return projected?.takeIf { it.isFinite() }
            ?: invalid(node, "Numeric scalar cannot be represented exactly as Double")
    }


    override fun readText(node: DataNode): String =
        present(node, "readText").value as? String
            ?: invalid(node, "Text scalar required")


    override fun readBinary(node: DataNode): ByteArray =
        (present(node, "readBinary").value as? ByteArray)?.copyOf()
            ?: invalid(node, "Binary scalar required")


    override fun native(node: DataNode): Any {
        val entry = present(node, "native")
        if (entry.contract.nativeByPath[tech.kzen.lib.common.exec.data.type.DataTypePath.root] == null) {
            invalid(node, "Node has no native facet")
        }
        return entry.value!!
    }


    override fun nativeType(node: DataNode): NativeTypeToken? = get(node).nativeType


    private fun actualChildContract(value: Any?, expected: DataContract): DataContract =
        try {
            when {
                expected.structural is DataType.Opaque -> expected
                expected.structural is DataType.Dynamic && value != null -> registry.childValue(value, null).second
                else -> registry.childValue(value, expected).second
            }
        }
        catch (e: DataException) {
            throw DataAccessException(e.problem)
        }


    private fun add(value: Any?, contract: DataContract, path: List<DataPathSegment>): DataNode {
        nodes += NodeEntry(contract, value, path, nativeToken(value))
        return DataNode(nodes.lastIndex.toLong())
    }


    private fun get(node: DataNode): NodeEntry {
        if (node.token < 0 || node.token > Int.MAX_VALUE || node.token.toInt() !in nodes.indices) {
            throw DataAccessException(DataProblem(
                DataProblem.invalidValue,
                "Data node ${node.token} does not belong to this native backing"))
        }
        return nodes[node.token.toInt()]
    }


    private fun present(node: DataNode, operation: String): NodeEntry {
        val entry = get(node)
        if (entry.value == null) {
            throw DataAccessException(DataProblem(
                DataProblem.invalidState,
                "$operation requires a present value",
                entry.path))
        }
        return entry
    }


    private fun invalid(node: DataNode, message: String): Nothing {
        val entry = get(node)
        throw DataAccessException(DataProblem(DataProblem.invalidOperation, message, entry.path))
    }
}


private class NodeEntry(
    val contract: DataContract,
    val value: Any?,
    val path: List<DataPathSegment>,
    val nativeType: NativeTypeToken?
) {
    val fields = mutableMapOf<FieldId, DataNode>()
    val elements = mutableMapOf<Int, DataNode>()
    val entries = mutableMapOf<ScalarExecutionValue, DataNode>()
}


private object NativePropertyPlans {
    private val plans = mutableMapOf<KClass<*>, Map<FieldId, (Any) -> Any?>>()

    @Synchronized
    fun reader(type: KClass<*>, field: FieldId): (Any) -> Any? =
        plans.getOrPut(type) { create(type) }[field]
            ?: throw DataAccessException(DataProblem(
                DataProblem.invalidValue,
                "Native type ${type.qualifiedName} has no planned field '$field'"))


    private fun create(type: KClass<*>): Map<FieldId, (Any) -> Any?> {
        if (type.java.isRecord) {
            return type.java.recordComponents.associate { component ->
                FieldId(component.name) to { instance: Any -> component.accessor.invoke(instance) }
            }
        }
        if (type.isData) {
            val properties = type.declaredMemberProperties.associateBy { it.name }
            return type.primaryConstructor!!.parameters.associate { parameter ->
                val name = parameter.name
                    ?: throw IllegalArgumentException("Unnamed data-class constructor parameter in $type")
                val property = properties[name]
                    ?: throw IllegalArgumentException("No declared property for data-class parameter '$name'")
                property.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val reader = property as KProperty1<Any, *>
                FieldId(name) to { instance: Any -> reader.get(instance) }
            }
        }
        throw DataAccessException(DataProblem(
            DataProblem.invalidValue,
            "Native record access is limited to Kotlin data classes and Java records: ${type.qualifiedName}"))
    }
}


private fun nativeToken(value: Any?): NativeTypeToken? =
    value?.let { NativeTypeToken(it::class.starProjectedType) }


private fun sequenceSize(value: Any): Int =
    when (value) {
        is List<*> -> value.size
        else -> if (value.javaClass.isArray) ReflectArray.getLength(value)
            else throw DataAccessException(DataProblem(
                DataProblem.invalidValue,
                "Native listing node no longer contains a List or array"))
    }


private fun sequenceElement(value: Any, index: Int): Any? =
    when (value) {
        is List<*> -> value[index]
        else -> ReflectArray.get(value, index)
    }


private fun scalarValue(value: Any, type: DataType.Scalar): ScalarExecutionValue =
    when (type.kind) {
        ScalarKind.Boolean -> BooleanExecutionValue.of(value as Boolean)
        is ScalarKind.Integer -> when (value) {
            is Byte -> LongExecutionValue(value.toLong())
            is Short -> LongExecutionValue(value.toLong())
            is Int -> LongExecutionValue(value.toLong())
            is Long -> LongExecutionValue(value)
            is UByte -> LongExecutionValue(value.toLong())
            is UShort -> LongExecutionValue(value.toLong())
            is UInt -> LongExecutionValue(value.toLong())
            else -> TextExecutionValue(value.toString())
        }
        ScalarKind.Decimal -> TextExecutionValue((value as BigDecimal).stripTrailingZeros().let {
            if (it.signum() == 0) "0" else it.toString()
        })
        is ScalarKind.Floating -> NumberExecutionValue((value as Number).toDouble())
        ScalarKind.Text -> TextExecutionValue(value as String)
        ScalarKind.Binary -> BinaryExecutionValue((value as ByteArray).copyOf())
        ScalarKind.Date -> TextExecutionValue((value as LocalDate).toString())
        ScalarKind.Time -> TextExecutionValue((value as LocalTime).toString())
        ScalarKind.Instant -> TextExecutionValue((value as Instant).toString())
        ScalarKind.Duration -> TextExecutionValue((value as Duration).toString())
        ScalarKind.Uuid -> TextExecutionValue((value as UUID).toString())
    }
