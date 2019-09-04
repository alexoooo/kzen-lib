package tech.kzen.lib.common.context.definition

import tech.kzen.lib.common.model.locate.AttributeLocation
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectLocationMap


data class GraphDefinition(
        val objectDefinitions: ObjectLocationMap<ObjectDefinition>
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun get(attributeLocation: AttributeLocation): AttributeDefinition? {
        return objectDefinitions[attributeLocation.objectLocation]
                ?.get(attributeLocation.attributePath)
    }


    operator fun get(objectPath: ObjectLocation): ObjectDefinition? {
        return objectDefinitions[objectPath]
    }



    //-----------------------------------------------------------------------------------------------------------------
    fun filter(predicate: (Pair<ObjectLocation, ObjectDefinition>) -> Boolean): GraphDefinition {
        return GraphDefinition(objectDefinitions.filter(predicate))
    }


    fun put(objectLocation: ObjectLocation, objectDefinition: ObjectDefinition): GraphDefinition {
        return GraphDefinition(objectDefinitions.put(objectLocation, objectDefinition))
    }
}