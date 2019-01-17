package tech.kzen.lib.common.definition

import tech.kzen.lib.common.api.model.AttributeLocation
import tech.kzen.lib.common.api.model.ObjectMap


data class GraphDefinition(
        val objectDefinitions: ObjectMap<ObjectDefinition>
) {
    fun get(attributeLocation: AttributeLocation): AttributeDefinition {
        val objectDefinition = objectDefinitions.get(attributeLocation.objectLocation)
        return objectDefinition.get(attributeLocation.attributePath)
    }


//    fun get(objectPath: ObjectLocation): ObjectDefinition {
//        return objectDefinitions.get(objectPath)
//    }


//    fun get(name: ObjectName): ObjectDefinition {
//        val matches = mutableListOf<ObjectDefinition>()
//        for (e in objectDefinitions.entries) {
//            if (e.key.name == name) {
//                matches.add(e.value)
//            }
//        }
//        check(matches.size == 1) { "Single object required: $matches" }
//
//        return matches[0]
//    }
}