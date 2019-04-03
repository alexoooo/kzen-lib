package tech.kzen.lib.common.model.obj

import tech.kzen.lib.common.model.attribute.AttributePath


data class ObjectNestingSegment(
        val objectName: ObjectName,
        val attributePath: AttributePath
) {
    companion object {
        fun parse(asString: String): ObjectNestingSegment {
            val nameDelimiter = AttributePath.indexOfDelimiter(asString)
            check(nameDelimiter != -1)

            val encodedName = asString.substring(0, nameDelimiter)
            val attributePathSuffix = asString.substring(nameDelimiter + 1)

            return ObjectNestingSegment(
                    ObjectName(AttributePath.decodeDelimiter(encodedName)),
                    AttributePath.parse(attributePathSuffix))
        }
    }


    fun asString(): String {
        return objectName.value +
                AttributePath.delimiter +
                attributePath.asString()
    }
}