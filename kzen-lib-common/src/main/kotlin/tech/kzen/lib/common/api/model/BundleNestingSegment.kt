package tech.kzen.lib.common.api.model


data class BundleNestingSegment(
        val name: ObjectName,
        val nesting: AttributeNesting
) {
    fun asString(): String {
        return name.value +
                AttributeNesting.delimiter +
                nesting.asString()
    }
}