package tech.kzen.lib.common.model.resource

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class ResourceListing(
        val values: Map<ResourcePath, ResourceInfo>
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = ResourceListing(mapOf())
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(digester: Digest.Builder) {
        digester.addDigestibleUnorderedMap(values)
    }
}