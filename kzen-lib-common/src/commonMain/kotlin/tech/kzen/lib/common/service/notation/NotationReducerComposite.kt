package tech.kzen.lib.common.service.notation

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.notation.*
import tech.kzen.lib.common.model.structure.notation.cqrs.*


// Composite-attribute command handlers, dispatched from NotationReducer.applyStructural. Split out of NotationReducer
// (G7a follow-up). Unlike the primitive handlers in the sibling files, these compose several lower-level structural
// commands through StructuralBuffer (top-level in NotationReducer.kt) and emit one aggregate event; they are still
// pure functions over GraphNotation, no reducer state.


internal fun shiftInAttribute(
    state: GraphNotation,
    command: ShiftInAttributeCommand
): NotationTransition {
    val objectNotation = state.coalesce[command.objectLocation]
        ?: throw IllegalArgumentException("Object location not found: $command")

    val containerPath = command.attributePath.parent()
    val containerNotation = objectNotation.get(containerPath) as StructuredAttributeNotation

    val attributeNotation = objectNotation.get(command.attributePath)
        ?: throw IllegalArgumentException("Attribute path not found: $command")

    val builder = StructuralBuffer(state)

    val removedInAttribute = builder
        .apply(RemoveInAttributeCommand(
            command.objectLocation,
            command.attributePath,
            false
        )) as RemovedInAttributeEvent

    val insertCommand = when (containerNotation) {
        is ListAttributeNotation ->
            InsertListItemInAttributeCommand(
                command.objectLocation,
                containerPath,
                command.newPosition,
                attributeNotation)

        is MapAttributeNotation ->
            InsertMapEntryInAttributeCommand(
                command.objectLocation,
                containerPath,
                command.newPosition,
                command.attributePath.nesting.segments.last(),
                attributeNotation,
                false)
    }

    val reinsertedInAttribute = builder
        .apply(insertCommand)
        as InsertedInAttributeEvent

    return NotationTransition(
        ShiftedInAttributeEvent(removedInAttribute, reinsertedInAttribute),
        builder.graphNotation)
}


internal fun addObjectAtAttribute(
    graphNotation: GraphNotation,
    command: AddObjectAtAttributeCommand
): NotationTransition {
    val buffer = StructuralBuffer(graphNotation)

    val objectLocation = command.insertedObjectLocation()

    val objectAdded = buffer
        .apply(AddObjectCommand(
            objectLocation,
            command.positionInDocument,
            command.objectNotation))
        as AddedObjectEvent

    val addendReference = objectLocation.toReference()
        .crop(retainPath = false)

//        val existingValue = graphNotation.getString(
//            command.containingObjectLocation, AttributePath.ofName(command.containingAttribute))

    val insertAtAttributeCommand = UpsertAttributeCommand(
        command.containingObjectLocation,
        command.containingAttribute,
        ScalarAttributeNotation(addendReference.asString()))

    val addedAtAttribute = buffer
        .apply(insertAtAttributeCommand)
        as UpsertedAttributeEvent

    return NotationTransition(
        AddedObjectAtAttributeEvent(objectAdded, addedAtAttribute),
            buffer.graphNotation)
}


internal fun insertObjectInListAttribute(
    graphNotation: GraphNotation,
    command: InsertObjectInListAttributeCommand
): NotationTransition {
    val buffer = StructuralBuffer(graphNotation)

    val objectLocation = command.insertedObjectLocation()

    val objectAdded = buffer
        .apply(AddObjectCommand(
            objectLocation,
            command.positionInDocument,
            command.objectNotation))
        as AddedObjectEvent

    val addendReference = objectLocation.toReference()
        .crop(retainPath = false)

    val insertInAttributeCommand = InsertListItemInAttributeCommand(
        command.containingObjectLocation,
        command.containingList,
        command.indexInList,
        ScalarAttributeNotation(addendReference.asString()))

    val insertedInAttribute = buffer
        .apply(insertInAttributeCommand)
        as InsertedListItemInAttributeEvent

    return NotationTransition(
        InsertedObjectInListAttributeEvent(objectAdded, insertedInAttribute),
        buffer.graphNotation)
}


internal fun removeObjectInAttribute(
    state: GraphNotation,
    command: RemoveObjectInAttributeCommand
): NotationTransition {
    val objectNotation = state.coalesce[command.containingObjectLocation]
        ?: throw IllegalArgumentException("Containing object not found: ${command.containingObjectLocation}")

    val attributeNotation = objectNotation.get(command.attributePath)!!
    val objectReference = ObjectReference.parse(attributeNotation.asString()!!)
    val objectReferenceHost = ObjectReferenceHost.ofLocation(command.containingObjectLocation)
    val objectLocation = state.coalesce.locate(objectReference, objectReferenceHost)

    val buffer = StructuralBuffer(state)

    val removedInAttribute = buffer
        .apply(RemoveInAttributeCommand(
            command.containingObjectLocation,
            command.attributePath,
            false
        )) as RemovedInAttributeEvent

    val removedObject = buffer
        .apply(RemoveObjectCommand(
            objectLocation
        )) as RemovedObjectEvent

    val containingDocumentPath = command.containingObjectLocation.documentPath

    val nestedObjectLocations = buffer
            .graphNotation
            .documents[containingDocumentPath]!!
            .objects
            .notations
            .map
            .keys
            .filter { it.startsWith(objectLocation.objectPath) }
            .toList()

    val removedNestedObjects = nestedObjectLocations
        .map {
            buffer.apply(RemoveObjectCommand(
                ObjectLocation(containingDocumentPath, it)
            )) as RemovedObjectEvent
        }

    return NotationTransition(
        RemovedObjectInAttributeEvent(
            removedInAttribute, removedObject, removedNestedObjects),
        buffer.graphNotation)
}
