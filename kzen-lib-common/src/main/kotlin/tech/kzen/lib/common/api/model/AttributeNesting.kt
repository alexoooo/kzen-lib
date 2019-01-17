package tech.kzen.lib.common.api.model



data class AttributeNesting(
        val segments: List<AttributeSegment>
) {
    companion object {
        val empty = AttributeNesting(listOf())
    }


    fun shift(): AttributeNesting {
        return AttributeNesting(segments.subList(1, segments.size))
    }


    fun push(segment: AttributeSegment): AttributeNesting {
        return AttributeNesting(segments.plus(segment))
    }
}