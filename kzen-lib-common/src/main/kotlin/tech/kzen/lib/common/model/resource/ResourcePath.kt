package tech.kzen.lib.common.model.resource

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


/**
 * Path to a resource within a document
 */
data class ResourcePath(
        val resourceName: ResourceName,
        val resourceNesting: ResourceNesting
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun parse(asString: String): ResourcePath {
            val nameIndex = asString.lastIndexOf(ResourceNesting.delimiter)
            if (nameIndex == -1) {
                return ResourcePath(
                        ResourceName(asString),
                        ResourceNesting.empty)
            }

            val nestingPrefix = asString.substring(0, nameIndex)
            val nameSuffix = asString.substring(nameIndex + 1)

            return ResourcePath(
                    ResourceName(nameSuffix),
                    ResourceNesting.parse(nestingPrefix))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asString(): String {
        return asRelativeFile()
    }


    fun asRelativeFile(): String {
        return resourceNesting.asString() + resourceName.value
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(digester: Digest.Builder) {
        digester.addDigestible(resourceName)
        digester.addDigestible(resourceNesting)
    }
}