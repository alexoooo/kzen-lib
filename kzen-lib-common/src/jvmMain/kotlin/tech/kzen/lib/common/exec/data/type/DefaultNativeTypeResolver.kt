package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.JvmNativeValueAccess
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.IdentityHashMap
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.kotlinFunction


class DefaultNativeTypeResolver(
    describers: List<NativeTypeDescriber> = emptyList()
): NativeTypeResolver {
    private val describerByClass: Map<KClass<*>, NativeTypeDescriber>
    private val descriptionCache = mutableMapOf<KType, ResolvedDataContract>()
    private val resolutionCache = IdentityHashMap<ClassLoader, MutableMap<DataContract, ResolvedDataContract>>()
    private var released = false

    init {
        val duplicates = describers.groupBy { it.nativeClass }.filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) {
            throw DataException(DataProblem(
                DataProblem.invalidResolvedContract,
                "Native type describers must have unique exact classes: ${duplicates.keys}"))
        }
        describerByClass = describers.associateBy { it.nativeClass }
    }

    override fun describe(native: KType): ResolvedDataContract {
        ensureOpen()
        return descriptionCache.getOrPut(native) {
            describe(native, emptySet())
        }
    }

    override fun describe(value: DataValue): ResolvedDataContract {
        ensureOpen()
        if (value.contract.nativeByPath.isEmpty()) {
            return ResolvedDataContract(value.contract, emptyMap())
        }
        val access = value.access as? JvmNativeValueAccess
            ?: throw DataException(DataProblem(
                DataProblem.nativeTypeMissing,
                "Value contract carries native metadata but its backing supplies no JVM native tokens"))
        val tokens = value.contract.nativeByPath.keys.associateWith { path ->
            val node = nodeAt(value, path)
            access.nativeType(node) ?: throw DataException(DataProblem(
                DataProblem.nativeTypeMissing,
                "Value backing supplies no native token at $path",
                path.segments))
        }
        return ResolvedDataContract(value.contract, tokens)
    }

    override fun resolve(
        contract: DataContract,
        owner: ClassLoader
    ): ResolvedDataContract {
        ensureOpen()
        val ownerCache = resolutionCache.getOrPut(owner) { mutableMapOf() }
        return ownerCache.getOrPut(contract) {
            val tokens = contract.nativeByPath.mapValues { (_, metadata) ->
                NativeTypeToken(resolveMetadata(metadata, owner))
            }
            ResolvedDataContract(contract, tokens)
        }
    }

    override fun isAssignable(
        expected: ResolvedDataContract,
        actual: ResolvedDataContract
    ): TypeAcceptance {
        ensureOpen()
        return resolvedAcceptance(expected, actual)
    }

    override fun isAssignable(
        expected: ResolvedDataContract,
        actual: DataValue
    ): TypeAcceptance {
        ensureOpen()
        val structural = DataTypeAlgebra.isAssignable(
            expected.contract.structural,
            actual.contract.structural)
        if (structural is TypeAcceptance.Rejected) {
            return structural
        }
        if (expected.tokenByPath.isEmpty()) {
            return TypeAcceptance.Accepted
        }
        if (actual.access is JvmNativeValueAccess) {
            return resolvedAcceptance(expected, describe(actual))
        }
        if (expected.tokenByPath.keys == setOf(DataTypePath.root) &&
            actual.type is DataType.Scalar
        ) {
            return scalarProjectionAcceptance(expected.tokenByPath.getValue(DataTypePath.root), actual)
        }
        return TypeAcceptance.Rejected(DataProblem(
            DataProblem.nativeTypeMissing,
            "Actual value has no native tokens required by $expected"))
    }

    override fun selectVariant(
        union: ResolvedDataContract,
        actual: ResolvedDataContract
    ): VariantSelection {
        ensureOpen()
        val unionType = union.contract.structural as? DataType.Union
            ?: throw DataException(DataProblem(
                DataProblem.invalidUnion,
                "Resolved variant selection requires a union contract"))
        if (actual.contract.structural is DataType.Dynamic) {
            return noVariant(unionType, actual.contract.structural)
        }

        val candidates = unionType.variants.mapNotNull { variant ->
            val expectedVariant = union.child(DataPathSegment.Variant(variant.id))
            if (resolvedAcceptance(expectedVariant, actual) == TypeAcceptance.Accepted) {
                variant.id
            }
            else {
                null
            }
        }
        return when (candidates.size) {
            0 -> noVariant(unionType, actual.contract.structural)
            1 -> VariantSelection.Selected(candidates.single())
            else -> VariantSelection.Ambiguous(candidates)
        }
    }

    override fun validateVariant(
        union: ResolvedDataContract,
        variant: VariantId,
        actual: ResolvedDataContract
    ): TypeAcceptance {
        ensureOpen()
        val unionType = union.contract.structural as? DataType.Union
            ?: throw DataException(DataProblem(
                DataProblem.invalidUnion,
                "Resolved variant validation requires a union contract"))
        if (unionType.variants.none { it.id == variant }) {
            return TypeAcceptance.Rejected(DataProblem(
                DataProblem.unionVariantUnknown,
                "Union has no variant '$variant'"))
        }
        return resolvedAcceptance(union.child(DataPathSegment.Variant(variant)), actual)
    }

    override fun close() {
        descriptionCache.clear()
        resolutionCache.clear()
        released = true
    }

    private fun describe(
        native: KType,
        openClasses: Set<KClass<*>>
    ): ResolvedDataContract {
        val classifier = native.classifier as? KClass<*>
            ?: return unresolvedKType(native)

        val custom = describerByClass[classifier]?.describe(native)
        if (custom != null) {
            return resolveCustom(native, classifier, custom)
        }

        scalarType(classifier)?.let { scalar ->
            return describedNode(
                DataType.Scalar(scalar, native.isMarkedNullable),
                native,
                emptyList())
        }

        primitiveArrayElement(classifier)?.let { element ->
            return describedNode(
                DataType.Listing(DataType.Scalar(element), native.isMarkedNullable),
                native,
                emptyList())
        }

        if (isListOrArray(classifier)) {
            val element = native.arguments.singleOrNull()?.type
                ?.let { describe(it, openClasses) }
                ?: dynamicResolved()
            return describedNode(
                DataType.Listing(element.contract.structural, native.isMarkedNullable),
                native,
                listOf(DataPathSegment.ListingElement to element))
        }

        if (isMap(classifier)) {
            val key = native.arguments.getOrNull(0)?.type
                ?.let { describe(it, openClasses) }
                ?: dynamicResolved(nullable = false)
            val value = native.arguments.getOrNull(1)?.type
                ?.let { describe(it, openClasses) }
                ?: dynamicResolved()
            val keyType = key.contract.structural
            if (keyType !is DataType.Scalar || keyType.nullable) {
                return opaque(native)
            }
            return describedNode(
                DataType.Mapping(keyType, value.contract.structural, native.isMarkedNullable),
                native,
                listOf(DataPathSegment.MappingValue to value))
        }

        if (classifier in openClasses) {
            return opaque(native)
        }
        if (classifier.isData) {
            return describeDataClass(native, classifier, openClasses + classifier)
        }
        if (classifier.java.isRecord) {
            return describeJavaRecord(native, classifier, openClasses + classifier)
        }
        return opaque(native)
    }

    private fun describeDataClass(
        native: KType,
        classifier: KClass<*>,
        openClasses: Set<KClass<*>>
    ): ResolvedDataContract {
        val propertyByName = classifier.memberProperties.associateBy { it.name }
        val parameters = classifier.primaryConstructor?.parameters.orEmpty()
        val children = parameters.map { parameter ->
            val name = parameter.name
                ?: throw DataException(DataProblem(
                    DataProblem.nativeTypeUnresolved,
                    "Data-class constructor parameter has no name: $native"))
            val property = propertyByName[name]
                ?: throw DataException(DataProblem(
                    DataProblem.nativeTypeUnresolved,
                    "Data-class property '$name' not found on $native"))
            val child = describe(property.returnType, openClasses)
            DataField(FieldId(name), child.contract.structural) to child
        }
        return describedNode(
            DataType.Record(children.map { it.first }, native.isMarkedNullable),
            native,
            children.map { (field, child) -> DataPathSegment.Field(field.id) to child })
    }

    private fun describeJavaRecord(
        native: KType,
        classifier: KClass<*>,
        openClasses: Set<KClass<*>>
    ): ResolvedDataContract {
        val children = classifier.java.recordComponents.map { component ->
            val returnType = component.accessor.kotlinFunction?.returnType
                ?: component.type.kotlin.createType()
            val child = describe(returnType, openClasses)
            DataField(FieldId(component.name), child.contract.structural) to child
        }
        return describedNode(
            DataType.Record(children.map { it.first }, native.isMarkedNullable),
            native,
            children.map { (field, child) -> DataPathSegment.Field(field.id) to child })
    }

    private fun resolveCustom(
        native: KType,
        classifier: KClass<*>,
        described: DataContract
    ): ResolvedDataContract {
        val metadata = described.nativeByPath.toMutableMap()
        if (described.structural !is DataType.Scalar &&
            described.structural !is DataType.Dynamic &&
            DataTypePath.root !in metadata
        ) {
            metadata[DataTypePath.root] = native.toMetadata()
        }
        val contract = DataContract(described.structural, metadata)
        val owner = classifier.java.classLoader ?: DefaultNativeTypeResolver::class.java.classLoader
        val resolved = resolve(contract, owner)
        if (DataTypePath.root !in resolved.tokenByPath) {
            return resolved
        }
        return ResolvedDataContract(
            contract,
            resolved.tokenByPath + (DataTypePath.root to NativeTypeToken(native)))
    }

    private fun describedNode(
        structural: DataType,
        native: KType,
        children: List<Pair<DataPathSegment, ResolvedDataContract>>
    ): ResolvedDataContract {
        val metadata = mutableMapOf(DataTypePath.root to native.toMetadata())
        val tokens = mutableMapOf(DataTypePath.root to NativeTypeToken(native))
        for ((segment, child) in children) {
            metadata.putAll(child.contract.nativeByPath.prefixed(segment))
            tokens.putAll(child.tokenByPath.prefixed(segment))
        }
        return ResolvedDataContract(DataContract(structural, metadata), tokens)
    }

    private fun opaque(native: KType): ResolvedDataContract =
        describedNode(DataType.Opaque(native.isMarkedNullable), native, emptyList())

    private fun unresolvedKType(native: KType): ResolvedDataContract =
        describedNode(DataType.Opaque(native.isMarkedNullable), native, emptyList())

    private fun dynamicResolved(nullable: Boolean = true): ResolvedDataContract =
        ResolvedDataContract(DataContract(DataType.Dynamic(nullable)), emptyMap())

    private fun resolvedAcceptance(
        expected: ResolvedDataContract,
        actual: ResolvedDataContract
    ): TypeAcceptance {
        val expectedType = expected.contract.structural
        val actualType = actual.contract.structural

        if (expectedType is DataType.Union) {
            if (actualType is DataType.Union) {
                for (actualVariant in actualType.variants) {
                    val actualChild = actual.child(DataPathSegment.Variant(actualVariant.id))
                    val accepted = expectedType.variants.any { expectedVariant ->
                        resolvedAcceptance(
                            expected.child(DataPathSegment.Variant(expectedVariant.id)),
                            actualChild) == TypeAcceptance.Accepted
                    }
                    if (!accepted) {
                        return rejected(expectedType, actualType)
                    }
                }
                return TypeAcceptance.Accepted
            }
            val accepted = expectedType.variants.any { variant ->
                resolvedAcceptance(
                    expected.child(DataPathSegment.Variant(variant.id)),
                    actual) == TypeAcceptance.Accepted
            }
            return if (accepted) TypeAcceptance.Accepted else rejected(expectedType, actualType)
        }
        if (actualType is DataType.Union) {
            val accepted = actualType.variants.all { variant ->
                resolvedAcceptance(
                    expected,
                    actual.child(DataPathSegment.Variant(variant.id))) == TypeAcceptance.Accepted
            }
            return if (accepted) TypeAcceptance.Accepted else rejected(expectedType, actualType)
        }

        val structuralExpected = replaceOpaque(expectedType, actualType)
            ?: return rejected(expectedType, actualType)
        if (DataTypeAlgebra.isAssignable(structuralExpected, actualType) != TypeAcceptance.Accepted) {
            return rejected(expectedType, actualType)
        }

        for ((path, expectedToken) in expected.tokenByPath) {
            val actualToken = actual.tokenByPath[path]
            if (actualToken == null) {
                if (nativeRequirementCoveredByAncestor(path, expected, actual)) {
                    continue
                }
                return TypeAcceptance.Rejected(DataProblem(
                    DataProblem.nativeTypeMissing,
                    "Actual contract has no native type at required path $path",
                    path.segments))
            }
            if (!nativeAssignable(expectedToken, actualToken)) {
                return TypeAcceptance.Rejected(DataProblem(
                    DataProblem.nativeTypeIncompatible,
                    "Native type ${actualToken.type} is not assignable to ${expectedToken.type} at $path",
                    path.segments))
            }
        }
        return TypeAcceptance.Accepted
    }

    private fun replaceOpaque(expected: DataType, actual: DataType): DataType? {
        if (expected is DataType.Opaque) {
            if (!expected.nullable && actual.nullable) {
                return null
            }
            return DataType.Dynamic(expected.nullable)
        }
        if (expected is DataType.Record && actual is DataType.Record) {
            val fields = expected.fields.map { expectedField ->
                val actualField = actual.fields.firstOrNull { it.id == expectedField.id }
                    ?: return null
                val fieldType = replaceOpaque(expectedField.type, actualField.type) ?: return null
                expectedField.copy(type = fieldType)
            }
            return DataType.Record(fields, expected.nullable)
        }
        if (expected is DataType.Listing && actual is DataType.Listing) {
            return DataType.Listing(
                replaceOpaque(expected.element, actual.element) ?: return null,
                expected.nullable)
        }
        if (expected is DataType.Mapping && actual is DataType.Mapping) {
            return DataType.Mapping(
                expected.key,
                replaceOpaque(expected.value, actual.value) ?: return null,
                expected.nullable)
        }
        return expected
    }

    private fun nativeRequirementCoveredByAncestor(
        path: DataTypePath,
        expected: ResolvedDataContract,
        actual: ResolvedDataContract
    ): Boolean {
        for (size in path.segments.size - 1 downTo 0) {
            val ancestor = DataTypePath(path.segments.take(size))
            val expectedAncestor = expected.tokenByPath[ancestor] ?: continue
            val actualAncestor = actual.tokenByPath[ancestor] ?: continue
            if (nativeAssignable(expectedAncestor, actualAncestor)) {
                return true
            }
        }
        return false
    }

    private fun nativeAssignable(
        expected: NativeTypeToken,
        actual: NativeTypeToken
    ): Boolean {
        val expectedClass = expected.type.classifier as? KClass<*> ?: return false
        val actualClass = actual.type.classifier as? KClass<*> ?: return false
        if (!expectedClass.java.isAssignableFrom(actualClass.java)) {
            return false
        }
        return try {
            actual.type.isSubtypeOf(expected.type)
        }
        catch (_: IllegalArgumentException) {
            false
        }
        catch (_: ClassCastException) {
            // Kotlin reflection can expose ErrorTypeParameter for Sequence/Iterator supertypes. The raw JVM
            // class check above remains loader-aware; fall back to it only for that reflection defect.
            true
        }
    }

    private fun resolveMetadata(metadata: TypeMetadata, owner: ClassLoader): KType {
        val classifier = builtInClass(metadata.className.asString())
            ?: loadClass(metadata.className.asString(), owner).kotlin
        val arguments = metadata.generics.map { generic ->
            KTypeProjection.invariant(resolveMetadata(generic, owner))
        }
        return try {
            classifier.createType(arguments, metadata.nullable)
        }
        catch (e: IllegalArgumentException) {
            throw DataException(DataProblem(
                DataProblem.nativeTypeUnresolved,
                "Unable to construct native type from $metadata: ${e.message}"))
        }
    }

    private fun loadClass(name: String, owner: ClassLoader): Class<*> {
        var candidate = name
        while (true) {
            try {
                return Class.forName(candidate, false, owner)
            }
            catch (_: ClassNotFoundException) {
                val separator = candidate.lastIndexOf('.')
                if (separator < 0) {
                    throw DataException(DataProblem(
                        DataProblem.nativeTypeUnresolved,
                        "Unable to resolve native type '$name' in owner loader $owner"))
                }
                candidate = candidate.substring(0, separator) + "$" + candidate.substring(separator + 1)
            }
        }
    }

    private fun nodeAt(value: DataValue, path: DataTypePath): DataNode {
        var node = value.root
        for (segment in path.segments) {
            node = when (segment) {
                is DataPathSegment.Field -> value.access.field(node, segment.id)
                is DataPathSegment.Variant -> {
                    if (value.access.activeVariant(node) != segment.id) {
                        throw DataException(DataProblem(
                            DataProblem.nativeTypeMissing,
                            "Inactive union variant has no runtime token at $path",
                            path.segments))
                    }
                    value.access.selected(node)
                }
                DataPathSegment.ListingElement -> {
                    if (value.access.size(node) == 0) {
                        throw DataException(DataProblem(
                            DataProblem.nativeTypeMissing,
                            "Empty listing has no runtime element token at $path",
                            path.segments))
                    }
                    value.access.element(node, 0)
                }
                DataPathSegment.MappingValue -> {
                    if (value.access.size(node) == 0) {
                        throw DataException(DataProblem(
                            DataProblem.nativeTypeMissing,
                            "Empty mapping has no runtime value token at $path",
                            path.segments))
                    }
                    value.access.entry(node, value.access.keyAt(node, 0))
                }
                DataPathSegment.MappingKey,
                is DataPathSegment.Entry,
                is DataPathSegment.Element -> throw DataException(DataProblem(
                    DataProblem.invalidPath,
                    "Runtime-only path segment is not valid in native metadata: $segment",
                    path.segments))
            }
        }
        return node
    }

    private fun scalarProjectionAcceptance(
        expected: NativeTypeToken,
        actual: DataValue
    ): TypeAcceptance {
        if (actual.access.state(actual.root) == DataState.Null && expected.type.isMarkedNullable) {
            return TypeAcceptance.Accepted
        }
        return try {
            when (expected.type.classifier as? KClass<*>) {
                Boolean::class -> actual.access.readBoolean(actual.root)
                Byte::class -> actual.access.readLong(actual.root).also {
                    if (it !in Byte.MIN_VALUE..Byte.MAX_VALUE) throw ArithmeticException("Byte overflow: $it")
                }.toByte()
                Short::class -> actual.access.readLong(actual.root).also {
                    if (it !in Short.MIN_VALUE..Short.MAX_VALUE) throw ArithmeticException("Short overflow: $it")
                }.toShort()
                Int::class -> actual.access.readLong(actual.root).also {
                    if (it !in Int.MIN_VALUE..Int.MAX_VALUE) throw ArithmeticException("Int overflow: $it")
                }.toInt()
                Long::class -> actual.access.readLong(actual.root)
                Float::class -> actual.access.readDouble(actual.root).also {
                    val projected = it.toFloat()
                    if (!projected.isFinite() || projected.toDouble() != it) {
                        throw ArithmeticException("Float precision loss: $it")
                    }
                }.toFloat()
                Double::class -> actual.access.readDouble(actual.root)
                String::class -> actual.access.readText(actual.root)
                ByteArray::class -> actual.access.readBinary(actual.root)
                else -> return TypeAcceptance.Rejected(DataProblem(
                    DataProblem.nativeTypeMissing,
                    "No exact canonical scalar projection to ${expected.type}"))
            }
            TypeAcceptance.Accepted
        }
        catch (e: DataAccessException) {
            TypeAcceptance.Rejected(e.problem)
        }
        catch (e: ArithmeticException) {
            TypeAcceptance.Rejected(DataProblem(
                DataProblem.nativeTypeIncompatible,
                e.message ?: "Scalar projection is not exact"))
        }
    }

    private fun ensureOpen() {
        if (released) {
            throw DataException(DataProblem(
                DataProblem.nativeResolverReleased,
                "Native type resolver has been released"))
        }
    }

    private fun rejected(expected: DataType, actual: DataType): TypeAcceptance.Rejected =
        TypeAcceptance.Rejected(DataProblem(
            DataProblem.incompatibleType,
            "Actual type $actual is not assignable to expected type $expected"))

    private fun noVariant(union: DataType.Union, actual: DataType): VariantSelection.NoMatch =
        VariantSelection.NoMatch(DataProblem(
            DataProblem.unionVariantNoMatch,
            "No native union variant in $union accepts actual type $actual"))
}


