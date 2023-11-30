package tech.kzen.lib.common.model.definition

import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectLocationMap
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.service.context.GraphDefiner

/**
 * objectDefinitions could be subset of graphStructure (e.g. successful),
 *  where graphStructure would be the entire graph (not just what is defined)
 */
data class GraphDefinition(
    val objectDefinitions: ObjectLocationMap<ObjectDefinition>,
    val graphStructure: GraphStructure
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = GraphDefinition(
                ObjectLocationMap.empty(),
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
                objectDefinitions.filterDocumentNestings(allowed),
                graphStructure)
    }


    fun filterDefinitions(predicate: (Pair<ObjectLocation, ObjectDefinition>) -> Boolean): GraphDefinition {
        return GraphDefinition(
                objectDefinitions.filterBy(predicate),
                graphStructure)
    }


    fun filterTransitive(objectLocations: Collection<ObjectLocation>): GraphDefinition {
        for (objectLocation in objectLocations) {
            require(objectLocation in objectDefinitions) {
                "Missing: $objectLocation"
            }
        }

        val closed = mutableSetOf<ObjectLocation>()

        var open = mutableSetOf<ObjectLocation>()
        var nextOpen = mutableSetOf<ObjectLocation>()

        open.addAll(objectLocations)

        while (open.isNotEmpty()) {
            for (openObjectLocation in open) {
                val host = ObjectReferenceHost.ofLocation(openObjectLocation)
                val openObjectDefinition = objectDefinitions[openObjectLocation]!!
                for (objectDefinitionReference in openObjectDefinition.references()) {
                    val objectReference = objectDefinitionReference.objectReference
                    if (GraphDefiner.isBootstrap(objectReference)) {
                        continue
                    }

                    val location = objectDefinitions.locateOptional(objectReference, host)
                        ?: throw IllegalArgumentException(
                            "Missing $objectReference in $openObjectLocation from $objectLocations")

                    if (location !in closed && location !in open) {
                        nextOpen.add(location)
                    }
                }
            }

            closed.addAll(open)
            open.clear()

            val openSwap = open
            open = nextOpen
            nextOpen = openSwap
        }

        return GraphDefinition(
            objectDefinitions.filterObjectLocations(closed),
            graphStructure)
    }


    fun filterTransitive(objectLocation: ObjectLocation): GraphDefinition {
        return filterTransitive(listOf(objectLocation))
    }


    fun filterTransitive(documentPath: DocumentPath): GraphDefinition {
        val documentObjectLocations = objectDefinitions
            .values
            .keys
            .filter { it.documentPath == documentPath }

        return filterTransitive(documentObjectLocations)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun put(objectLocation: ObjectLocation, objectDefinition: ObjectDefinition): GraphDefinition {
        return GraphDefinition(
                objectDefinitions.put(objectLocation, objectDefinition),
                graphStructure)
    }
}