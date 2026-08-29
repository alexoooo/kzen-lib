package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType


/** A live, read-only root view. Content equality is deliberately not defined on live values. */
class DataValue(
    val access: ValueAccess,
    val root: DataNode
) {
    init {
        if (access.state(root) == DataState.Absent) {
            throw DataAccessException(DataProblem(
                DataProblem.invalidState,
                "A DataValue root must not be absent"))
        }
    }

    val contract: DataContract
        get() = access.contract(root)

    val type: DataType
        get() = contract.structural
}
