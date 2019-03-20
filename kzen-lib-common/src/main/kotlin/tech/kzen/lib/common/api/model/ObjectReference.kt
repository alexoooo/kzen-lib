package tech.kzen.lib.common.api.model


data class ObjectReference(
        val name: ObjectName,
        val nesting: DocumentNesting?,
        val path: DocumentPath?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val nestingSeparator = "#"


        fun ofName(name: ObjectName): ObjectReference {
            return ObjectReference(name, null, null)
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

            val nameSegment: String = DocumentNesting.extractNameSuffix(nestingAsString)

            val nesting: DocumentNesting? = DocumentNesting.extractSegments(nestingAsString)
                    ?.let { DocumentNesting.parse(it) }

            return ObjectReference(ObjectName(nameSegment), nesting, path)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isAbsolute(): Boolean {
        return hasNesting() &&
                hasPath()
    }

    fun hasPath(): Boolean {
        return path != null
    }

    fun hasNesting(): Boolean {
        return nesting != null
    }


    fun crop(retainNesting: Boolean, retainPath: Boolean): ObjectReference {
        if (hasNesting() == retainNesting &&
                hasPath() == retainPath) {
            return this
        }

        val croppedNesting =
                if (retainNesting) {
                    nesting
                }
                else {
                    null
                }

        val croppedPath =
                if (retainPath) {
                    path
                }
                else {
                    null
                }


        return ObjectReference(name, croppedNesting, croppedPath)
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
                if (nesting == null) {
                    ""
                }
                else {
                    nesting.asString() + DocumentNesting.delimiter
                }

        return pathPrefix + nestingInfix + name.value
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return asString()
    }
}

