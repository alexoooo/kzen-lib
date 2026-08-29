package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem


data class FieldId(
    val name: String,
    val occurrence: Int = 0
) {
    init {
        if (name.isEmpty()) {
            throw DataException(DataProblem(
                DataProblem.invalidIdentifier,
                "Field name must not be empty"))
        }
        if (occurrence < 0) {
            throw DataException(DataProblem(
                DataProblem.invalidIdentifier,
                "Field '$name' occurrence must not be negative: $occurrence"))
        }
    }
}
