package tech.kzen.lib.common.exec.logic

import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition


interface LogicExecution {
    /**
     * Initialize for next execution
     * @return false if something went wrong
     */
    fun beforeStart(
        arguments: TupleValue
    ): Boolean


    /**
     * Continue running where we left off (possibly from beginning)
     * @param logicControl might have LogicCommand Pause,
     *  where we continue to run as little as possible from where we left off
     * @param graphDefinition
     */
    fun continueOrStart(
        logicControl: LogicControl,
        graphDefinition: GraphDefinition
    ): LogicResult


    fun close(error: Boolean)
}
