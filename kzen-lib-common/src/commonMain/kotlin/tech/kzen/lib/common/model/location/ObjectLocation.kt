package tech.kzen.lib.common.model.location

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible
import tech.kzen.lib.platform.ClassName


data class ObjectLocation(
    val documentPath: DocumentPath,
    val objectPath: ObjectPath
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val className = ClassName(
                "tech.kzen.lib.common.model.locate.ObjectLocation")


        fun parse(asString: String): ObjectLocation {
            val asReference = ObjectReference.parse(asString)
            require(asReference.hasPath()) { "Must be absolute: $asString" }

            val objectName = asReference.name.objectName
            require(objectName != null) { "Object name missing: $asString" }

            return ObjectLocation(
                    asReference.path!!,
                    ObjectPath(objectName, asReference.nesting))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun parent(): ObjectLocation? {
        val parentObjectPath = objectPath.nesting.parent()
                ?: return null

        return ObjectLocation(documentPath, parentObjectPath)
    }


    fun startsWith(objectLocation: ObjectLocation): Boolean {
        if (documentPath != objectLocation.documentPath) {
            return false
        }

        return objectPath.startsWith(objectLocation.objectPath)
    }


    fun toReference(): ObjectReference {
        return ObjectReference(
            ObjectReferenceName.of(objectPath.name),
            objectPath.nesting,
            documentPath)
    }


    fun asString(): String {
        return documentPath.asString() +
            ObjectReference.nestingSeparator +
            objectPath.asString()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addDigestible(documentPath)
        sink.addDigestible(objectPath)
    }


    override fun toString(): String {
        return asString()
    }
}