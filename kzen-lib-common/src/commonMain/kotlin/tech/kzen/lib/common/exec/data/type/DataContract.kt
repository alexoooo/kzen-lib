package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


class DataContract(
    val structural: DataType,
    nativeByPath: Map<DataTypePath, TypeMetadata> = emptyMap()
): Digestible {
    companion object {
        fun ofExecutionValue(executionValue: tech.kzen.lib.common.exec.ExecutionValue): DataContract {
            val map = executionValue as? MapExecutionValue
                ?: invalidEncoding("Data contract must be a map")
            val structural = DataType.ofExecutionValue(
                map.values["structural"] ?: invalidEncoding("Data contract is missing 'structural'"))
            val native = map.values["native"] as? ListExecutionValue
                ?: invalidEncoding("Data contract is missing native metadata list")
            val nativeByPath = native.values.associate { encodedEntry ->
                val entry = encodedEntry as? MapExecutionValue
                    ?: invalidEncoding("Native metadata entry must be a map")
                val path = decodePath(
                    entry.values["path"] ?: invalidEncoding("Native metadata entry is missing 'path'"))
                val metadata = entry.values["metadata"] as? MapExecutionValue
                    ?: invalidEncoding("Native metadata entry is missing metadata map")
                path to TypeMetadata.ofExecutionValue(metadata)
            }
            return DataContract(structural, nativeByPath)
        }
    }

    val nativeByPath: Map<DataTypePath, TypeMetadata> = nativeByPath.toMap()

    private val childCache: Map<DataPathSegment, DataContract> by lazy {
        structural.schemaChildren().associate { (segment, childType) ->
            val prefix = DataTypePath(listOf(segment))
            val rebased = nativeByPath.entries
                .filter { it.key.startsWith(prefix) }
                .associate { it.key.removePrefix(prefix) to it.value }
            segment to DataContract(childType, rebased)
        }
    }

    init {
        validateNativeMetadata()
    }

    val structuralDigest: Digest by lazy {
        structural.asExecutionValue().digest()
    }

    val declarationDigest: Digest by lazy {
        asExecutionValue().digest()
    }

    fun child(segment: DataPathSegment): DataContract =
        childCache[segment] ?: throw invalidPath(DataTypePath(listOf(segment)))

    fun asExecutionValue(): MapExecutionValue {
        val metadataEntries = nativeByPath.entries
            .sortedBy { it.key.toString() }
            .map { (path, metadata) ->
                MapExecutionValue(mapOf(
                    "path" to path.asExecutionValue(),
                    "metadata" to metadata.asExecutionValue()))
            }

        return MapExecutionValue(mapOf(
            "structural" to structural.asExecutionValue(),
            "native" to ListExecutionValue(metadataEntries)))
    }

    override fun digest(sink: Digest.Sink) {
        asExecutionValue().digest(sink)
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is DataContract &&
                structural == other.structural && nativeByPath == other.nativeByPath

    override fun hashCode(): Int = 31 * structural.hashCode() + nativeByPath.hashCode()

    override fun toString(): String =
        "DataContract(structural=$structural, nativeByPath=$nativeByPath)"

    private fun validateNativeMetadata() {
        for ((path, metadata) in nativeByPath) {
            val pathType = structural.typeAt(path) ?: throw invalidPath(path)
            if (pathType is DataType.Dynamic) {
                throw DataException(DataProblem(
                    DataProblem.invalidContract,
                    "Dynamic path $path must not carry native metadata",
                    path.segments))
            }
            if (path.segments.lastOrNull() == DataPathSegment.MappingKey) {
                throw DataException(DataProblem(
                    DataProblem.invalidContract,
                    "Mapping-key path $path must not carry native metadata",
                    path.segments))
            }
            if (pathType.nullable != metadata.nullable) {
                throw DataException(DataProblem(
                    DataProblem.invalidContract,
                    "Native nullability at $path (${metadata.nullable}) does not agree with " +
                            "structural nullability (${pathType.nullable})",
                    path.segments))
            }
        }

        for ((path, type) in structural.walk()) {
            if (type is DataType.Opaque && path !in nativeByPath) {
                throw DataException(DataProblem(
                    DataProblem.invalidContract,
                    "Opaque path $path requires native metadata",
                    path.segments))
            }
        }
    }

    private fun invalidPath(path: DataTypePath): DataException =
        DataException(DataProblem(
            DataProblem.invalidPath,
            "Native metadata path does not exist in the structural type: $path",
            path.segments))
}


private fun DataType.childType(segment: DataPathSegment): DataType? =
    when (segment) {
        is DataPathSegment.Field ->
            (this as? DataType.Record)?.fields?.firstOrNull { it.id == segment.id }?.type

        is DataPathSegment.Variant ->
            (this as? DataType.Union)?.variants?.firstOrNull { it.id == segment.id }?.type

        DataPathSegment.ListingElement ->
            (this as? DataType.Listing)?.element

        DataPathSegment.MappingKey ->
            (this as? DataType.Mapping)?.key

        DataPathSegment.MappingValue ->
            (this as? DataType.Mapping)?.value

        is DataPathSegment.Entry,
        is DataPathSegment.Element ->
            null
    }


private fun DataType.schemaChildren(): List<Pair<DataPathSegment, DataType>> =
    when (this) {
        is DataType.Record -> fields.map { DataPathSegment.Field(it.id) to it.type }
        is DataType.Mapping -> listOf(
            DataPathSegment.MappingKey to key,
            DataPathSegment.MappingValue to value)
        is DataType.Listing -> listOf(DataPathSegment.ListingElement to element)
        is DataType.Union -> variants.map { DataPathSegment.Variant(it.id) to it.type }
        is DataType.Dynamic,
        is DataType.Opaque,
        is DataType.Scalar -> emptyList()
    }


private fun DataType.typeAt(path: DataTypePath): DataType? {
    var current = this
    for (segment in path.segments) {
        current = current.childType(segment) ?: return null
    }
    return current
}


private fun DataType.walk(
    path: DataTypePath = DataTypePath.root
): List<Pair<DataTypePath, DataType>> {
    val descendants = when (this) {
        is DataType.Record -> fields.flatMap { field ->
            field.type.walk(path.child(DataPathSegment.Field(field.id)))
        }

        is DataType.Mapping ->
            key.walk(path.child(DataPathSegment.MappingKey)) +
                    value.walk(path.child(DataPathSegment.MappingValue))

        is DataType.Listing ->
            element.walk(path.child(DataPathSegment.ListingElement))

        is DataType.Union -> variants.flatMap { variant ->
            variant.type.walk(path.child(DataPathSegment.Variant(variant.id)))
        }

        is DataType.Dynamic,
        is DataType.Opaque,
        is DataType.Scalar ->
            emptyList()
    }
    return listOf(path to this) + descendants
}


private fun DataTypePath.asExecutionValue(): ListExecutionValue =
    ListExecutionValue(segments.map { segment ->
        val (kind, value) = when (segment) {
            is DataPathSegment.Field -> "field" to segment.id.asExecutionValue()
            is DataPathSegment.Variant -> "variant" to TextExecutionValue(segment.id.value)
            DataPathSegment.ListingElement -> "listing-element" to TextExecutionValue("")
            DataPathSegment.MappingKey -> "mapping-key" to TextExecutionValue("")
            DataPathSegment.MappingValue -> "mapping-value" to TextExecutionValue("")
            is DataPathSegment.Entry -> "entry" to MapExecutionValue(mapOf(
                "kind" to segment.kind.asExecutionValue(),
                "key" to segment.key))
            is DataPathSegment.Element -> "element" to TextExecutionValue(segment.index.toString())
        }
        MapExecutionValue(mapOf(
            "kind" to TextExecutionValue(kind),
            "value" to value))
    })


private fun decodePath(executionValue: tech.kzen.lib.common.exec.ExecutionValue): DataTypePath {
    val list = executionValue as? ListExecutionValue
        ?: invalidEncoding("Data type path must be a list")
    return DataTypePath(list.values.map { encodedSegment ->
        val segment = encodedSegment as? MapExecutionValue
            ?: invalidEncoding("Data type path segment must be a map")
        val kind = (segment.values["kind"] as? TextExecutionValue)?.value
            ?: invalidEncoding("Data type path segment is missing text 'kind'")
        val value = segment.values["value"]
            ?: invalidEncoding("Data type path segment is missing 'value'")
        when (kind) {
            "field" -> DataPathSegment.Field(decodeFieldId(value))
            "variant" -> DataPathSegment.Variant(VariantId(value.requireText("variant")))
            "listing-element" -> DataPathSegment.ListingElement
            "mapping-key" -> DataPathSegment.MappingKey
            "mapping-value" -> DataPathSegment.MappingValue
            "element" -> DataPathSegment.Element(value.requireText("element index").toInt())
            else -> invalidEncoding("Unsupported data type path segment '$kind'")
        }
    })
}


private fun decodeFieldId(executionValue: tech.kzen.lib.common.exec.ExecutionValue): FieldId {
    val map = executionValue as? MapExecutionValue
        ?: invalidEncoding("Field identifier must be a map")
    val name = map.values["name"].requireText("field name")
    val occurrence = map.values["occurrence"].requireText("field occurrence").toInt()
    return FieldId(name, occurrence)
}


private fun tech.kzen.lib.common.exec.ExecutionValue?.requireText(label: String): String =
    (this as? TextExecutionValue)?.value ?: invalidEncoding("$label must be text")


private fun invalidEncoding(message: String): Nothing =
    throw DataException(DataProblem(DataProblem.invalidTypeEncoding, message))
