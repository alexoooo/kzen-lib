package tech.kzen.lib.common.notation.model


data class PositionIndex(
        val value: Int
) {
    init {
        check(value >= 0) { "Index must not be negative: $value" }
    }
}