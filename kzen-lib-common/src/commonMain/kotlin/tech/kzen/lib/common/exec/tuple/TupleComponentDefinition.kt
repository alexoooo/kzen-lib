package tech.kzen.lib.common.exec.tuple

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.logic.model.LogicType


data class TupleComponentDefinition(
    val name: TupleComponentName,
    val type: LogicType
) {
    companion object {
        fun ofMain(type: LogicType): TupleComponentDefinition {
            return TupleComponentDefinition(
                TupleComponentName.main, type)
        }


        fun ofDetail(): TupleComponentDefinition {
            return TupleComponentDefinition(
                TupleComponentName.detail,
                LogicType(ExecutionValue.typeMetadata)
            )
        }
    }
}
