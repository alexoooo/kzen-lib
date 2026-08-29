package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.BinaryHandleExecutionValue
import tech.kzen.lib.common.exec.BooleanExecutionValue
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.NumberExecutionValue
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem


sealed interface DataPathSegment {
    data class Field(val id: FieldId): DataPathSegment

    class Entry(
        val kind: ScalarKind,
        key: ScalarExecutionValue
    ): DataPathSegment {
        val key: ScalarExecutionValue = key.frozenCopy()

        override fun equals(other: Any?): Boolean =
            this === other || other is Entry && kind == other.kind && key == other.key

        override fun hashCode(): Int = 31 * kind.hashCode() + key.hashCode()

        override fun toString(): String = "Entry(kind=$kind, key=$key)"
    }

    data class Element(val index: Int): DataPathSegment {
        init {
            if (index < 0) {
                throw DataException(DataProblem(
                    DataProblem.invalidPath,
                    "Element index must not be negative: $index"))
            }
        }
    }

    data class Variant(val id: VariantId): DataPathSegment

    /** Schema position of every element in a listing. */
    data object ListingElement: DataPathSegment

    /** Schema position of the uniform mapping key. Native metadata is forbidden here. */
    data object MappingKey: DataPathSegment

    /** Schema position of every value in a mapping. */
    data object MappingValue: DataPathSegment
}


private fun ScalarExecutionValue.frozenCopy(): ScalarExecutionValue =
    when (this) {
        is TextExecutionValue -> this
        is BooleanExecutionValue -> this
        is NumberExecutionValue -> this
        is LongExecutionValue -> this
        is BinaryExecutionValue -> BinaryExecutionValue(value.copyOf())
        is BinaryHandleExecutionValue -> this
    }
