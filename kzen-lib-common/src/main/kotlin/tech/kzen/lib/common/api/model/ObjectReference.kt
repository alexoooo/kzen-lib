package tech.kzen.lib.common.api.model


data class ObjectReference(
        val name: ObjectName,
        val nesting: BundleNesting?,
        val path: BundlePath?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val nestingSeparator = "#"

        fun parse(asString: String): ObjectReference {
            val endOfPath = asString.indexOf(nestingSeparator)

            val path: BundlePath?
            val nestingAsString: String

            if (endOfPath == -1) {
                path = null
                nestingAsString = asString
            }
            else {
                path = BundlePath.parse(asString.substring(0, endOfPath))
                nestingAsString = asString.substring(endOfPath + nestingSeparator.length)
            }

            val nameSegment: String = BundleNesting.extractNameSuffix(nestingAsString)

            val nesting: BundleNesting? = BundleNesting.extractSegments(nestingAsString)
                    ?.let { BundleNesting.parse(it) }

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
                    nesting.asString() + BundleNesting.delimiter
                }

        return pathPrefix + nestingInfix + name.value
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return asString()
    }
}

