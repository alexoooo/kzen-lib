package tech.kzen.lib.common.exec.data.problem


class DataException(
    val problem: DataProblem
): IllegalArgumentException(problem.message)
