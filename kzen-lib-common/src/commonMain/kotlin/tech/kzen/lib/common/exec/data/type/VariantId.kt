package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import kotlin.jvm.JvmInline


@JvmInline
value class VariantId(val value: String) {
    init {
        if (value.isEmpty()) {
            throw DataException(DataProblem(
                DataProblem.invalidIdentifier,
                "Variant identifier must not be empty"))
        }
    }

    override fun toString(): String = value
}
