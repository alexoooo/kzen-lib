package tech.kzen.lib.common.model.structure.resource

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible
import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.persistentMapOf


data class ResourceListing(
        val digests: PersistentMap<ResourcePath, Digest>
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = ResourceListing(persistentMapOf())


        fun emptyOrNull(directory: Boolean): ResourceListing? {
            return when {
                directory ->
                    empty

                else ->
                    null
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun withNewResource(
            resourcePath: ResourcePath,
            contentDigest: Digest
    ): ResourceListing {
        check(resourcePath !in digests) {
            "Resource already exists: $resourcePath"
        }

        return ResourceListing(
                digests.put(resourcePath, contentDigest))
    }


    fun withoutResource(
            resourcePath: ResourcePath
    ): ResourceListing {
        check(resourcePath in digests) {
            "Resource missing: $resourcePath"
        }

        return ResourceListing(
                digests.remove(resourcePath))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(builder: Digest.Builder) {
        builder.addDigestibleUnorderedMap(digests)
    }
}