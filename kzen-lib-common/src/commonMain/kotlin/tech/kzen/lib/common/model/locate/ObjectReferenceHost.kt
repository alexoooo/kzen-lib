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

        val parts = mutableListOf<String>()

        if (documentPath != null) {
            parts.add(documentPath.asString())
        }

        if (objectPath != null) {
            parts.add(objectPath.asString())
        }

        if (attributePath != null) {
            parts.add(attributePath.asString())
        }

        return parts.joinToString("/")
    }
}