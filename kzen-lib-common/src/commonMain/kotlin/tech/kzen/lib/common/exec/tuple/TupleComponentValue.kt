package tech.kzen.lib.common.exec.tuple

import tech.kzen.lib.common.exec.ExecutionValue


data class TupleComponentValue(
    val name: TupleComponentName,
    val value: Any?
) {
    companion object {
        fun ofMain(value: Any?): TupleComponentValue {
            return TupleComponentValue(
                TupleComponentName.main, value)
        }


        fun ofDetail(value: ExecutionValue): TupleComponentValue {
            return TupleComponentValue(
                TupleComponentName.detail, value)
        }
    }
}
