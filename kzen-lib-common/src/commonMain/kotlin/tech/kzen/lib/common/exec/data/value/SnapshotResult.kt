package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.data.problem.DataProblem


sealed interface SnapshotResult {
    data class Complete(val snapshot: DataSnapshot): SnapshotResult
    data object Redacted: SnapshotResult
    class Rejected(problems: List<DataProblem>): SnapshotResult {
        val problems: List<DataProblem> = problems.toList()

        override fun equals(other: Any?): Boolean =
            this === other || other is Rejected && problems == other.problems

        override fun hashCode(): Int = problems.hashCode()
        override fun toString(): String = "Rejected(problems=$problems)"
    }
}
