package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.DataTypeAlgebra
import tech.kzen.lib.common.exec.data.type.DefaultNativeTypeResolver
import tech.kzen.lib.common.exec.data.type.TypeAcceptance
import tech.kzen.lib.common.exec.data.type.VariantSelection
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.starProjectedType


class DefaultDataAdapterRegistry(
    exact: List<ExactDataAdapter> = emptyList(),
    private val fallbacks: List<CapabilityDataAdapter> = emptyList(),
    private val nativeTypeResolver: DefaultNativeTypeResolver = DefaultNativeTypeResolver()
): DataAdapterRegistry, AutoCloseable {
    private val exactByClass: Map<KClass<*>, DataAdapter>

    init {
        val duplicate = exact.groupBy { it.nativeClass }.entries.firstOrNull { it.value.size > 1 }
        if (duplicate != null) {
            val adapters = duplicate.value.joinToString { it.adapter.id.value }
            throw DataException(DataProblem(
                DataProblem.adapterConflict,
                "Native class ${duplicate.key.qualifiedName} has duplicate exact adapters: $adapters"))
        }
        exactByClass = exact.associate { it.nativeClass to it.adapter }
    }


    override fun describe(native: KType): DataContract {
        val classifier = native.classifier as? KClass<*>
            ?: return nativeTypeResolver.describe(native).contract

        exactByClass[classifier]?.let { adapter ->
            return adapter.describe(native)
                ?: nativeTypeResolver.describe(native).contract
        }
        if (isRefusedAutomaticType(classifier)) {
            throw refused(classifier)
        }
        if (isBuiltIn(classifier)) {
            return nativeTypeResolver.describe(native).contract
        }
        fallback(classifier)?.let { adapter ->
            return adapter.describe(native)
                ?: nativeTypeResolver.describe(native).contract
        }
        return nativeTypeResolver.describe(native).contract
    }


    override fun lift(value: Any?, expected: DataContract?): DataValue {
        if (value is DataValue) {
            return conformExpected(expected, value)
        }
        if (value == null) {
            val contract = expected ?: DataContract(DataType.Dynamic(true))
            if (!contract.structural.nullable) {
                throw DataException(DataProblem(
                    DataProblem.invalidState,
                    "Null cannot satisfy non-null contract ${contract.structural}"))
            }
            return NativeObjectValueAccess.value(null, contract, this)
        }
        if (expected?.structural is DataType.Opaque) {
            val owner = value::class.java.classLoader
                ?: DefaultDataAdapterRegistry::class.java.classLoader
            val acceptance = nativeTypeResolver.isAssignable(
                nativeTypeResolver.resolve(expected, owner),
                nativeTypeResolver.describe(value::class.starProjectedType))
            if (acceptance is TypeAcceptance.Rejected) {
                throw DataException(acceptance.problem)
            }
            return NativeObjectValueAccess.value(value, expected, this)
        }

        val classifier = value::class
        exactByClass[classifier]?.let { adapter ->
            return validateAdapterResult(adapter, classifier.starProjectedType, value, expected)
        }
        if (isRefusedAutomaticValue(value)) {
            throw refused(classifier)
        }
        if (isBuiltInValue(value)) {
            val contract = runtimeContract(value, expected.takeUnless { it?.structural is DataType.Union })
            val lifted = NativeObjectValueAccess.value(value, contract, this)
            return conformExpected(expected, lifted)
        }
        fallback(classifier)?.let { adapter ->
            return validateAdapterResult(adapter, classifier.starProjectedType, value, expected)
        }

        val contract = nativeTypeResolver.describe(classifier.starProjectedType).contract
        val lifted = NativeObjectValueAccess.value(value, contract, this)
        return conformExpected(expected, lifted)
    }


    internal fun childValue(value: Any?, expected: DataContract?): Pair<Any?, DataContract> {
        if (value == null) {
            val contract = expected ?: DataContract(DataType.Dynamic(true))
            if (!contract.structural.nullable) {
                throw DataAccessException(DataProblem(
                    DataProblem.invalidState,
                    "Null cannot satisfy non-null contract ${contract.structural}"))
            }
            return null to contract
        }
        val lifted = lift(value, expected)
        return value to lifted.contract
    }


    private fun runtimeContract(value: Any, expected: DataContract?): DataContract =
        when (value) {
            is List<*> -> {
                val expectedElement = (expected?.structural as? DataType.Listing)?.let {
                    expected.child(tech.kzen.lib.common.exec.data.type.DataPathSegment.ListingElement)
                }
                val element = joinedRuntimeContract(value, expectedElement)
                val structural = DataType.Listing(element.structural)
                val native = guidedNativeMetadata(expected, structural).toMutableMap()
                native.putIfAbsent(
                    DataTypePath.root,
                    tech.kzen.lib.common.model.structure.metadata.TypeMetadata(
                        tech.kzen.lib.platform.ClassNames.kotlinList,
                        listOf(tech.kzen.lib.common.model.structure.metadata.TypeMetadata.anyNullable),
                        false))
                DataContract(
                    structural,
                    native +
                            requiredNativeMetadata(element).prefixed(DataPathSegment.ListingElement))
            }
            is Map<*, *> -> runtimeMappingContract(value, expected)
            else -> nativeTypeResolver.describe(value::class.starProjectedType).contract
        }


    private fun runtimeMappingContract(value: Map<*, *>, expected: DataContract?): DataContract {
        val expectedMapping = expected?.structural as? DataType.Mapping
        if (value.isEmpty()) {
            return DataContract(expectedMapping ?: DataType.Mapping(
                DataType.Dynamic(false),
                DataType.Dynamic()))
        }
        val keyTypes = value.keys.map { key ->
            if (key == null || !isScalarValue(key)) {
                return nativeTypeResolver.describe(value::class.starProjectedType).contract
            }
            structuralOf(key, null)
        }
        val keyType = keyTypes.reduce(DataTypeAlgebra::join)
        if (keyType !is DataType.Scalar || keyType.nullable) {
            return nativeTypeResolver.describe(value::class.starProjectedType).contract
        }
        val canonicalKeys = mutableSetOf<String>()
        for (key in value.keys) {
            val liftedKey = lift(key)
            val canonical = LiteralDataValues.canonicalText(liftedKey.access.scalar(liftedKey.root))
            if (!canonicalKeys.add(canonical)) {
                throw DataException(DataProblem(
                    DataProblem.mappingKeyCollision,
                    "Native mapping keys collide after canonicalization: $key"))
            }
        }
        val expectedValue = expectedMapping?.let {
            expected.child(tech.kzen.lib.common.exec.data.type.DataPathSegment.MappingValue)
        }
        val valueContract = joinedRuntimeContract(value.values, expectedValue)
        val structural = DataType.Mapping(keyType, valueContract.structural)
        val native = guidedNativeMetadata(expected, structural).toMutableMap()
        native.putIfAbsent(
            DataTypePath.root,
            tech.kzen.lib.common.model.structure.metadata.TypeMetadata(
                tech.kzen.lib.platform.ClassName("kotlin.collections.Map"),
                listOf(
                    tech.kzen.lib.common.model.structure.metadata.TypeMetadata.any,
                    tech.kzen.lib.common.model.structure.metadata.TypeMetadata.anyNullable),
                false))
        return DataContract(
            structural,
            native +
                    requiredNativeMetadata(valueContract).prefixed(DataPathSegment.MappingValue))
    }


    private fun guidedNativeMetadata(
        expected: DataContract?,
        actual: DataType
    ) = expected?.nativeByPath?.mapValues { (path, metadata) ->
        val actualType = typeAt(actual, path)
            ?: throw DataException(DataProblem(
                DataProblem.invalidPath,
                "Expected native metadata path $path is absent from actual type $actual"))
        tech.kzen.lib.common.model.structure.metadata.TypeMetadata(
            metadata.className,
            metadata.generics,
            actualType.nullable)
    } ?: emptyMap()


    private fun typeAt(root: DataType, path: DataTypePath): DataType? {
        var current: DataType? = root
        for (segment in path.segments) {
            current = when (segment) {
                is DataPathSegment.Field ->
                    (current as? DataType.Record)?.fields?.firstOrNull { it.id == segment.id }?.type
                is DataPathSegment.Variant ->
                    (current as? DataType.Union)?.variants?.firstOrNull { it.id == segment.id }?.type
                DataPathSegment.ListingElement -> (current as? DataType.Listing)?.element
                DataPathSegment.MappingKey -> (current as? DataType.Mapping)?.key
                DataPathSegment.MappingValue -> (current as? DataType.Mapping)?.value
                is DataPathSegment.Element,
                is DataPathSegment.Entry -> null
            }
        }
        return current
    }


    private fun requiredNativeMetadata(
        contract: DataContract
    ): Map<DataTypePath, tech.kzen.lib.common.model.structure.metadata.TypeMetadata> =
        contract.nativeByPath.filterKeys { path ->
            typeAt(contract.structural, path) is DataType.Opaque
        }


    private fun joinedRuntimeContract(
        values: Iterable<Any?>,
        expected: DataContract?
    ): DataContract {
        var joined: DataType? = null
        var native: Map<DataTypePath, tech.kzen.lib.common.model.structure.metadata.TypeMetadata>? = null
        for (item in values) {
            val contract = if (item == null) {
                expected ?: DataContract(DataType.Dynamic(nullable = true))
            }
            else {
                lift(item, expected).contract
            }
            joined = joined?.let { DataTypeAlgebra.join(it, contract.structural) } ?: contract.structural
            native = when {
                native == null -> contract.nativeByPath
                native == contract.nativeByPath -> native
                else -> emptyMap()
            }
        }
        return DataContract(
            joined ?: expected?.structural ?: DataType.Dynamic(),
            native ?: expected?.nativeByPath.orEmpty())
    }


    private fun structuralOf(value: Any?, expected: DataContract?): DataType =
        if (value == null) {
            expected?.structural ?: DataType.Dynamic(true)
        }
        else {
            lift(value, expected).type
        }


    private fun validateAdapterResult(
        adapter: DataAdapter,
        native: KType,
        value: Any,
        expected: DataContract?
    ): DataValue {
        val result = adapter.lift(value, expected)
        adapter.describe(native)?.let { described ->
            if (DataTypeAlgebra.isAssignable(described.structural, result.type) is TypeAcceptance.Rejected ||
                DataTypeAlgebra.isAssignable(result.type, described.structural) is TypeAcceptance.Rejected
            ) {
                throw DataException(DataProblem(
                    DataProblem.adapterContractMismatch,
                    "Adapter '${adapter.id}' described ${described.structural} but lifted ${result.type}"))
            }
        }
        return conformExpected(expected, result)
    }


    private fun conformExpected(expected: DataContract?, actual: DataValue): DataValue {
        expected ?: return actual
        if (expected == actual.contract) {
            return actual
        }
        val union = expected.structural as? DataType.Union
        if (union != null && actual.type !is DataType.Union && actual.access.state(actual.root) == DataState.Present) {
            return when (val selection = DataTypeAlgebra.selectVariant(union, actual.type)) {
                is VariantSelection.Selected -> TaggedUnionValueAccess.value(expected, selection.variant, actual)
                is VariantSelection.NoMatch -> throw DataException(selection.problem)
                is VariantSelection.Ambiguous -> throw DataException(DataProblem(
                    DataProblem.unionVariantAmbiguous,
                    "Value ${actual.type} matches multiple expected variants: ${selection.candidates}"))
            }
        }
        val acceptance = DataTypeAlgebra.isAssignable(expected.structural, actual.type)
        if (acceptance is TypeAcceptance.Rejected) {
            throw DataException(acceptance.problem)
        }
        return actual
    }


    private fun fallback(classifier: KClass<*>): DataAdapter? =
        fallbacks.firstOrNull { it.accepts(classifier) }?.adapter


    private fun refused(classifier: KClass<*>): DataException =
        DataException(DataProblem(
            DataProblem.adapterRefused,
            "Automatic adaptation is refused for ${classifier.qualifiedName}; register an explicit adapter"))


    override fun close() {
        nativeTypeResolver.close()
    }
}


