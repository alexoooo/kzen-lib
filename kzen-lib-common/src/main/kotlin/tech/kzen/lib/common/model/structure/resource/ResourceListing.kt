package tech.kzen.lib.common.model.structure.resource

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible
import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.persistentMapOf


data class ResourceListing(
        val values: PersistentMap<ResourcePath, Digest>
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = ResourceListing(persistentMapOf())
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun withNewResource(
            resourcePath: ResourcePath,
            contentDigest: Digest
    ): ResourceListing {
        check(resourcePath !in values) {
            "Resource already exists: $resourcePath"
        }

        return ResourceListing(
                values.put(resourcePath, contentDigest))
    }


    fun withoutResource(
            resourcePath: ResourcePath
    ): ResourceListing {
        check(resourcePath in values) {
            "Resource missing: $resourcePath"
        }

        return ResourceListing(
                values.remove(resourcePath))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(builder: Digest.Builder) {
        builder.addDigestibleUnorderedMap(values)
    }
}