package tech.kzen.lib.common.model.locate

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.platform.ClassName


data class ObjectLocation(
        val documentPath: DocumentPath,
        val objectPath: ObjectPath
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val className = ClassName(
                "tech.kzen.lib.common.model.locate.ObjectLocation")


        fun parse(asString: String): ObjectLocation {
            val asReference = ObjectReference.parse(asString)
            check(asReference.hasPath()) { "Must be absolute: $asString" }
            return ObjectLocation(
                    asReference.path!!,
                    ObjectPath(asReference.name, asReference.nesting))
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
                objectPath.name,
                objectPath.nesting,
                documentPath)
    }


    fun asString(): String {
        return documentPath.asString() +
                ObjectReference.nestingSeparator +
                objectPath.asString()
//        val nestingInfix = objectPath.asString() + ObjectNesting.delimiter
//
//        return pathPrefix + nestingInfix + ObjectNesting.encodeDelimiter(name.value)
//        return toReference().asString()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return asString()
    }
}