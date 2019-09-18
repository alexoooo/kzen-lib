package tech.kzen.lib.common.model.definition

import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.locate.AttributeLocation
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectLocationMap
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.platform.collect.PersistentMap


data class GraphDefinition(
        val objectDefinitions: ObjectLocationMap<ObjectDefinition>,
        val graphStructure: GraphStructure
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = GraphDefinition(
                ObjectLocationMap(PersistentMap()),
                GraphStructure.empty)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun get(attributeLocation: AttributeLocation): AttributeDefinition? {
        return objectDefinitions[attributeLocation.objectLocation]
                ?.get(attributeLocation.attributePath)
    }


    operator fun get(objectPath: ObjectLocation): ObjectDefinition? {
        return objectDefinitions[objectPath]
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun filterDefinitions(allowed: Set<DocumentNesting>): GraphDefinition {
        return GraphDefinition(
                objectDefinitions.filter(allowed),
                graphStructure)
    }


    fun filterDefinitions(predicate: (Pair<ObjectLocation, ObjectDefinition>) -> Boolean): GraphDefinition {
        return GraphDefinition(
                objectDefinitions.filter(predicate),
                graphStructure)
    }


    fun put(objectLocation: ObjectLocation, objectDefinition: ObjectDefinition): GraphDefinition {
        return GraphDefinition(
                objectDefinitions.put(objectLocation, objectDefinition),
                graphStructure)
    }
}