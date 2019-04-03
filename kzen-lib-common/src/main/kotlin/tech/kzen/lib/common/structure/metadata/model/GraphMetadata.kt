package tech.kzen.lib.common.structure.metadata.model

import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectLocationMap


data class GraphMetadata(
        val objectMetadata: ObjectLocationMap<ObjectMetadata>
) {
    fun get(objectLocation: ObjectLocation): ObjectMetadata {
        val metadata = objectMetadata.find(objectLocation)

        check(metadata != null) { "Not found: $objectLocation" }

        return metadata
    }
}