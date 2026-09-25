package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.MetadataContract
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


/**
 * A snapshot of a value's metadata ([DataSnapshot.metadata]), the snapshot side of [ValueMetadata]: a record of
 * plain data, frozen. It has no metadata of its own: there is no slot for it, so snapshot metadata never nests.
 * Its [type] is checked as a [MetadataContract] when it is built. [snapshot] reads it as a plain snapshot.
 */
class MetadataSnapshot private constructor(
    val type: DataType.Record,
    private val frozenValue: ExecutionValue
): Digestible {
    companion object {
        /** [snapshot] as metadata; fails by name unless it is a plain record without metadata of its own. */
        fun of(snapshot: DataSnapshot): MetadataSnapshot {
            if (snapshot.metadata != null) {
                throw DataException(DataProblem(
                    DataProblem.invalidContract, "Value metadata must not have metadata of its own"))
            }
            val record = snapshot.type as? DataType.Record
                ?: throw DataException(DataProblem(
                    DataProblem.invalidContract, "Value metadata must be a record, not ${snapshot.type}"))
            return MetadataSnapshot(record, snapshot.frozenValue)
        }
    }


    init {
        // Fails by name on a nullable record or an opaque member
        MetadataContract(type)
    }


    /** The metadata record as a plain snapshot (without metadata). */
    val snapshot: DataSnapshot = DataSnapshot(type, frozenValue)

    val value: ExecutionValue
        get() = snapshot.value


    /** This snapshot as a live value's metadata. */
    fun asMetadata(): ValueMetadata =
        ValueMetadata.of(snapshot.asDataValue())


    override fun digest(sink: Digest.Sink) {
        snapshot.digest(sink)
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is MetadataSnapshot && snapshot == other.snapshot

    override fun hashCode(): Int =
        snapshot.hashCode()

    override fun toString(): String =
        "MetadataSnapshot(type=$type, value=$frozenValue)"
}
