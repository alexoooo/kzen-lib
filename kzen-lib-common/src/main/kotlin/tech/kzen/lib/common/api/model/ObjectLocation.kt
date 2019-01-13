package tech.kzen.lib.common.api.model


data class ObjectLocation(
        val bundlePath: BundlePath,
        val objectPath: ObjectPath
) {
    fun toReference(): ObjectReference {
        return ObjectReference(
                objectPath.name,
                objectPath.nesting,
                bundlePath)
    }


    override fun toString(): String {
        return toReference().asString()
    }
}