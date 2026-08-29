package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.data.problem.DataProblem


sealed interface VariantSelection {
    data class Selected(val variant: VariantId): VariantSelection
    data class NoMatch(val problem: DataProblem): VariantSelection

    class Ambiguous(candidates: List<VariantId>): VariantSelection {
        val candidates: List<VariantId> = candidates.toList()

        override fun equals(other: Any?): Boolean =
            this === other || other is Ambiguous && candidates == other.candidates

        override fun hashCode(): Int = candidates.hashCode()

        override fun toString(): String = "Ambiguous(candidates=$candidates)"
    }
}
