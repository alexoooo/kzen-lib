package tech.kzen.lib.common.util.naming


object NextAvailableName {
    fun find(
        base: String,
        separator: String = "",
        range: IntRange = 2 .. 1000,
        isAvailable: (String) -> Boolean
    ): String? {
        if (isAvailable(base)) {
            return base
        }
        for (i in range) {
            val candidate = "$base$separator$i"
            if (isAvailable(candidate)) {
                return candidate
            }
        }
        return null
    }
}
