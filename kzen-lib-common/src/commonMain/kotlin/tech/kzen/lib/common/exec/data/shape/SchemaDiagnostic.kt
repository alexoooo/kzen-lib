package tech.kzen.lib.common.exec.data.shape

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem


data class SchemaDiagnostic(
    val severity: DiagnosticSeverity,
    val code: String,
    val message: String,
    val location: String? = null
) {
    init {
        if (code.isBlank()) {
            throw DataException(DataProblem(
                DataProblem.invalidContract,
                "Schema diagnostic code must not be blank"))
        }
        if (message.isBlank()) {
            throw DataException(DataProblem(
                DataProblem.invalidContract,
                "Schema diagnostic message must not be blank"))
        }
    }
}
