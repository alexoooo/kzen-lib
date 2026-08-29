package tech.kzen.lib.common.exec.data.shape

import kotlinx.serialization.Serializable


@Serializable(with = DataShapeResultSerializer::class)
sealed interface DataShapeResult {
    companion object {
        fun ofExecutionValue(executionValue: tech.kzen.lib.common.exec.ExecutionValue): DataShapeResult =
            DataShapeResultExecutionValue.decode(executionValue)
    }

    data object Unavailable: DataShapeResult
    data class Observed(val shape: DataShape): DataShapeResult
}
