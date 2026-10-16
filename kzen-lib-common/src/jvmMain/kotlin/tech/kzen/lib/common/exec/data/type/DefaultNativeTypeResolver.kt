package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.value.BeanShape
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.DataValueAlgebra
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.JvmNativeValueAccess
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.kotlinFunction


/**
 * Safe for concurrent use: a process-wide registry's resolver serves every Worker thread. A cache miss computes
 * outside any lock and stores with `putIfAbsent`, so a describer or class loader that calls back into this resolver
 * can neither deadlock nor trip `computeIfAbsent`'s recursive-update check; threads missing at once may each
 * compute, but all of them return the first result stored.
 *
 * A cache entry never keeps a class loader alive. An entry whose key names only classes of a lasting loader (this
 * class's own loader, its parents, the bootstrap) lives in this resolver's maps, which [close] clears. Any other
 * entry lives in a [ClassValue] of its anchor: the class its key names from the youngest other loader (see
 * [anchor]), or for a resolution the owner class. Everything an entry holds was reached through the anchor's loader
 * or that loader's parents, which the anchor keeps alive anyway, so the entry is collected with the anchor's loader —
 * a compiled expression's loader stays releasable while plugin and application classes still hit the cache.
 */
class DefaultNativeTypeResolver(
    describers: List<NativeTypeDescriber> = emptyList()
): NativeTypeResolver {
    private val describerByClass: Map<KClass<*>, NativeTypeDescriber>

    private val ownLoader: ClassLoader = DefaultNativeTypeResolver::class.java.classLoader
    private val lastingLoaders: Array<ClassLoader> = generateSequence(ownLoader) { it.parent }.toList().toTypedArray()

    private val lastingDescriptions = ConcurrentHashMap<KType, ResolvedDataContract>()
    private val anchoredDescriptions = cacheByClass<KType>()

    // Positional with lastingLoaders
    private val lastingResolutions = Array(lastingLoaders.size) {
        ConcurrentHashMap<DataContract, ResolvedDataContract>()
    }
    private val anchoredResolutions = cacheByClass<DataContract>()

    @Volatile
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
        val cache = when (val anchor = anchor(native, null)) {
            null -> lastingDescriptions
            is Class<*> -> anchoredDescriptions.get(anchor)
            else -> null
        }
        cache?.get(native)?.let { return it }
        val session = DescribeSession()
        val root = describe(native, emptySet(), session)
        defineReferenced(session)
        val described =
            if (session.definitions.isEmpty()) {
                root
            }
            else {
                ResolvedDataContract(
                    DataContract(
                        root.contract.structural, root.contract.nativeByPath,
                        session.definitions, session.definitionNatives, root.contract.constraintsByPath),
                    root.tokenByPath)
            }
        // Not computeIfAbsent (see the class comment)
        return cache?.putIfAbsent(native, described) ?: described
    }


    /**
     * Where an entry keyed by [native] lives: null for [lastingDescriptions] when [native] names only classes of
     * lasting loaders; otherwise the class it names from the youngest other loader, every other such class coming
     * from that loader or one of its parents. [Unanchored] (not cached) when two such classes come from loaders on
     * different parent chains, so that neither is known to keep the other alive, or when [native] names a type
     * parameter. [youngest] is the anchor of the types already visited.
     */
    private fun anchor(native: KType, youngest: Any?): Any? {
        val classifier = native.classifier as? KClass<*>
            ?: return Unanchored
        var anchor = younger(youngest, classifier.java)
        val arguments = native.arguments
        for (i in arguments.indices) {
            if (anchor === Unanchored) {
                break
            }
            val argument = arguments[i].type ?: continue
            anchor = anchor(argument, anchor)
        }
        return anchor
    }


    private fun younger(youngest: Any?, candidate: Class<*>): Any? {
        val loader = candidate.classLoader
        if (youngest === Unanchored || lastingIndex(loader) >= 0) {
            return youngest
        }
        if (youngest == null) {
            return candidate
        }
        val current = (youngest as Class<*>).classLoader
        return when {
            current === loader || isParent(loader, current) -> youngest
            isParent(current, loader) -> candidate
            else -> Unanchored
        }
    }


    /** Whether [ancestor] is on [loader]'s parent chain, so that [loader] keeps it alive. */
    private fun isParent(ancestor: ClassLoader, loader: ClassLoader): Boolean {
        var parent = loader.parent
        while (parent != null) {
            if (parent === ancestor) {
                return true
            }
            parent = parent.parent
        }
        return false
    }


    /** [loader]'s position in [lastingLoaders], 0 (this class's own) for the bootstrap, or -1 when not lasting. */
    private fun lastingIndex(loader: ClassLoader?): Int {
        if (loader == null) {
            return 0
        }
        for (i in lastingLoaders.indices) {
            if (lastingLoaders[i] === loader) {
                return i
            }
        }
        return -1
    }


    private object Unanchored


    /**
     * One top-level description: the classes a recursive occurrence referenced, and each such class's
     * definition — the root contract carries them (E7 item 5). A definition is its class's own description,
     * unrolled from that class alone, so it is the same whichever root reaches it and any two contracts carrying
     * it agree. An unrolling inside another class's description is not: it stops early at every class open
     * around it, so only one with no class open around it is recorded ([defineReferenced] describes the rest).
     */
    private class DescribeSession {
        val referenced = mutableSetOf<KClass<*>>()
        val definitions = mutableMapOf<DefinitionId, DataType>()
        // Members' metadata per definition, relative to the definition's root (the root entry is the occurrence's)
        val definitionNatives = mutableMapOf<DefinitionId, Map<DataTypePath, TypeMetadata>>()
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
            heldNode(value, path)?.let(access::nativeType) ?: throw DataException(DataProblem(
                DataProblem.nativeTypeMissing,
                "Value holds no native object at $path",
                path.segments))
        }
        return ResolvedDataContract(value.contract, tokens)
    }

    override fun resolve(
        contract: DataContract,
        owner: Class<*>
    ): ResolvedDataContract {
        ensureOpen()
        val loader = owner.classLoader ?: ownLoader
        val lasting = lastingIndex(loader)
        val ownerCache =
            if (lasting >= 0) lastingResolutions[lasting]
            else anchoredResolutions.get(owner)
        ownerCache[contract]?.let { return it }
        // Not computeIfAbsent (see the class comment)
        val tokens = contract.nativeByPath.mapValues { (_, metadata) ->
            NativeTypeToken(resolveMetadata(metadata, loader))
        }
        val resolved = ResolvedDataContract(contract, tokens)
        return ownerCache.putIfAbsent(contract, resolved) ?: resolved
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
        val structural = DataValueAlgebra.isAssignable(expected.contract, actual)
        if (structural is TypeAcceptance.Rejected) {
            return structural
        }
        if (expected.tokenByPath.isEmpty()) {
            return TypeAcceptance.Accepted
        }
        if (actual.access is JvmNativeValueAccess) {
            return heldAcceptance(expected, actual, actual.access)
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

    // Anchored entries go with their classes' loaders
    override fun close() {
        released = true
        lastingDescriptions.clear()
        for (resolutions in lastingResolutions) {
            resolutions.clear()
        }
    }

    private fun describe(
        native: KType,
        openClasses: Set<KClass<*>>,
        session: DescribeSession
    ): ResolvedDataContract {
        val classifier = native.classifier as? KClass<*>
            ?: return unresolvedKType(native)

        // `Any` is the dynamic contract, as the transport already treats it (TypeMetadata.toDataContract)
        if (classifier == Any::class) {
            return dynamicResolved(native.isMarkedNullable)
        }

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

        // An enum is its constant name (E7 item 2), one of its constants in declaration order; a constant with a
        // body is an anonymous subclass. Behind a recursive reference the set is dropped, as definitions carry none.
        if (isEnum(classifier)) {
            return describedNode(
                DataType.Scalar(ScalarKind.Text, native.isMarkedNullable),
                native,
                emptyList(),
                mapOf(DataTypePath.root to listOf(DataConstraint.SymbolSet(enumConstantNames(classifier)))))
        }

        primitiveArrayElement(classifier)?.let { element ->
            return describedNode(
                DataType.Listing(DataType.Scalar(element), native.isMarkedNullable),
                native,
                emptyList())
        }

        // A Set is an unordered Listing (iteration order, unstable); Sequence and Iterator stay streaming-only
        if (isListOrArray(classifier) || isSet(classifier)) {
            val element = native.arguments.singleOrNull()?.type
                ?.let { describe(it, openClasses, session) }
                ?: dynamicResolved()
            return describedNode(
                DataType.Listing(element.contract.structural, native.isMarkedNullable),
                native,
                listOf(DataPathSegment.ListingElement to element))
        }

        if (isMap(classifier)) {
            val key = native.arguments.getOrNull(0)?.type
                ?.let { describe(it, openClasses, session) }
                ?: dynamicResolved(nullable = false)
            val value = native.arguments.getOrNull(1)?.type
                ?.let { describe(it, openClasses, session) }
                ?: dynamicResolved()
            val keyType = key.contract.structural
            if (keyType !is DataType.Scalar || keyType.nullable) {
                return opaque(native)
            }
            // A key carries no native metadata, but keeps its constraints (an enum-keyed map's symbols)
            return describedNode(
                DataType.Mapping(keyType, value.contract.structural, native.isMarkedNullable),
                native,
                listOf(DataPathSegment.MappingValue to value),
                key.contract.constraintsByPath.prefixed(DataPathSegment.MappingKey))
        }

        if (classifier in openClasses) {
            // A recursive occurrence: a finite named reference, defined once the enclosing description closes
            session.referenced += classifier
            return describedNode(
                DataType.Reference(definitionId(classifier), native.isMarkedNullable),
                native,
                emptyList())
        }
        val described = when {
            classifier.isData -> describeDataClass(native, classifier, openClasses + classifier, session)
            classifier.java.isRecord -> describeJavaRecord(native, classifier, openClasses + classifier, session)
            BeanShape.isCandidate(classifier.java) -> BeanShape.of(classifier.java)?.let { shape ->
                describeBean(native, shape, openClasses + classifier, session)
            }
            else -> null
        } ?: return opaque(native)
        // Only the class's own unrolling defines it (see DescribeSession)
        if (openClasses.isEmpty() && classifier in session.referenced) {
            val id = definitionId(classifier)
            session.definitions.putIfAbsent(id, described.contract.structural.withNullability(false))
            session.definitionNatives.putIfAbsent(
                id, described.contract.nativeByPath.filterKeys { it != DataTypePath.root })
        }
        return described
    }


    /**
     * Describes each referenced class that has no definition yet from itself, until every reference (including
     * those the new definitions make) is defined. A definition depends only on its class's declared members,
     * never on the type arguments it was reached with, so any type of the class will do.
     */
    private fun defineReferenced(session: DescribeSession) {
        while (true) {
            val undefined = session.referenced.firstOrNull { definitionId(it) !in session.definitions }
                ?: return
            describe(undefined.starProjectedType, emptySet(), session)
            check(definitionId(undefined) in session.definitions) {
                "Recursive class ${undefined.qualifiedName} does not recur in its own description"
            }
        }
    }


    private fun definitionId(classifier: KClass<*>): DefinitionId =
        DefinitionId(classifier.qualifiedName ?: classifier.java.name)


    /** An ordinary class by the fixed convention ([BeanShape]): lexical property order, per-property nullability. */
    private fun describeBean(
        native: KType,
        shape: BeanShape,
        openClasses: Set<KClass<*>>,
        session: DescribeSession
    ): ResolvedDataContract {
        val children = shape.properties.map { property ->
            val child = describe(property.type, openClasses, session)
            DataField(FieldId(property.name), child.contract.structural) to child
        }
        return describedNode(
            DataType.Record(children.map { it.first }, native.isMarkedNullable),
            native,
            children.map { (field, child) -> DataPathSegment.Field(field.id) to child })
    }

    private fun describeDataClass(
        native: KType,
        classifier: KClass<*>,
        openClasses: Set<KClass<*>>,
        session: DescribeSession
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
            val child = describe(property.returnType, openClasses, session)
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
        openClasses: Set<KClass<*>>,
        session: DescribeSession
    ): ResolvedDataContract {
        val children = classifier.java.recordComponents.map { component ->
            val returnType = component.accessor.kotlinFunction?.returnType
                ?: component.type.kotlin.createType()
            val child = describe(returnType, openClasses, session)
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
        val contract = DataContract(described.structural, metadata, constraintsByPath = described.constraintsByPath)
        val resolved = resolve(contract, classifier.java)
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
        children: List<Pair<DataPathSegment, ResolvedDataContract>>,
        constraints: Map<DataTypePath, List<DataConstraint>> = emptyMap()
    ): ResolvedDataContract {
        val metadata = mutableMapOf(DataTypePath.root to native.toMetadata())
        val tokens = mutableMapOf(DataTypePath.root to NativeTypeToken(native))
        val constraintsByPath = constraints.toMutableMap()
        for ((segment, child) in children) {
            metadata.putAll(child.contract.nativeByPath.prefixed(segment))
            tokens.putAll(child.tokenByPath.prefixed(segment))
            constraintsByPath.putAll(child.contract.constraintsByPath.prefixed(segment))
        }
        return ResolvedDataContract(
            DataContract(structural, metadata, constraintsByPath = constraintsByPath), tokens)
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
        // A definition reached through a reference gets the same opaque-for-opaque relaxation as the root: the
        // native identity behind an opaque member is checked by token, not by structure
        val definitionsExpected = expected.contract.definitions.mapValues { (id, type) ->
            actual.contract.definitions[id]?.let { replaceOpaque(type, it) } ?: type
        }
        val structuralAcceptance = DataTypeAlgebra.isAssignable(
            DataContract(
                structuralExpected,
                definitions = definitionsExpected,
                definitionNatives = expected.contract.definitionNatives),
            actual.contract)
        if (structuralAcceptance != TypeAcceptance.Accepted) {
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
            if (!nativeAssignable(expectedToken.type, actualToken.type) &&
                    !(isStarProjected(actualToken.type) &&
                            erasedCollectionAssignable(expected, path, expectedToken, actualToken))
            ) {
                return incompatibleToken(expectedToken, actualToken, path)
            }
        }
        return TypeAcceptance.Accepted
    }

    /**
     * [expected]'s tokens against the objects [actual] holds at their paths, whatever native metadata [actual]'s
     * own contract carries: a held collection's class is erased (`ArrayList<*>`), so its type arguments are shown
     * by what it holds. Each collection is sampled at its first element or entry; nothing is held to check below a
     * null, in an empty collection, or in an inactive variant.
     */
    private fun heldAcceptance(
        expected: ResolvedDataContract,
        actual: DataValue,
        access: JvmNativeValueAccess
    ): TypeAcceptance {
        val expectedType = expected.contract.structural
        if (expectedType is DataType.Union && actual.type !is DataType.Union) {
            val accepted = expectedType.variants.any { variant ->
                val expectedVariant = expected.child(DataPathSegment.Variant(variant.id))
                DataValueAlgebra.isAssignable(expectedVariant.contract, actual) == TypeAcceptance.Accepted &&
                        heldAcceptance(expectedVariant, actual, access) == TypeAcceptance.Accepted
            }
            return if (accepted) TypeAcceptance.Accepted else rejected(expectedType, actual.type)
        }

        for ((path, expectedToken) in expected.tokenByPath) {
            val node =
                try {
                    heldNode(actual, path)
                }
                catch (e: DataAccessException) {
                    // An object that does not conform to its declared contract (a polluted element, a null Java
                    // record component) fails as its parent lifts it
                    return TypeAcceptance.Rejected(e.problem)
                }
                ?: continue
            val actualToken = access.nativeType(node)
            if (actualToken == null) {
                if (access.state(node) == DataState.Null && expectedToken.type.isMarkedNullable) {
                    continue
                }
                return TypeAcceptance.Rejected(DataProblem(
                    DataProblem.nativeTypeMissing,
                    "Actual value holds no native object at $path",
                    path.segments))
            }
            if (!nativeAssignable(expectedToken.type, actualToken.type) &&
                    !(erasedCollectionAssignable(expected, path, expectedToken, actualToken) &&
                            (isStarProjected(actualToken.type) || access.size(node) == 0))
            ) {
                return incompatibleToken(expectedToken, actualToken, path)
            }
        }
        return TypeAcceptance.Accepted
    }

    /**
     * Whether [actual]'s class is assignable to the class of [expected], a built-in collection at a collection
     * node of [expected]'s contract. For a run-time collection, whose type arguments are erased or (when empty)
     * shown by nothing, this is all there is to compare: its elements have their own paths, checked there.
     */
    private fun erasedCollectionAssignable(
        expected: ResolvedDataContract,
        path: DataTypePath,
        expectedToken: NativeTypeToken,
        actualToken: NativeTypeToken
    ): Boolean {
        val node = expected.contract.structural.typeAt(path)
        if (node !is DataType.Listing && node !is DataType.Mapping) {
            return false
        }
        val expectedClass = expectedToken.type.classifier as? KClass<*>
            ?: return false
        if (!isListOrArray(expectedClass) && !isSet(expectedClass) && !isMap(expectedClass)) {
            return false
        }
        return nativeAssignable(expectedClass.starProjectedType, actualToken.type)
    }

    private fun isStarProjected(type: KType): Boolean =
        type.arguments.isNotEmpty() && type.arguments.all { it == KTypeProjection.STAR }

    private fun incompatibleToken(
        expected: NativeTypeToken,
        actual: NativeTypeToken,
        path: DataTypePath
    ): TypeAcceptance.Rejected =
        TypeAcceptance.Rejected(DataProblem(
            DataProblem.nativeTypeIncompatible,
            "Native type ${actual.type} is not assignable to ${expected.type} at $path",
            path.segments))

    private fun replaceOpaque(expected: DataType, actual: DataType): DataType? {
        if (expected is DataType.Reference || actual is DataType.Reference) {
            return expected
        }
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
            if (nativeAssignable(expectedAncestor.type, actualAncestor.type)) {
                return true
            }
        }
        return false
    }

    private fun nativeAssignable(
        expected: KType,
        actual: KType
    ): Boolean {
        val expectedClass = expected.classifier as? KClass<*> ?: return false
        val actualClass = actual.classifier as? KClass<*> ?: return false
        // A held number's class is the box (`Integer`) of the declared primitive (`int`)
        if (!expectedClass.javaObjectType.isAssignableFrom(actualClass.javaObjectType)) {
            return false
        }
        return try {
            actual.isSubtypeOf(expected)
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

    /** The node [value] holds at [path], or null when nothing is held there (see [heldAcceptance]). */
    private fun heldNode(value: DataValue, path: DataTypePath): DataNode? {
        val access = value.access
        var node = value.root
        for (segment in path.segments) {
            if (access.state(node) == DataState.Null) {
                return null
            }
            node = when (segment) {
                is DataPathSegment.Field -> access.field(node, segment.id)
                is DataPathSegment.Variant ->
                    if (access.activeVariant(node) == segment.id) access.selected(node) else return null
                DataPathSegment.ListingElement ->
                    if (access.size(node) == 0) return null else access.element(node, 0)
                DataPathSegment.MappingValue ->
                    if (access.size(node) == 0) return null else access.entry(node, access.keyAt(node, 0))
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
                BigDecimal::class -> BigDecimal(
                    LiteralDataValues.canonicalNumberText(actual.access.scalar(actual.root)))
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


/** One map per class, created on first use and collected with the class. */
private fun <K> cacheByClass(): ClassValue<ConcurrentHashMap<K, ResolvedDataContract>> =
    object: ClassValue<ConcurrentHashMap<K, ResolvedDataContract>>() {
        override fun computeValue(type: Class<*>) = ConcurrentHashMap<K, ResolvedDataContract>()
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


private fun isSet(classifier: KClass<*>): Boolean =
    classifier == Set::class || classifier == MutableSet::class


private fun isEnum(classifier: KClass<*>): Boolean =
    classifier.java.isEnum || classifier.java.superclass?.isEnum == true


private fun enumConstantNames(classifier: KClass<*>): List<String> {
    val enumClass = if (classifier.java.isEnum) classifier.java else classifier.java.superclass
    return enumClass.enumConstants.map { (it as Enum<*>).name }
}


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
        "kotlin.collections.Set",
        "kotlin.collections.MutableSet" -> Set::class
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
