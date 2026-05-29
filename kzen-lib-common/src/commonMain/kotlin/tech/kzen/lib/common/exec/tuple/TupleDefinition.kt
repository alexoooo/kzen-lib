package tech.kzen.lib.common.exec.tuple

import tech.kzen.lib.common.exec.logic.model.LogicType


data class TupleDefinition(
    val components: List<TupleComponentDefinition>
) {
    companion object {
        val empty = TupleDefinition(listOf())


        fun ofMain(type: LogicType): TupleDefinition {
            return TupleDefinition(listOf(
                TupleComponentDefinition.ofMain(type)
            ))
        }


        fun ofVoidWithDetail(): TupleDefinition {
            return TupleDefinition(listOf(
                TupleComponentDefinition.ofDetail()
            ))
        }
    }


    fun find(name: TupleComponentName): LogicType? {
        return components
            .find { it.name == name }
            ?.type
    }
}
