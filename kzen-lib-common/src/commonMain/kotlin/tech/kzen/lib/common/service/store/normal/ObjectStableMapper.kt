package tech.kzen.lib.common.service.store.normal

import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentSegment
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.service.store.LocalGraphStore


class ObjectStableMapper: LocalGraphStore.Observer {
    //-----------------------------------------------------------------------------------------------------------------
    private val locationToId = mutableMapOf<ObjectLocation, ObjectStableId>()
    private val idToLocation = mutableMapOf<ObjectStableId, ObjectLocation>()


    //-----------------------------------------------------------------------------------------------------------------
    fun objectStableId(objectLocation: ObjectLocation): ObjectStableId {
        val existingId = locationToId[objectLocation]
        if (existingId != null) {
            return existingId
        }

        val newId = ObjectStableId(objectLocation.asString())
        locationToId[objectLocation] = newId
        idToLocation[newId] = objectLocation
        return newId
    }


    fun objectLocation(objectStableId: ObjectStableId): ObjectLocation {
        return idToLocation[objectStableId]
            ?: throw IllegalArgumentException("Unknown id: $objectStableId")
    }


    fun snapshot(): Map<ObjectStableId, ObjectLocation> {
        return idToLocation.toMap()
    }


    fun seed(snapshot: Map<ObjectStableId, ObjectLocation>) {
        check(idToLocation.isEmpty() && locationToId.isEmpty()) {
            "Mapper must be empty before seed"
        }
        for ((id, location) in snapshot) {
            idToLocation[id] = location
            locationToId[location] = id
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {}


    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        apply(event)
    }


    fun apply(event: NotationEvent) {
        when (event) {
            is SingularNotationEvent ->
                applySingular(event)

            is CompoundNotationEvent ->
                applyCompound(event)
        }
    }


    private fun applyCompound(
        event: CompoundNotationEvent
    ) {
        val appliedWithDependentEvents = applyCompoundWithDependentEvents(event)
        if (appliedWithDependentEvents) {
            return
        }

        for (singularEvent in event.singularEvents) {
            applySingular(singularEvent)
        }
    }


    private fun applyCompoundWithDependentEvents(
        event: CompoundNotationEvent
    ): Boolean {
        if (event is RenamedDocumentRefactorEvent) {
            val affectedObjectLocations = locationToId
                .keys
                .filter { it.documentPath == event.removedUnderOldName.documentPath }

            for (oldLocation in affectedObjectLocations) {
                val id = locationToId[oldLocation]!!
                val newLocation = oldLocation.copy(documentPath = event.createdWithNewName.destination)

                locationToId.remove(oldLocation)
                locationToId[newLocation] = id
                idToLocation[id] = newLocation
            }
            return true
        }

        if (event is RenamedFolderRefactorEvent) {
            // A folder relocation re-nests its whole subtree; remap every tracked id under the old content
            // nesting (otherwise the inner DeletedFolderEvent cascade would destroy the ids).
            val oldFolderPath = event.removedFolder.documentPath
            val newFolderPath = event.createdFolder.documentPath
            val oldContentNesting = oldFolderPath.nesting.plus(DocumentSegment(oldFolderPath.name.value))
            val newContentNesting = newFolderPath.nesting.plus(DocumentSegment(newFolderPath.name.value))

            fun reNest(documentPath: DocumentPath): DocumentPath =
                documentPath.copy(
                    nesting = documentPath.nesting.replacePrefix(oldContentNesting, newContentNesting))

            val affectedObjectLocations = locationToId
                .keys
                .filter { it.documentPath.nesting.startsWith(oldContentNesting) }

            for (oldLocation in affectedObjectLocations) {
                val id = locationToId[oldLocation]!!
                val newLocation = oldLocation.copy(documentPath = reNest(oldLocation.documentPath))

                locationToId.remove(oldLocation)
                locationToId[newLocation] = id
                idToLocation[id] = newLocation
            }
            return true
        }
        return false
    }


    private fun applySingular(
        event: SingularNotationEvent
    ) {
        val id: ObjectStableId?
        val oldLocation: ObjectLocation
        val newLocation: ObjectLocation

        when (event) {
            is RenamedObjectEvent -> {
                oldLocation = event.objectLocation
                id = locationToId[event.objectLocation]
                newLocation = event.newObjectLocation()
            }

            is RenamedNestedObjectEvent -> {
                oldLocation = event.objectLocation
                id = locationToId[event.objectLocation]
                newLocation = event.newObjectLocation()
            }

            is RemovedObjectEvent -> {
                removeLocation(event.objectLocation)
                return
            }

            is DeletedDocumentEvent -> {
                val affected = locationToId.keys.filter { it.documentPath == event.documentPath }
                for (location in affected) {
                    removeLocation(location)
                }
                return
            }

            else ->
                return
        }

        if (id != null) {
            locationToId.remove(oldLocation)
            locationToId[newLocation] = id
            idToLocation[id] = newLocation
        }
    }


    private fun removeLocation(location: ObjectLocation) {
        val id = locationToId.remove(location)
        if (id != null) {
            idToLocation.remove(id)
        }
    }
}