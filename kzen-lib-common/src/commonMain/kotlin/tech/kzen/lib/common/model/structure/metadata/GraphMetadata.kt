package tech.kzen.lib.common.model.structure.metadata

import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectLocationMap
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible
import tech.kzen.lib.platform.collect.toPersistentMap


data class GraphMetadata(
    val objectMetadata: ObjectLocationMap<ObjectMetadata>
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = GraphMetadata(ObjectLocationMap(
                mapOf<ObjectLocation, ObjectMetadata>().toPersistentMap()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun get(objectLocation: ObjectLocation): ObjectMetadata? {
        return objectMetadata[objectLocation]
    }


    fun filter(allowed: Set<DocumentNesting>): GraphMetadata {
        return GraphMetadata(
                objectMetadata.filterDocumentNestings(allowed))
    }


    override fun digest(sink: Digest.Sink) {
        sink.addDigestibleOrderedMap(objectMetadata.map)
    }
}