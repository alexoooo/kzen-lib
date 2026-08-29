package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.BinaryHandleExecutionValue
import tech.kzen.lib.common.exec.BooleanExecutionValue
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.NumberExecutionValue
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible
import kotlin.time.TimeSource


/** Immutable detached content. The exposed tree is a defensive copy and never carries native metadata. */
class DataSnapshot private constructor(
    val type: DataType,
    private val frozenValue: ExecutionValue
): Digestible {
    val value: ExecutionValue
        get() = frozenValue.deepCopy()

    fun asDataValue(): DataValue = LiteralDataValues.decode(type, frozenValue)

    override fun digest(sink: Digest.Sink) {
        type.digest(sink)
        frozenValue.digest(sink)
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is DataSnapshot &&
                type == other.type && frozenValue == other.frozenValue

    override fun hashCode(): Int = 31 * type.hashCode() + frozenValue.hashCode()

    override fun toString(): String = "DataSnapshot(type=$type, value=$frozenValue)"

    companion object {
        /** Validates under [type] and freezes a pre-existing detached tree. */
        fun of(type: DataType, value: ExecutionValue): DataSnapshot {
            val frozen = value.deepCopy()
            val decoded = try {
                LiteralDataValues.decode(type, frozen)
            }
            catch (e: DataAccessException) {
                throw DataException(e.problem)
            }
            val problems = DataValueAlgebra.validate(decoded.contract, decoded)
            if (problems.isNotEmpty()) {
                throw DataException(problems.first())
            }
            return DataSnapshot(type, frozen)
        }


        fun capture(
            value: DataValue,
            policy: SnapshotPolicy = SnapshotPolicy(),
            sensitive: Boolean = false
        ): SnapshotResult {
            if (sensitive) {
                return when (policy.sensitive) {
                    SensitiveSnapshotPolicy.Redact -> SnapshotResult.Redacted
                    SensitiveSnapshotPolicy.Reject -> SnapshotResult.Rejected(listOf(DataProblem(
                        DataProblem.snapshotRejected,
                        "Sensitive value snapshot is forbidden")))
                }
            }
            val writer = SnapshotWriter(value, policy)
            val executionValue = writer.write()
            if (writer.problems.isNotEmpty() || executionValue == null) {
                return SnapshotResult.Rejected(writer.problems)
            }
            return try {
                SnapshotResult.Complete(DataSnapshot(value.type, executionValue.deepCopy()))
            }
            catch (e: RuntimeException) {
                SnapshotResult.Rejected(listOf(DataProblem(
                    DataProblem.snapshotRejected,
                    e.message ?: "Snapshot validation failed")))
            }
        }
    }
}


private class SnapshotWriter(
    private val value: DataValue,
    private val policy: SnapshotPolicy
) {
    val problems = mutableListOf<DataProblem>()
    private val started = TimeSource.Monotonic.markNow()
    private var elements = 0
    private val openIdentities = mutableListOf<Any>()
    private val seenIdentities = mutableListOf<Any>()

    fun write(): ExecutionValue? = writeNode(value.root, value.type, emptyList(), 1)

    private fun writeNode(
        node: DataNode,
        type: DataType,
        path: List<DataPathSegment>,
        depth: Int
    ): ExecutionValue? {
        if (!checkBounds(path, depth)) return null
        return try {
            when (value.access.state(node)) {
                DataState.Absent -> null
                DataState.Null -> {
                    if (!type.nullable) reject(
                        DataProblem.invalidState, "Null is not allowed by $type", path)
                    else NullExecutionValue
                }
                DataState.Present -> writePresent(node, type, path, depth)
            }
        }
        catch (e: DataAccessException) {
            problems += e.problem
            null
        }
        catch (e: RuntimeException) {
            reject(DataProblem.snapshotRejected, e.message ?: "Snapshot read failed", path)
        }
    }

    private fun writePresent(
        node: DataNode,
        type: DataType,
        path: List<DataPathSegment>,
        depth: Int
    ): ExecutionValue? {
        if (type is DataType.Opaque) {
            return reject(DataProblem.snapshotOpaque, "Opaque value cannot be snapshotted", path)
        }
        val identity = containerIdentity(node, type)
        if (identity != null) {
            if (seenIdentities.any { it === identity }) {
                return reject(DataProblem.snapshotCycle, "Container identity was revisited", path)
            }
            seenIdentities += identity
            openIdentities += identity
        }
        try {
            return when (type) {
                is DataType.Scalar -> writeScalar(value.access.scalar(node), type.kind, path)
                is DataType.Record -> writeRecord(node, type, path, depth)
                is DataType.Listing -> writeListing(node, type, path, depth)
                is DataType.Mapping -> writeMapping(node, type, path, depth)
                is DataType.Union -> writeUnion(node, type, path, depth)
                is DataType.Dynamic -> reject(
                    DataProblem.snapshotRejected,
                    "A Dynamic live node must expose a concrete runtime contract before snapshot",
                    path)
                is DataType.Opaque -> error("handled")
            }
        }
        finally {
            if (identity != null) openIdentities.removeAt(openIdentities.lastIndex)
        }
    }

    private fun writeRecord(
        node: DataNode,
        type: DataType.Record,
        path: List<DataPathSegment>,
        depth: Int
    ): ExecutionValue? {
        if (type.fields.any { it.id.occurrence != 0 }) {
            return reject(
                DataProblem.snapshotDuplicateField,
                "Records with duplicate field names cannot use the v1 snapshot grammar",
                path)
        }
        val fields = linkedMapOf<String, ExecutionValue>()
        for (field in type.fields) {
            if (!count(path)) return null
            val segment = DataPathSegment.Field(field.id)
            val child = value.access.field(node, field.id)
            if (value.access.state(child) == DataState.Absent) {
                if (!field.optional) return reject(
                    DataProblem.missingValue, "Required field '${field.id}' is absent", path + segment)
                continue
            }
            fields[field.id.name] = writeNode(child, field.type, path + segment, depth + 1) ?: return null
        }
        return MapExecutionValue(fields.toMap())
    }

    private fun writeListing(
        node: DataNode,
        type: DataType.Listing,
        path: List<DataPathSegment>,
        depth: Int
    ): ExecutionValue? {
        val size = value.access.size(node)
        val values = ArrayList<ExecutionValue>(size)
        for (index in 0 until size) {
            if (!count(path)) return null
            val segment = DataPathSegment.Element(index)
            values += writeNode(
                value.access.element(node, index), type.element,
                path + segment, depth + 1) ?: return null
        }
        return ListExecutionValue(values.toList())
    }

    private fun writeMapping(
        node: DataNode,
        type: DataType.Mapping,
        path: List<DataPathSegment>,
        depth: Int
    ): ExecutionValue? {
        val size = value.access.size(node)
        val keyType = type.key as? DataType.Scalar
            ?: return if (size == 0) MapExecutionValue(emptyMap()) else reject(
                DataProblem.invalidMappingKey,
                "Non-empty snapshot mapping requires a concrete scalar key",
                path)
        if (keyType.kind == ScalarKind.Text) {
            val mapped = linkedMapOf<String, ExecutionValue>()
            for (index in 0 until size) {
                if (!count(path)) return null
                val key = value.access.keyAt(node, index)
                val text = (key as? TextExecutionValue)?.value
                    ?: return reject(DataProblem.invalidMappingKey, "Text mapping key required", path)
                val segment = DataPathSegment.Entry(keyType.kind, key)
                mapped[text] = writeNode(
                    value.access.entry(node, key), type.value,
                    path + segment, depth + 1) ?: return null
            }
            return MapExecutionValue(mapped.toMap())
        }
        val entries = mutableListOf<ExecutionValue>()
        for (index in 0 until size) {
            if (!count(path)) return null
            val key = value.access.keyAt(node, index)
            val segment = DataPathSegment.Entry(keyType.kind, key)
            val child = writeNode(
                value.access.entry(node, key), type.value,
                path + segment, depth + 1) ?: return null
            entries += MapExecutionValue(mapOf("key" to key.deepCopy(), "value" to child))
        }
        return ListExecutionValue(entries.toList())
    }

    private fun writeUnion(
        node: DataNode,
        type: DataType.Union,
        path: List<DataPathSegment>,
        depth: Int
    ): ExecutionValue? {
        if (!count(path)) return null
        val active = value.access.activeVariant(node)
        val variant = type.variants.firstOrNull { it.id == active }
            ?: return reject(DataProblem.unionVariantUnknown, "Union has no variant '$active'", path)
        val segment = DataPathSegment.Variant(active)
        val selected = writeNode(
            value.access.selected(node), variant.type, path + segment, depth + 1) ?: return null
        return MapExecutionValue(mapOf(
            "variant" to TextExecutionValue(active.value),
            "value" to selected))
    }

    private fun writeScalar(
        scalar: ScalarExecutionValue,
        kind: ScalarKind,
        path: List<DataPathSegment>
    ): ExecutionValue? {
        if (scalar is BinaryHandleExecutionValue) {
            return reject(
                DataProblem.snapshotBinaryHandle,
                "Generic snapshots cannot contain binary handles",
                path)
        }
        return when (scalar) {
            is TextExecutionValue -> {
                if (scalar.value.length > policy.maximumTextLength) reject(
                    DataProblem.snapshotLimit, "Text exceeds maximum length", path)
                else scalar
            }
            is BinaryExecutionValue -> {
                if (scalar.value.size > policy.maximumBinaryBytes) reject(
                    DataProblem.snapshotLimit, "Binary exceeds maximum bytes", path)
                else BinaryExecutionValue(scalar.value.copyOf())
            }
            is BooleanExecutionValue -> scalar
            is NumberExecutionValue -> scalar
            is LongExecutionValue -> when (kind) {
                is ScalarKind.Integer,
                ScalarKind.Decimal -> TextExecutionValue(scalar.value.toString())
                else -> scalar
            }
            is BinaryHandleExecutionValue -> error("handled")
        }
    }

    private fun containerIdentity(node: DataNode, type: DataType): Any? {
        if (type !is DataType.Record && type !is DataType.Listing && type !is DataType.Mapping) return null
        if (value.access.contract(node).nativeByPath.isEmpty()) return null
        return value.access.native(node)
    }

    private fun checkBounds(path: List<DataPathSegment>, depth: Int): Boolean {
        if (depth > policy.maximumDepth) {
            reject(DataProblem.snapshotLimit, "Snapshot exceeds maximum depth", path)
            return false
        }
        if (started.elapsedNow().inWholeMilliseconds > policy.maximumDurationMillis) {
            reject(DataProblem.snapshotLimit, "Snapshot exceeds maximum duration", path)
            return false
        }
        return true
    }

    private fun count(path: List<DataPathSegment>): Boolean {
        elements += 1
        if (elements > policy.maximumElements) {
            reject(DataProblem.snapshotLimit, "Snapshot exceeds cumulative element limit", path)
            return false
        }
        return true
    }

    private fun reject(code: String, message: String, path: List<DataPathSegment>): Nothing? {
        problems += DataProblem(code, message, path)
        return null
    }
}


private fun ExecutionValue.deepCopy(): ExecutionValue =
    when (this) {
        NullExecutionValue -> this
        is TextExecutionValue -> this
        is BooleanExecutionValue -> this
        is NumberExecutionValue -> this
        is LongExecutionValue -> this
        is BinaryExecutionValue -> BinaryExecutionValue(value.copyOf())
        is BinaryHandleExecutionValue -> this
        is ListExecutionValue -> ListExecutionValue(values.map { it.deepCopy() })
        is MapExecutionValue -> MapExecutionValue(values.mapValues { it.value.deepCopy() }.toMap())
    }