private fun KType.toMetadata(): TypeMetadata {
    val classifier = classifier as? KClass<*>
        ?: return TypeMetadata.anyNullable
    val className = classifier.qualifiedName ?: classifier.java.name
    return TypeMetadata(
        ClassName(className),
        arguments.map { argument -> argument.type?.toMetadata() ?: TypeMetadata.anyNullable },
        isMarkedNullable)
}


private fun <T> Map<DataTypePath, T>.prefixed(segment: DataPathSegment): Map<DataTypePath, T> =
    mapKeys { (path, _) -> DataTypePath(listOf(segment) + path.segments) }


private fun isListOrArray(classifier: KClass<*>): Boolean =
    classifier == List::class ||
            classifier == MutableList::class ||
            classifier.java.isArray && !classifier.java.componentType.isPrimitive


private fun isMap(classifier: KClass<*>): Boolean =
    classifier == Map::class || classifier == MutableMap::class


private fun scalarType(classifier: KClass<*>): ScalarKind? =
    when (classifier) {
        Boolean::class -> ScalarKind.Boolean
        Byte::class -> ScalarKind.Integer(8)
        Short::class -> ScalarKind.Integer(16)
        Int::class -> ScalarKind.Integer(32)
        Long::class -> ScalarKind.Integer(64)
        UByte::class -> ScalarKind.Integer(8, signed = false)
        UShort::class -> ScalarKind.Integer(16, signed = false)
        UInt::class -> ScalarKind.Integer(32, signed = false)
        ULong::class -> ScalarKind.Integer(64, signed = false)
        BigInteger::class -> ScalarKind.Integer()
        BigDecimal::class -> ScalarKind.Decimal
        Float::class -> ScalarKind.Floating(32)
        Double::class -> ScalarKind.Floating(64)
        String::class -> ScalarKind.Text
        ByteArray::class -> ScalarKind.Binary
        LocalDate::class -> ScalarKind.Date
        LocalTime::class -> ScalarKind.Time
        Instant::class -> ScalarKind.Instant
        Duration::class -> ScalarKind.Duration
        UUID::class -> ScalarKind.Uuid
        else -> null
    }


