package tech.kzen.lib.common.exec.data.shape

import kotlinx.serialization.KSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ExecutionValueSerializer


object DataShapeSerializer: KSerializer<DataShape> {
    override val descriptor = ExecutionValueSerializer.descriptor

    override fun serialize(encoder: Encoder, value: DataShape) {
        encoder.encodeSerializableValue(ExecutionValueSerializer, value.asExecutionValue())
    }

    override fun deserialize(decoder: Decoder): DataShape =
        DataShape.ofExecutionValue(
            decoder.decodeSerializableValue<ExecutionValue>(ExecutionValueSerializer))
}


object DataShapeResultSerializer: KSerializer<DataShapeResult> {
    override val descriptor = ExecutionValueSerializer.descriptor

    override fun serialize(encoder: Encoder, value: DataShapeResult) {
        encoder.encodeSerializableValue(ExecutionValueSerializer, value.asExecutionValue())
    }

    override fun deserialize(decoder: Decoder): DataShapeResult =
        DataShapeResult.ofExecutionValue(
            decoder.decodeSerializableValue<ExecutionValue>(ExecutionValueSerializer))
}
