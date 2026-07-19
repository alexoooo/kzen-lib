package tech.kzen.lib.common.service.notation

import tech.kzen.lib.common.model.document.DocumentSegment
import tech.kzen.lib.common.model.structure.notation.*
import tech.kzen.lib.common.model.structure.notation.cqrs.*


// Document- and folder-level structural command handlers, dispatched from NotationReducer.applyStructural.
// Split out of NotationReducer (G7a); pure functions over GraphNotation, no reducer state.


internal fun createDocument(
    state: GraphNotation,
    command: CreateDocumentCommand
): NotationTransition {
    check(!command.documentPath.folder) {
        "Cannot create a document at a folder path (use CreateFolderCommand): ${command.documentPath}"
    }
    check(!state.documents.map.containsKey(command.documentPath)) {
        "Already exists: ${command.documentPath}"
    }

    val documentNotation = DocumentNotation.ofObjectsWithEmptyOrNullResources(
            command.documentObjectNotation, command.documentPath.directory)

    val nextState = state.withNewDocument(
            command.documentPath, documentNotation)

    val event = CreatedDocumentEvent(
            command.documentPath, command.documentObjectNotation)

    return NotationTransition(event, nextState)
}


internal fun deleteDocument(
    state: GraphNotation,
    command: DeleteDocumentCommand
): NotationTransition {
    check(state.documents.map.containsKey(command.documentPath)) {
        "Does not exist: ${command.documentPath} - ${state.documents.map.keys}"
    }

    val nextState = state.withoutDocument(command.documentPath)

    return NotationTransition(
            DeletedDocumentEvent(command.documentPath),
            nextState)
}


internal fun createFolder(
    state: GraphNotation,
    command: CreateFolderCommand
): NotationTransition {
    check(command.documentPath.folder) {
        "Not a folder path: ${command.documentPath}"
    }
    check(!state.documents.map.containsKey(command.documentPath)) {
        "Already exists: ${command.documentPath}"
    }

    val nextState = state.withNewDocument(
        command.documentPath, DocumentNotation.folder)

    return NotationTransition(
        CreatedFolderEvent(command.documentPath),
        nextState)
}


internal fun deleteFolder(
    state: GraphNotation,
    command: DeleteFolderCommand
): NotationTransition {
    check(command.documentPath.folder) {
        "Not a folder path: ${command.documentPath}"
    }

    // Cascade: remove the folder's own entry plus every document/folder nested under its content nesting
    // (folder "foo" at nesting N holds its contents at N + foo). Every folder has its own entry, so removing
    // the whole subtree here puts the folder directory itself into the store's removed set — the generic
    // deepest-first delete loop then drops the directory with no folder-specific special-casing.
    val contentNesting = command.documentPath.nesting.plus(
        DocumentSegment(command.documentPath.name.value))

    val toRemove = state.documents.map.keys.filter { path ->
        path == command.documentPath || path.nesting.startsWith(contentNesting)
    }

    check(toRemove.isNotEmpty()) {
        "Empty or unknown folder: ${command.documentPath} - ${state.documents.map.keys}"
    }

    var nextState = state
    for (path in toRemove) {
        nextState = nextState.withoutDocument(path)
    }

    return NotationTransition(
        DeletedFolderEvent(command.documentPath),
        nextState)
}


internal fun copyDocument(
    state: GraphNotation,
    command: CopyDocumentCommand
): NotationTransition {
    check(command.sourceDocumentPath in state.documents.map) {
        "Does not exist: ${command.sourceDocumentPath} - ${state.documents.map.keys}"
    }

    val document = state.documents[command.sourceDocumentPath]!!

    val withDocumentNotationCopy = state
            .withNewDocument(command.destinationDocumentPath, document)

    return NotationTransition(
            CopiedDocumentEvent(command.sourceDocumentPath, command.destinationDocumentPath),
            withDocumentNotationCopy)
}


internal fun setDocumentObjects(
    state: GraphNotation,
    command: SetDocumentObjectsCommand
): NotationTransition {
    val documentNotation = state.documents.map[command.documentPath]
    checkNotNull(documentNotation) {
        "Does not exist: ${command.documentPath} - ${state.documents.map.keys}"
    }

    val modifiedDocumentNotation = documentNotation.withObjects(command.documentObjectNotation)

    val nextState = state.withModifiedDocument(
        command.documentPath, modifiedDocumentNotation)

    val event = SetDocumentObjectsEvent(
        command.documentPath, command.documentObjectNotation)

    return NotationTransition(event, nextState)
}
