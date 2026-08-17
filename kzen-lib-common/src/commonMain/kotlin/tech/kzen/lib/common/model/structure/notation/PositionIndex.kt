package tech.kzen.lib.common.model.structure.notation


data class PositionIndex(
        val value: Int
) {
    companion object {
        val zero = PositionIndex(0)
    }


    init {
        check(value >= 0) { "Index must not be negative: $value" }
    }


    fun asString(): String {
        return value.toString()
    }
}