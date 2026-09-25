package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.MetadataContract
import tech.kzen.lib.common.exec.data.type.VariantId
import tech.kzen.lib.platform.platformSynchronized


/**
 * A record composed without copying: a base record with fields set on top of it. A set field replaces the
 * base's field of the same name in place, and is otherwise appended. Every field is read through lazily from
 * the value it came from, so composing costs nothing per field until it is read, and a composition of detached
 * parts (literals, snapshots, other compositions) stays detached.
 *
 * This is how a value's [ValueMetadata] is built up as it travels: a source's facts, a computed field, a
 * `parent` holding the metadata of the value this one was derived from.
 */
object DataOverlay {
    private val emptyRecord = DataContract(DataType.Record(emptyList()))


    /** The record contract [base] (an empty record when null) with [fields] set. */
    fun contract(base: DataContract?, fields: List<Pair<FieldId, DataContract>>): DataContract {
        val baseContract = base?.expanded() ?: emptyRecord
        val record = baseContract.structural as? DataType.Record
            ?: throw DataException(DataProblem(
                DataProblem.invalidRecord, "Only a record can have fields set, not ${baseContract.structural}"))
        requireDistinct(fields.map { it.first })

        val replaced = fields.map { it.first }.toSet()
        val kept = record.fields.filter { it.id !in replaced }
        var composed = DataContract(
            DataType.Record(kept, record.nullable),
            baseContract.nativeByPath.filterKeys { it.fieldOrNull() !in replaced },
            baseContract.definitions,
            baseContract.definitionNatives,
            baseContract.constraintsByPath.filterKeys { it.fieldOrNull() !in replaced },
            baseContract.metadata)
        composed = composed.withFields(fields.map { (id, contract) ->
            DataField(id, contract.structural) to contract
        })

        // Back into the base's field order: a replaced field keeps its position
        val byId = (composed.structural as DataType.Record).fields.associateBy { it.id }
        val baseOrder = record.fields.map { it.id }
        val order = baseOrder + fields.map { it.first }.filter { it !in baseOrder }
        return DataContract(
            DataType.Record(order.map { byId.getValue(it) }, record.nullable),
            composed.nativeByPath,
            composed.definitions,
            composed.definitionNatives,
            composed.constraintsByPath,
            composed.metadata)
    }


    /** A view of record [base] (an empty record when null) with [fields] set; [base]'s own metadata is kept. */
    fun record(base: DataValue?, fields: List<Pair<FieldId, DataValue>>): DataValue {
        if (base != null && fields.isEmpty()) {
            return base
        }
        val contract = contract(base?.payloadContract, fields.map { it.first to it.second.payloadContract })

        val bindings = LinkedHashMap<FieldId, Lazy<Bound>>()
        if (base != null) {
            bindings.putAll(bindingsOf(base))
        }
        for ((id, value) in fields) {
            bindings[id] = lazyOf(Bound(value.access, value.root))
        }
        val composite = base?.let { nativeSourceOf(it) }
        return DataValue(OverlayAccess(contract, bindings, composite), OverlayAccess.root, base?.metadata)
    }


    /** [value] with [fields] set on its metadata (starting from none when it has no metadata). */
    fun withMetadataFields(value: DataValue, fields: List<Pair<FieldId, DataValue>>): DataValue =
        if (fields.isEmpty()) value
        else value.withMetadata(ValueMetadata.of(record(value.metadata?.value, fields)))


    /** The metadata contract [contract] would have after [withMetadataFields]. */
    fun withMetadataFields(contract: DataContract, fields: List<Pair<FieldId, DataContract>>): DataContract =
        if (fields.isEmpty()) contract
        else contract.withMetadata(MetadataContract.of(contract(contract.metadata?.contract, fields)))


    private fun requireDistinct(ids: List<FieldId>) {
        val repeated = ids.groupBy { it }.filterValues { it.size > 1 }.keys
        if (repeated.isNotEmpty()) {
            throw DataException(DataProblem(DataProblem.invalidRecord, "Fields set more than once: $repeated"))
        }
    }


    private fun DataTypePath.fieldOrNull(): FieldId? =
        (segments.firstOrNull() as? DataPathSegment.Field)?.id


    private fun bindingsOf(base: DataValue): Map<FieldId, Lazy<Bound>> {
        val overlay = base.access as? OverlayAccess
        if (overlay != null && base.root == OverlayAccess.root) {
            return overlay.bindings
        }
        val record = base.payloadContract.expanded().structural as? DataType.Record
            ?: throw DataException(DataProblem(
                DataProblem.invalidRecord, "Only a record can have fields set, not ${base.type}"))
        return record.fields.associate { field ->
            field.id to lazy { Bound(base.access, base.access.field(base.root, field.id)) }
        }
    }


