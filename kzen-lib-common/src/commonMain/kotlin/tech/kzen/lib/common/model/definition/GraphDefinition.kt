package tech.kzen.lib.common.model.definition

import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectLocationMap
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.util.digest.Digest

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


    fun transitiveClosure(objectLocations: Collection<ObjectLocation>): Set<ObjectLocation> {
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

        return closed
    }


    fun filterTransitive(objectLocations: Collection<ObjectLocation>): GraphDefinition {
        return GraphDefinition(
            objectDefinitions.filterObjectLocations(transitiveClosure(objectLocations)),
            graphStructure)
    }


    fun filterTransitive(objectLocation: ObjectLocation): GraphDefinition {
        return filterTransitive(listOf(objectLocation))
    }


    fun filterTransitive(documentPath: DocumentPath): GraphDefinition {
        return filterTransitive(documentObjectLocations(documentPath))
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Content digest of the transitive closure's source notation: an ordered combine (sorted by
     *  location string) over each closure member's location and [ObjectNotation] digest, pulled from
     *  the coalesced graph notation. Covers the notation the definitions were derived from, NOT the
     *  definitions themselves (definitions can embed definer-allocated runtime scaffolding with
     *  identity equality) — same source notation implies same compiled behaviour, so digest equality
     *  answers "would recompiling this closure change anything?"
     */
    fun transitiveDigest(objectLocations: Collection<ObjectLocation>): Digest {
        return notationDigest(transitiveClosure(objectLocations))
    }


    /**
     * Document-scoped digest, covering everything document-level semantics can depend on:
     *  the document's notated members IN DOCUMENT ORDER (order is semantic — Job channel derivation
     *  and Script steps are position-driven, while the content combine below is deliberately
     *  order-independent), then the content combine ([notationDigest]) over the defined members'
     *  closure WIDENED by every notated member, defined or not. On a pruned (undefined-by-design)
     *  member — e.g. a Job Worker, whose blank channel ports drop it from a transitive-successful
     *  definition — the closure alone is blind to edits, which is exactly what a validation cache or
     *  live-edit signal keyed on this digest must see. A pruned member contributes only its own
     *  notation (its references cannot be walked without a definition); its archetype chain is
     *  classpath notation, static per process.
     */
    fun transitiveDigest(documentPath: DocumentPath): Digest {
        val notatedMembers = graphStructure.graphNotation.documents[documentPath]
            ?.objects?.notations?.map?.keys
            ?.map { ObjectLocation(documentPath, it) }
            ?: listOf()
        val definedMembers = notatedMembers.filter { it in objectDefinitions }
        val widened = transitiveClosure(definedMembers) + notatedMembers

        return Digest.build {
            for (location in notatedMembers) {
                addDigestible(location)
            }
            addDigestible(notationDigest(widened))
        }
    }


    private fun notationDigest(objectLocations: Set<ObjectLocation>): Digest {
        val coalesce = graphStructure.graphNotation.coalesce

        return Digest.build {
            for (location in objectLocations.sortedBy { it.asString() }) {
                addDigestible(location)
                addDigestibleNullable(coalesce[location])
            }
        }
    }


    private fun documentObjectLocations(documentPath: DocumentPath): List<ObjectLocation> {
        return objectDefinitions
            .map
            .keys
            .filter { it.documentPath == documentPath }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun put(objectLocation: ObjectLocation, objectDefinition: ObjectDefinition): GraphDefinition {
        return GraphDefinition(
                objectDefinitions.put(objectLocation, objectDefinition),
                graphStructure)
    }
}