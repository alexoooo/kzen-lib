package tech.kzen.lib.common.model.attribute

import tech.kzen.lib.platform.collect.PersistentList


data class AttributeNesting(
        val segments: PersistentList<AttributeSegment>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = AttributeNesting(PersistentList())
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun shift(): AttributeNesting {
        return AttributeNesting(segments.subList(1, segments.size))
    }


    fun push(segment: AttributeSegment): AttributeNesting {
        return AttributeNesting(segments.add(segment))
    }


    fun push(attributeNesting: AttributeNesting): AttributeNesting {
        return AttributeNesting(
                segments.addAll(attributeNesting.segments))
    }


    fun parent(): AttributeNesting {
        return AttributeNesting(segments.subList(0, segments.size - 1))
    }
}