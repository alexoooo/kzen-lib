package tech.kzen.lib.common.api.model


data class BundleNestingSegment(
        val objectName: ObjectName,
        val attributePath: AttributePath
) {
    companion object {
        fun parse(asString: String): BundleNestingSegment {
            val nameDelimiter = AttributePath.indexOfDelimiter(asString)
            check(nameDelimiter != -1)

            val encodedName = asString.substring(0, nameDelimiter)
            val attributePathSuffix = asString.substring(nameDelimiter + 1)

            return BundleNestingSegment(
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