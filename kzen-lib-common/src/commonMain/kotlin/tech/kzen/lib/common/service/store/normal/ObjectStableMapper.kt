package tech.kzen.lib.common.service.store.normal

import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.locate.ObjectLocation
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


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {}


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {}


    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
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

            else ->
                return
        }

        if (id != null) {
            locationToId.remove(oldLocation)
            locationToId[newLocation] = id
            idToLocation[id] = newLocation
        }
    }
}