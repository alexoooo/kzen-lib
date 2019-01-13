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
        return nesting.asString() + BundleNesting.delimiter + name.value
    }


    override fun toString(): String {
        return asString()
    }
}