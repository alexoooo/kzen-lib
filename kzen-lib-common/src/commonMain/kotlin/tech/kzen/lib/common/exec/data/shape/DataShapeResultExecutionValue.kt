package tech.kzen.lib.common.exec.data.shape

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem


fun DataShapeResult.asExecutionValue(): MapExecutionValue =
    when (this) {
        DataShapeResult.Unavailable -> MapExecutionValue(mapOf(
            "case" to TextExecutionValue("unavailable")))
        is DataShapeResult.Observed -> MapExecutionValue(mapOf(
            "case" to TextExecutionValue("observed"),
            "shape" to shape.asExecutionValue()))
    }


object DataShapeResultExecutionValue {
    fun decode(executionValue: ExecutionValue): DataShapeResult {
        val map = executionValue as? MapExecutionValue
            ?: invalidResult("Data shape result must be a map")
        val case = (map.values["case"] as? TextExecutionValue)?.value
            ?: invalidResult("Data shape result is missing text 'case'")
        return when (case) {
            "unavailable" -> DataShapeResult.Unavailable
            "observed" -> DataShapeResult.Observed(DataShape.ofExecutionValue(
                map.values["shape"] ?: invalidResult("Observed shape result is missing 'shape'")))
            else -> invalidResult("Unknown data shape result '$case'")
        }
    }
}


private fun invalidResult(message: String): Nothing =
    throw DataException(DataProblem(DataProblem.invalidTypeEncoding, message))
