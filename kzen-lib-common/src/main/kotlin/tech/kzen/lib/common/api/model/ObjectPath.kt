package tech.kzen.lib.common.api.model


data class ObjectPath(
        val name: ObjectName,
        val nesting: BundleNesting
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun parse(asString: String): ObjectPath {
            val nameSuffix = BundleNesting.extractNameSuffix(asString)
            val segmentsAsString = BundleNesting.extractSegments(asString)

            val name = ObjectName(nameSuffix)
            val nesting =
                    if (segmentsAsString == null) {
                        BundleNesting.root
                    }
                    else {
                        BundleNesting.parse(segmentsAsString)
                    }

            return ObjectPath(name, nesting)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asString(): String {
        if (nesting.segments.isEmpty()) {
            return name.value
        }
        return nesting.asString() + BundleNesting.delimiter + name.value
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun nest(attributePath: AttributePath, nestedName: ObjectName): ObjectPath {
        val nestSegment = nesting.append(BundleNestingSegment(name, attributePath))
        return ObjectPath(nestedName, nestSegment)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return asString()
    }
}