package tech.kzen.lib.common.exec.logic

import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.tuple.TupleValue


interface LogicExecutionFacade: AutoCloseable {
    fun beforeStart(arguments: TupleValue): Boolean

    fun continueOrStart(): LogicResult
}
