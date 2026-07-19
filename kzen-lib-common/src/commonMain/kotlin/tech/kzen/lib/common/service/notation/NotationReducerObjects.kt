package tech.kzen.lib.common.service.notation

import tech.kzen.lib.common.model.structure.notation.*
import tech.kzen.lib.common.model.structure.notation.cqrs.*


// Object-level structural command handlers, dispatched from NotationReducer.applyStructural.
// Split out of NotationReducer (G7a); pure functions over GraphNotation, no reducer state.


internal fun addObject(
    graphNotation: GraphNotation,
    command: AddObjectCommand
): NotationTransition {
    check(command.objectLocation !in graphNotation.coalesce.map) {
        "Object named '${command.objectLocation}' already exists"
    }

    val documentNotation = graphNotation.documents.map[command.objectLocation.documentPath]!!

    val indexInDocument =
        command.indexInDocument.resolve(documentNotation.objects.notations.map.size)

    val modifiedDocumentNotation =
            documentNotation.withNewObject(
                PositionedObjectPath(command.objectLocation.objectPath, indexInDocument),
                command.body)

    val nextState = graphNotation.withModifiedDocument(
            command.objectLocation.documentPath, modifiedDocumentNotation)

    return NotationTransition(
            AddedObjectEvent(
                command.objectLocation,
                indexInDocument,
                command.body),
            nextState)
}


internal fun removeObject(
    state: GraphNotation,
    command: RemoveObjectCommand
): NotationTransition {
    check(command.objectLocation in state.coalesce.map)

    val documentNotation = state.documents.map[command.objectLocation.documentPath]!!

    val modifiedDocumentNotation =
            documentNotation.withoutObject(command.objectLocation.objectPath)

    val nextState = state.withModifiedDocument(
            command.objectLocation.documentPath, modifiedDocumentNotation)

    return NotationTransition(
            RemovedObjectEvent(command.objectLocation),
            nextState)
}


internal fun shiftObject(
    state: GraphNotation,
    command: ShiftObjectCommand
): NotationTransition {
    check(command.objectLocation in state.coalesce.map)

    val documentNotation = state.documents.map[command.objectLocation.documentPath]!!

    val objectNotation = state.coalesce[command.objectLocation]!!

    val removedFromCurrent = documentNotation.withoutObject(command.objectLocation.objectPath)

    val newPositionInDocument =
        command.newPositionInDocument.resolve(documentNotation.objects.notations.map.size)

    val addedToNew = removedFromCurrent.withNewObject(
            PositionedObjectPath(command.objectLocation.objectPath, newPositionInDocument),
            objectNotation)

    val nextState = state.withModifiedDocument(
            command.objectLocation.documentPath, addedToNew)

    return NotationTransition(
            ShiftedObjectEvent(command.objectLocation, newPositionInDocument),
            nextState)
}


internal fun shiftObjectTree(
    state: GraphNotation,
    command: ShiftObjectTreeCommand
): NotationTransition {
    check(command.objectLocation in state.coalesce.map)

    val documentPath = command.objectLocation.documentPath
    val documentNotation = state.documents.map[documentPath]!!
    val rootObjectPath = command.objectLocation.objectPath

    // The subtree: root + every descendant object, captured in current document order.
    val subtree = documentNotation.objects.notations.map.entries
        .filter { it.key == rootObjectPath || it.key.startsWith(rootObjectPath) }
        .map { it.key to it.value }

    var remaining = documentNotation
    for ((objectPath, _) in subtree) {
        remaining = remaining.withoutObject(objectPath)
    }

    val rootPositionInDocument = command.newPositionInDocument.resolve(
        remaining.objects.notations.map.size)

    var rebuilt = remaining
    for ((index, entry) in subtree.withIndex()) {
        rebuilt = rebuilt.withNewObject(
            PositionedObjectPath(entry.first, PositionIndex(rootPositionInDocument.value + index)),
            entry.second)
    }

    val nextState = state.withModifiedDocument(documentPath, rebuilt)

    return NotationTransition(
            ShiftedObjectTreeEvent(command.objectLocation, rootPositionInDocument),
            nextState)
}


internal fun renameObject(
    state: GraphNotation,
    command: RenameObjectCommand
): NotationTransition {
    check(command.objectLocation in state.coalesce.map)

    val documentNotation = state.documents.map[command.objectLocation.documentPath]!!
    val objectNotation = state.coalesce[command.objectLocation]!!
    val objectIndex = documentNotation.indexOf(command.objectLocation.objectPath)

    val removedCurrentName =
            documentNotation.withoutObject(command.objectLocation.objectPath)

    val newObjectPath = command.objectLocation.objectPath.copy(name = command.newName)

    val addedWithNewName = removedCurrentName.withNewObject(
            PositionedObjectPath(newObjectPath, objectIndex),
            objectNotation)

    val nextState = state.withModifiedDocument(
            command.objectLocation.documentPath, addedWithNewName)

    return NotationTransition(
            RenamedObjectEvent(command.objectLocation, command.newName),
            nextState)
}


internal fun renameNestedObject(
    state: GraphNotation,
    command: RenameNestedObjectCommand
): NotationTransition {
    check(command.objectLocation in state.coalesce.map)

    val documentNotation = state.documents.map[command.objectLocation.documentPath]!!
    val objectNotation = state.coalesce[command.objectLocation]!!
    val objectIndex = documentNotation.indexOf(command.objectLocation.objectPath)

    val removedCurrentNesting =
            documentNotation.withoutObject(command.objectLocation.objectPath)

    val newObjectPath = command.objectLocation.objectPath.copy(nesting = command.newObjectNesting)

    val addedWithNewNesting = removedCurrentNesting.withNewObject(
            PositionedObjectPath(newObjectPath, objectIndex),
            objectNotation)

    val nextState = state.withModifiedDocument(
            command.objectLocation.documentPath, addedWithNewNesting)

    return NotationTransition(
            RenamedNestedObjectEvent(
                    command.objectLocation, command.newObjectNesting),
            nextState)
}
