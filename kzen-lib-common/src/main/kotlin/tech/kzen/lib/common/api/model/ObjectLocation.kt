package tech.kzen.lib.common.api.model


data class ObjectLocation(
        val bundlePath: BundlePath,
        val objectPath: ObjectPath
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun parse(asString: String): ObjectLocation {
            val asReference = ObjectReference.parse(asString)
            check(asReference.isAbsolute()) { "Must be absolute: $asString" }
            return ObjectLocation(
                    asReference.path!!,
                    ObjectPath(
                            asReference.name,
                            asReference.nesting!!
                    ))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toReference(): ObjectReference {
        return ObjectReference(
                objectPath.name,
                objectPath.nesting,
                bundlePath)
    }


    fun asString(): String {
        return toReference().asString()
    }


    override fun toString(): String {
        return asString()
    }
}