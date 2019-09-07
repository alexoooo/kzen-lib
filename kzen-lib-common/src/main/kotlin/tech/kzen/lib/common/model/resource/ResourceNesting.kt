package tech.kzen.lib.common.model.resource

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class ResourceNesting(
        val directories: List<ResourceDirectory>
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val delimiter = "/"
        val empty = ResourceNesting(listOf())


        fun parse(asString: String): ResourceNesting {
            val parts = asString.split(delimiter)

            val withoutEmptySuffix =
                    if (parts.last().isEmpty()) {
                        parts.subList(0, parts.size - 1)
                    }
                    else {
                        parts
                    }

            val directories = withoutEmptySuffix
                    .map { ResourceDirectory(it) }

            return ResourceNesting(directories)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asString(): String {
        if (directories.isEmpty()) {
            return ""
        }

        return directories.joinToString(delimiter, postfix = delimiter)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(digester: Digest.Builder) {
        digester.addDigestibleList(directories)
    }


    override fun toString(): String {
        return asString()
    }
}