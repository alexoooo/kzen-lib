package tech.kzen.lib.common.model.locate

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectPath


data class ObjectReferenceHost(
    val documentPath: DocumentPath?,
    val objectPath: ObjectPath?,
    val attributePath: AttributePath?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val global = ObjectReferenceHost(null, null, null)


        fun ofLocation(objectLocation: ObjectLocation): ObjectReferenceHost {
            return ObjectReferenceHost(
                    objectLocation.documentPath,
                    objectLocation.objectPath,
                    null
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        if (documentPath == null && objectPath == null && attributePath == null) {
            return "global"
        }

        return (documentPath?.asString() ?: "") +
                ObjectReference.nestingSeparator +
                (objectPath?.asString() ?: "") +
                ObjectReference.nestingSeparator +
                (attributePath?.asString() ?: "")
    }
}