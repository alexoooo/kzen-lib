package tech.kzen.lib.common.model.location

import tech.kzen.lib.common.model.obj.ObjectName


data class ObjectReferenceName(
    val objectName: ObjectName?
) {
    companion object {
        val empty = ObjectReferenceName(null)

        fun of(objectName: ObjectName): ObjectReferenceName {
            return ObjectReferenceName(objectName)
        }
    }
}