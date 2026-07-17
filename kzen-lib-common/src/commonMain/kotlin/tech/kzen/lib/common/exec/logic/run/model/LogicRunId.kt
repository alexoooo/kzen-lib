package tech.kzen.lib.common.exec.logic.run.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


/**
 * Identifies a top-level run.
 *
 * Today at most one top-level run is active at any given time — but that is a **current
 * `ServerLogicController` limitation**, not an engine invariant: a `RunEngine` owns one run with no
 * process-global state, so multiple runs may execute concurrently once the controller is made per-run
 * (engine plan E6 "multiple concurrent runs" — deferred). Treat this id as a first-class addressing
 * key, not an assumption that only one run exists.
 */
@Serializable(with = LogicRunIdSerializer::class)
data class LogicRunId(
    val value: String
) {
    override fun toString(): String {
        return value
    }
}


//---------------------------------------------------------------------------------------------------------------------
// SER2: single-field id; no asString()/parse(), so delegate through .value; bound via @Serializable(with).
object LogicRunIdSerializer: KSerializer<LogicRunId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("tech.kzen.lib.common.exec.logic.run.model.LogicRunId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LogicRunId) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): LogicRunId {
        return LogicRunId(decoder.decodeString())
    }
}
