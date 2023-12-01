package tech.kzen.lib.common.model.location

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class ObjectReference(
    val name: ObjectReferenceName,
    val nesting: ObjectNesting,
    val path: DocumentPath?
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        @Suppress("ConstPropertyName")
        const val nestingSeparator = "#"

        val empty = ObjectReference(ObjectReferenceName.empty, ObjectNesting.root, null)


        fun ofRootName(name: ObjectName): ObjectReference {
            return ObjectReference(ObjectReferenceName(name), ObjectNesting.root, null)
        }


        fun tryParse(asString: String): ObjectReference? {
            @Suppress("LiftReturnOrAssignment")
            try {
                return parse(asString)
            }
            catch (t: Throwable) {
                return null
            }
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

            val name =
                if (nameSegment.isEmpty()) {
                    ObjectReferenceName.empty
                }
                else {
                    ObjectReferenceName.of(ObjectName(nameSegment))
                }

            return ObjectReference(name, nesting, path)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun hasPath(): Boolean {
        return path != null
    }


    fun crop(retainPath: Boolean): ObjectReference {
        if (hasPath() == retainPath) {
            return this
        }

        val croppedPath =
            if (retainPath) {
                path
            }
            else {
                null
            }

        return ObjectReference(name, nesting, croppedPath)
    }


    fun isEmpty(): Boolean {
        return path == null && nesting.isRoot() && name.objectName == null
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

        return pathPrefix + nestingInfix + ObjectNesting.encodeDelimiter(name.objectName?.value ?: "")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addDigestibleNullable(name.objectName)
        sink.addDigestible(nesting)
        sink.addDigestibleNullable(path)
    }


    override fun toString(): String {
        if (isEmpty()) {
            return "<empty>"
        }

        return asString()
    }
}

