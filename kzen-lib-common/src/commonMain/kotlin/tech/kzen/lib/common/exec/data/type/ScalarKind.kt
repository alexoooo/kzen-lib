package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem


sealed interface ScalarKind {
    data object Boolean: ScalarKind

    data class Integer(
        val bits: Int? = null,
        val signed: kotlin.Boolean = true
    ): ScalarKind {
        init {
            if (bits != null && bits !in setOf(8, 16, 32, 64)) {
                throw DataException(DataProblem(
                    DataProblem.invalidScalar,
                    "Integer width must be one of 8, 16, 32, 64, or null: $bits"))
            }
        }
    }

    data object Decimal: ScalarKind

    data class Floating(val bits: Int = 64): ScalarKind {
        init {
            if (bits !in setOf(32, 64)) {
                throw DataException(DataProblem(
                    DataProblem.invalidScalar,
                    "Floating width must be 32 or 64: $bits"))
            }
        }
    }

    data object Text: ScalarKind
    data object Binary: ScalarKind
    data object Date: ScalarKind
    data object Time: ScalarKind
    data object Instant: ScalarKind
    data object Duration: ScalarKind
    data object Uuid: ScalarKind
}


fun ScalarKind.renderName(): String =
    when (this) {
        ScalarKind.Boolean -> "boolean"
        is ScalarKind.Integer -> "integer:${bits ?: "arbitrary"}:${if (signed) "signed" else "unsigned"}"
        ScalarKind.Decimal -> "decimal"
        is ScalarKind.Floating -> "floating:$bits"
        ScalarKind.Text -> "text"
        ScalarKind.Binary -> "binary"
        ScalarKind.Date -> "date"
        ScalarKind.Time -> "time"
        ScalarKind.Instant -> "instant"
        ScalarKind.Duration -> "duration"
        ScalarKind.Uuid -> "uuid"
    }
