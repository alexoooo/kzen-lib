package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem


/**
 * A value restriction declared at a [DataContract] path. It narrows what `DataValueAlgebra.validate` accepts but
 * never how a value is read, so type assignability and join ignore it and dropping one leaves a weaker but
 * truthful contract.
 */
sealed interface DataConstraint {
    companion object {
        private const val kindKey = "kind"

        fun ofExecutionValue(executionValue: ExecutionValue): DataConstraint {
            val map = executionValue as? MapExecutionValue
                ?: invalidEncoding("Data constraint must be a map")
            return when (val kind = (map.values[kindKey] as? TextExecutionValue)?.value) {
                SymbolSet.kind -> SymbolSet.decode(map)
                else -> invalidEncoding("Unknown data constraint kind '$kind'")
            }
        }
    }


    /** A path carries at most one constraint of each kind. */
    val kind: String

    fun appliesTo(type: DataType): Boolean

    /** Why [value] (present and non-null) breaks this constraint, or null when it conforms. */
    fun violation(value: ScalarExecutionValue): String?

    fun asExecutionValue(): MapExecutionValue


    /**
     * The value is one of [symbols], in declared (presentation) order. Symbols are canonical scalar text, so the
     * encoding already fits any text-encoded scalar kind; only text positions accept the set today.
     */
    class SymbolSet(symbols: List<String>): DataConstraint {
        companion object {
            const val kind = "symbol-set"
            private const val symbolsKey = "symbols"

            internal fun decode(map: MapExecutionValue): SymbolSet {
                val symbols = map.values[symbolsKey] as? ListExecutionValue
                    ?: invalidEncoding("Symbol set is missing its '$symbolsKey' list")
                return SymbolSet(symbols.values.map {
                    (it as? TextExecutionValue)?.value ?: invalidEncoding("Symbol must be text")
                })
            }
        }

        val symbols: List<String> = symbols.toList()
        private val symbolSet: Set<String> = this.symbols.toSet()

        init {
            if (this.symbols.isEmpty()) {
                throw DataException(DataProblem(DataProblem.invalidConstraint, "Symbol set must not be empty"))
            }
            if (symbolSet.size != this.symbols.size) {
                val duplicates = this.symbols.groupBy { it }.filterValues { it.size > 1 }.keys
                throw DataException(DataProblem(
                    DataProblem.invalidConstraint, "Symbol set has duplicate symbols: $duplicates"))
            }
        }

        override val kind: String get() = Companion.kind

        override fun appliesTo(type: DataType): Boolean =
            type is DataType.Scalar && type.kind == ScalarKind.Text

        override fun violation(value: ScalarExecutionValue): String? {
            val text = (value as? TextExecutionValue)?.value
            return if (text in symbolSet) null
                else "Value $value is not one of $symbols"
        }

        override fun asExecutionValue(): MapExecutionValue =
            MapExecutionValue(mapOf(
                kindKey to TextExecutionValue(kind),
                symbolsKey to ListExecutionValue(symbols.map { TextExecutionValue(it) })))

        override fun equals(other: Any?): Boolean =
            this === other || other is SymbolSet && symbols == other.symbols

        override fun hashCode(): Int = symbols.hashCode()

        override fun toString(): String = "SymbolSet($symbols)"
    }
}


private fun invalidEncoding(message: String): Nothing =
    throw DataException(DataProblem(DataProblem.invalidTypeEncoding, message))