    // The value whose native stands for the composed record: the original record under any composition
    private fun nativeSourceOf(base: DataValue): DataValue? {
        val overlay = base.access as? OverlayAccess
        if (overlay != null && base.root == OverlayAccess.root) {
            return overlay.nativeSource
        }
        return base
    }


    private class Bound(val access: ValueAccess, val node: DataNode)


    /** Tokens belong to this view; delegated nodes are interned, so recursive data stays navigable without copying. */
    private class OverlayAccess(
        private val rootContract: DataContract,
        val bindings: Map<FieldId, Lazy<Bound>>,
        val nativeSource: DataValue?
    ): ValueAccess {
        companion object {
            val root = DataNode(0)
        }

        private val nodes = mutableListOf<Bound>()
        private val tokens = HashMap<ValueAccess, MutableMap<DataNode, DataNode>>()

        private fun intern(bound: Bound): DataNode = platformSynchronized(nodes) {
            tokens.getOrPut(bound.access) { mutableMapOf() }.getOrPut(bound.node) {
                nodes += bound
                DataNode(nodes.size.toLong())
            }
        }

        private fun bound(node: DataNode): Bound = platformSynchronized(nodes) {
            if (node.token <= 0 || node.token > nodes.size) {
                throw DataAccessException(DataProblem(
                    DataProblem.invalidOperation, "Operation requires a composed field node"))
            }
            nodes[node.token.toInt() - 1]
        }

        private inline fun <T> read(node: DataNode, action: (ValueAccess, DataNode) -> T): T {
            val bound = bound(node)
            return action(bound.access, bound.node)
        }

        private inline fun navigate(node: DataNode, action: (ValueAccess, DataNode) -> DataNode): DataNode =
            read(node) { access, original -> intern(Bound(access, action(access, original))) }

        private fun rootOnly(operation: String): Nothing =
            throw DataAccessException(DataProblem(
                DataProblem.invalidOperation, "A composed record does not support $operation"))

        override fun contract(node: DataNode): DataContract =
            if (node == root) rootContract else read(node) { a, n -> a.contract(n) }

        override fun state(node: DataNode): DataState =
            if (node == root) DataState.Present else read(node) { a, n -> a.state(n) }

        override fun field(node: DataNode, field: FieldId): DataNode =
            if (node == root) {
                val binding = bindings[field]
                    ?: throw DataAccessException(DataProblem(
                        DataProblem.invalidPath, "Unknown field '$field'"))
                intern(binding.value)
            }
            else {
                navigate(node) { a, n -> a.field(n, field) }
            }

        override fun native(node: DataNode): Any =
            if (node == root) {
                val source = nativeSource ?: rootOnly("native access")
                source.access.native(source.root)
            }
            else {
                read(node) { a, n -> a.native(n) }
            }

        override fun size(node: DataNode): Int =
            if (node == root) bindings.size else read(node) { a, n -> a.size(n) }

        override fun activeVariant(node: DataNode): VariantId =
            if (node == root) rootOnly("variants") else read(node) { a, n -> a.activeVariant(n) }
        override fun selected(node: DataNode): DataNode =
            if (node == root) rootOnly("variants") else navigate(node) { a, n -> a.selected(n) }
        override fun entry(node: DataNode, key: ScalarExecutionValue): DataNode =
            if (node == root) rootOnly("keyed entries") else navigate(node) { a, n -> a.entry(n, key) }
        override fun element(node: DataNode, index: Int): DataNode =
            if (node == root) rootOnly("elements") else navigate(node) { a, n -> a.element(n, index) }
        override fun keyAt(node: DataNode, index: Int): ScalarExecutionValue =
            if (node == root) rootOnly("keys") else read(node) { a, n -> a.keyAt(n, index) }
        override fun scalar(node: DataNode): ScalarExecutionValue =
            if (node == root) rootOnly("scalar reads") else read(node) { a, n -> a.scalar(n) }
        override fun readBoolean(node: DataNode): Boolean =
            if (node == root) rootOnly("scalar reads") else read(node) { a, n -> a.readBoolean(n) }
        override fun readLong(node: DataNode): Long =
            if (node == root) rootOnly("scalar reads") else read(node) { a, n -> a.readLong(n) }
        override fun readDouble(node: DataNode): Double =
            if (node == root) rootOnly("scalar reads") else read(node) { a, n -> a.readDouble(n) }
        override fun readText(node: DataNode): String =
            if (node == root) rootOnly("scalar reads") else read(node) { a, n -> a.readText(n) }
        override fun readBinary(node: DataNode): ByteArray =
            if (node == root) rootOnly("scalar reads") else read(node) { a, n -> a.readBinary(n) }
    }
}
