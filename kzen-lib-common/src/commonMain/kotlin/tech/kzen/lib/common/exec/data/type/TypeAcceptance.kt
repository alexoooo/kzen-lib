package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.data.problem.DataProblem


sealed interface TypeAcceptance {
    data object Accepted: TypeAcceptance
    data class Rejected(val problem: DataProblem): TypeAcceptance
}
