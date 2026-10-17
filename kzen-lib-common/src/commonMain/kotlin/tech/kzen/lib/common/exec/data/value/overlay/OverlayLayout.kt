package tech.kzen.lib.common.exec.data.value.overlay

import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.value.DataOverlay
import tech.kzen.lib.common.exec.data.value.DataValue


/**
 * Where each field of a composed record comes from, worked out once per base contract and set fields' contracts:
 * the composed [contract] ([DataOverlay.contract]) and, by position in it, either a set value or the base's field.
 *
 * The base's fields come first, in its order, each at the position it has in the base (a replaced one keeps its
 * position); appended fields follow. So position `p` below the base's field count that no set value replaces is
 * the base's field at `p`.
 */
internal class OverlayLayout(
    base: DataContract?,
    fields: List<Pair<FieldId, DataContract>>
) {
    val contract: DataContract = DataOverlay.contract(base, fields)

    private val ids: List<FieldId> = (contract.structural as DataType.Record).fields.map { it.id }

    private val positions: Map<FieldId, Int> = ids.withIndex().associate { it.value to it.index }

    // Per position: the index of the set value there, or -1 for the base's field at that position
    private val setIndexes: IntArray = run {
        val setIndexById = fields.withIndex().associate { it.value.first to it.index }
        IntArray(ids.size) { position -> setIndexById[ids[position]] ?: -1 }
    }


    val size: Int
        get() = ids.size

    fun id(position: Int): FieldId =
        ids[position]

    /** The position of [id], or -1 when the record has no such field. */
    fun position(id: FieldId): Int =
        positions[id] ?: -1

    /** The index (into the set values) of the value at [position], or -1 when it is the base's field. */
    fun setIndex(position: Int): Int =
        setIndexes[position]


    /**
     * The record [base] with [values] set, in the order of the fields this layout was made for; [base]'s own
     * metadata is kept, and the result depends on every resource [base] or any of [values] depends on. The
     * caller vouches that their contracts are this layout's.
     */
    fun record(base: DataValue?, values: List<DataValue>): DataValue {
        var owners = base?.owners
        for (value in values) {
            val valueOwners = value.owners ?: continue
            owners = owners?.plus(valueOwners) ?: valueOwners
        }
        return DataValue(OverlayAccess(this, base, values), OverlayAccess.root, base?.metadata, owners)
    }
}
