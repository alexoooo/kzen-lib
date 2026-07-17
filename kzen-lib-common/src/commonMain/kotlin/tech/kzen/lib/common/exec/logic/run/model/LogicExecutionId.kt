package tech.kzen.lib.common.exec.logic.run.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.concurrent.Volatile
import kotlin.random.Random
import kotlin.time.Clock


/**
 * Same logic can be executed multiple times within each run
 */
@Serializable(with = LogicExecutionIdSerializer::class)
data class LogicExecutionId(
    val value: String
) {
    companion object {
        private val clock = Clock.System
        private val random = Random(42)

        @Volatile
        private var previous = clock.now()

        /**
         * Fresh arbitrary ID — timestamp-first (readable and sortable), with a random suffix
         * only on a same-instant collision.
         */
        fun random(): LogicExecutionId {
            val now = clock.now()
            if (now != previous) {
                previous = now
                return LogicExecutionId(now.toString())
            }

            val randomSuffix = random.nextLong()
            return LogicExecutionId("${now}_${randomSuffix.toULong()}")
        }
    }


    override fun toString(): String {
        return value
    }
}


//---------------------------------------------------------------------------------------------------------------------
// SER2: single-field id; no asString()/parse(), so delegate through .value; bound via @Serializable(with).
object LogicExecutionIdSerializer: KSerializer<LogicExecutionId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LogicExecutionId) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): LogicExecutionId {
        return LogicExecutionId(decoder.decodeString())
    }
}
