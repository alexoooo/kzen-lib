package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.MetadataContract
import tech.kzen.lib.common.exec.data.value.overlay.MetadataShape
import tech.kzen.lib.common.exec.data.value.overlay.OverlayLayout


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


    /**
     * A view of record [base] (an empty record when null) with [fields] set; [base]'s own metadata is kept. Metadata
     * composed per value on a contract that repeats is cheaper through a [MetadataShape], which reads the same.
     */
    fun record(base: DataValue?, fields: List<Pair<FieldId, DataValue>>): DataValue {
        if (base != null && fields.isEmpty()) {
            return base
        }
        val layout = OverlayLayout(base?.payloadContract, fields.map { it.first to it.second.payloadContract })
        return layout.record(base, fields.map { it.second })
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
}
