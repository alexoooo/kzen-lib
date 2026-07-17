package tech.kzen.lib.common.exec.task.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


@Serializable(with = TaskIdSerializer::class)
data class TaskId(
    val identifier: String
)


//---------------------------------------------------------------------------------------------------------------------
// SER4: single-field id; encodes as the bare string (like LogicRunIdSerializer), so a TaskId property rides
// the wire byte-identically to the old codec's `taskId.identifier`.
object TaskIdSerializer: KSerializer<TaskId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("tech.kzen.lib.common.exec.task.model.TaskId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TaskId) {
        encoder.encodeString(value.identifier)
    }

    override fun deserialize(decoder: Decoder): TaskId {
        return TaskId(decoder.decodeString())
    }
}