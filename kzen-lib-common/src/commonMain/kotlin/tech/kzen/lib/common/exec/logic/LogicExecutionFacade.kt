package tech.kzen.lib.common.exec.logic

import tech.kzen.lib.common.exec.logic.model.LogicResult
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.definition.GraphDefinition


interface LogicExecutionFacade: AutoCloseable {
    fun beforeStart(arguments: TupleValue): Boolean

    /**
     * @param graphDefinition the current (possibly edited) graph for this run pass, so a nested
     *  execution resumes against live notation rather than a stale start-time snapshot.
     */
    fun continueOrStart(graphDefinition: GraphDefinition): LogicResult
}
