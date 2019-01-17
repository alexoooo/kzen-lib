package tech.kzen.lib.common.api.model


data class BundleNestingSegment(
        val name: ObjectName,
        val nesting: AttributePath
) {
    fun asString(): String {
        return name.value +
                AttributePath.delimiter +
                nesting.asString()
    }
}