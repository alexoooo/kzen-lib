package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem


enum class SensitiveSnapshotPolicy {
    Redact,
    Reject
}


data class SnapshotPolicy(
    val maximumDepth: Int = 64,
    val maximumElements: Int = 100_000,
    val maximumTextLength: Int = 1_000_000,
    val maximumBinaryBytes: Int = 10_000_000,
    val maximumDurationMillis: Long = 5_000,
    val sensitive: SensitiveSnapshotPolicy = SensitiveSnapshotPolicy.Redact
) {
    init {
        if (maximumDepth <= 0 || maximumElements <= 0 || maximumTextLength <= 0 ||
            maximumBinaryBytes <= 0 || maximumDurationMillis <= 0
        ) {
            throw DataException(DataProblem(
                DataProblem.snapshotLimit,
                "Every snapshot limit must be strictly positive"))
        }
    }
}
