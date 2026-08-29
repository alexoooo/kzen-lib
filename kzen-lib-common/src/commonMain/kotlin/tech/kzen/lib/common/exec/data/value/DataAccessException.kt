package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.data.problem.DataProblem


class DataAccessException(
    val problem: DataProblem
): RuntimeException(problem.message)