private fun primitiveArrayElement(classifier: KClass<*>): ScalarKind? =
    when (classifier) {
        BooleanArray::class -> ScalarKind.Boolean
        ShortArray::class -> ScalarKind.Integer(16)
        IntArray::class -> ScalarKind.Integer(32)
        LongArray::class -> ScalarKind.Integer(64)
        FloatArray::class -> ScalarKind.Floating(32)
        DoubleArray::class -> ScalarKind.Floating(64)
        else -> null
    }


private fun builtInClass(name: String): KClass<*>? =
    when (name) {
        "kotlin.Any" -> Any::class
        "kotlin.Boolean" -> Boolean::class
        "kotlin.Byte" -> Byte::class
        "kotlin.Short" -> Short::class
        "kotlin.Int" -> Int::class
        "kotlin.Long" -> Long::class
        "kotlin.UByte" -> UByte::class
        "kotlin.UShort" -> UShort::class
        "kotlin.UInt" -> UInt::class
        "kotlin.ULong" -> ULong::class
        "kotlin.Float" -> Float::class
        "kotlin.Double" -> Double::class
        "kotlin.String" -> String::class
        "kotlin.ByteArray" -> ByteArray::class
        "kotlin.Array" -> Array<Any>::class
        "kotlin.collections.List",
        "kotlin.collections.MutableList" -> List::class
        "kotlin.collections.Collection",
        "kotlin.collections.MutableCollection" -> Collection::class
        "kotlin.collections.Iterable" -> Iterable::class
        "kotlin.collections.Iterator" -> Iterator::class
        "kotlin.sequences.Sequence" -> Sequence::class
        "kotlin.collections.Map",
        "kotlin.collections.MutableMap" -> Map::class
        "java.math.BigInteger" -> BigInteger::class
        "java.math.BigDecimal" -> BigDecimal::class
        "java.time.LocalDate" -> LocalDate::class
        "java.time.LocalTime" -> LocalTime::class
        "java.time.Instant" -> Instant::class
        "java.time.Duration" -> Duration::class
        "java.util.UUID" -> UUID::class
        else -> null
    }
