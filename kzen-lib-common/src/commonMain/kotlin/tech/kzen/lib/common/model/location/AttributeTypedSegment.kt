package tech.kzen.lib.common.model.location


sealed class AttributeTypedSegment


data class IndexSegment(
    val index: Int
): AttributeTypedSegment()


data class KeySegment(
    val key: String
): AttributeTypedSegment()