private fun isBuiltIn(classifier: KClass<*>): Boolean =
    isScalarClass(classifier) ||
            classifier == List::class ||
            classifier == MutableList::class ||
            classifier.java.isArray ||
            classifier == Map::class ||
            classifier == MutableMap::class ||
            classifier.isData ||
            classifier.java.isRecord


private fun isBuiltInValue(value: Any): Boolean =
    isScalarValue(value) || value is List<*> || value.javaClass.isArray || value is Map<*, *> ||
            value::class.isData || value.javaClass.isRecord


private fun isRefusedAutomaticType(classifier: KClass<*>): Boolean =
    classifier == Set::class || classifier == MutableSet::class ||
            classifier == Sequence::class || classifier == Iterator::class ||
            Iterable::class.java.isAssignableFrom(classifier.java) &&
            !List::class.java.isAssignableFrom(classifier.java)


private fun isRefusedAutomaticValue(value: Any): Boolean =
    value is Set<*> || value is Sequence<*> || value is Iterator<*> ||
            value is Iterable<*> && value !is List<*>


private fun isScalarValue(value: Any): Boolean = isScalarClass(value::class)


private fun isScalarClass(classifier: KClass<*>): Boolean =
    classifier in setOf(
        Boolean::class, Byte::class, Short::class, Int::class, Long::class,
        UByte::class, UShort::class, UInt::class, ULong::class,
        Float::class, Double::class, String::class, ByteArray::class,
        java.math.BigInteger::class, java.math.BigDecimal::class,
            java.time.LocalDate::class, java.time.LocalTime::class,
            java.time.Instant::class, java.time.Duration::class, java.util.UUID::class)


private fun <T> Map<DataTypePath, T>.prefixed(segment: DataPathSegment): Map<DataTypePath, T> =
    mapKeys { (path, _) -> DataTypePath(listOf(segment) + path.segments) }
