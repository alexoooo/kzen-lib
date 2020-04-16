package tech.kzen.lib.common.model.structure.notation


data class PositionIndex(
        val value: Int
) {
    companion object {
        fun parse(asString: String): PositionIndex {
            return PositionIndex(asString.toInt())
        }
    }


    init {
        check(value >= 0) { "Index must not be negative: $value" }
    }


    fun asString(): String {
        return value.toString()
    }
}