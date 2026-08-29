package tech.kzen.lib.common.exec.data.shape

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem


data class SampleCoverage(
    val observedItems: Long,
    val observedBytes: Long? = null,
    val complete: Boolean = false
) {
    init {
        if (observedItems <= 0) {
            throw DataException(DataProblem(
                DataProblem.invalidContract,
                "Observed item count must be positive: $observedItems"))
        }
        if (observedBytes != null && observedBytes <= 0) {
            throw DataException(DataProblem(
                DataProblem.invalidContract,
                "Observed byte count must be positive when supplied: $observedBytes"))
        }
    }
}
