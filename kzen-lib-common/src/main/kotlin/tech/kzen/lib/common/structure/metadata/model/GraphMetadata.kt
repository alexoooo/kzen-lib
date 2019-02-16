package tech.kzen.lib.common.structure.metadata.model

import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectMap


data class GraphMetadata(
        val objectMetadata: ObjectMap<ObjectMetadata>
) {
    fun get(objectPath: ObjectLocation): ObjectMetadata {
        val metadata = objectMetadata.find(objectPath)

        check(metadata != null) { "Not found: $objectPath" }

        return metadata
    }
}