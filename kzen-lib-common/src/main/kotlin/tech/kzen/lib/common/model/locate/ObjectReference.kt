package tech.kzen.lib.common.model.locate

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting


data class ObjectReference(
        val name: ObjectName,
        val nesting: ObjectNesting,
        val path: DocumentPath?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val nestingSeparator = "#"


        fun ofRootName(name: ObjectName): ObjectReference {
            return ObjectReference(name, ObjectNesting.root, null)
        }


        fun parse(asString: String): ObjectReference {
            val endOfPath = asString.indexOf(nestingSeparator)

            val path: DocumentPath?
            val nestingAsString: String

            if (endOfPath == -1) {
                path = null
                nestingAsString = asString
            }
            else {
                path = DocumentPath.parse(asString.substring(0, endOfPath))
                nestingAsString = asString.substring(endOfPath + nestingSeparator.length)
            }

            val nameSegment: String = ObjectNesting.extractNameSuffix(nestingAsString)

            val nesting: ObjectNesting = ObjectNesting.extractSegments(nestingAsString)
                    ?.let { ObjectNesting.parse(it) }
                    ?: ObjectNesting.root

            return ObjectReference(ObjectName(nameSegment), nesting, path)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isAbsolute(): Boolean {
        return hasPath()
    }

    fun hasPath(): Boolean {
        return path != null
    }

//    fun hasNesting(): Boolean {
//        return nesting != null
//    }


    fun crop(retainPath: Boolean): ObjectReference {
        if (hasPath() == retainPath) {
            return this
        }

//        val croppedNesting =
//                if (retainNesting) {
//                    nesting
//                }
//                else {
//                    null
//                }

        val croppedPath =
                if (retainPath) {
                    path
                }
                else {
                    null
                }

        return ObjectReference(name, nesting, croppedPath)
    }


    fun asString(): String {
        val pathPrefix =
                if (path == null) {
                    ""
                }
                else {
                    path.asString() + nestingSeparator
                }

        val nestingInfix =
                if (nesting.isRoot()) {
                    ""
                }
                else {
                    nesting.asString() + ObjectNesting.delimiter
                }

        return pathPrefix + nestingInfix + ObjectNesting.encodeDelimiter(name.value)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return asString()
    }
}

