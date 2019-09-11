package tech.kzen.lib.common.model.structure.metadata

import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectLocationMap
import tech.kzen.lib.platform.collect.toPersistentMap


data class GraphMetadata(
        val objectMetadata: ObjectLocationMap<ObjectMetadata>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = GraphMetadata(ObjectLocationMap(
                mapOf<ObjectLocation, ObjectMetadata>().toPersistentMap()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun get(objectLocation: ObjectLocation): ObjectMetadata? {
        return objectMetadata[objectLocation]
    }
}