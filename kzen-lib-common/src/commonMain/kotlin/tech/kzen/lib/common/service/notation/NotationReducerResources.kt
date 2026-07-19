package tech.kzen.lib.common.service.notation

import tech.kzen.lib.common.model.structure.notation.*
import tech.kzen.lib.common.model.structure.notation.cqrs.*


// Resource structural command handlers, dispatched from NotationReducer.applyStructural.
// Split out of NotationReducer (G7a); pure functions over GraphNotation, no reducer state.


internal fun addResource(
    state: GraphNotation,
    command: AddResourceCommand
): NotationTransition {
    val documentNotation = state.documents.map[command.resourceLocation.documentPath]

    checkNotNull(documentNotation) {
        "Document '${command.resourceLocation.documentPath}' does not exist"
    }
    checkNotNull(documentNotation.resources) {
        "Document '${command.resourceLocation.documentPath}' does not have resources"
    }
    check(command.resourceLocation.resourcePath !in documentNotation.resources.digests) {
        "Resource '${command.resourceLocation}' already exists"
    }

    val contentDigest = command.resourceContent.digest()

    val modifiedDocumentNotation =
        documentNotation.withNewResource(
            command.resourceLocation.resourcePath,
            contentDigest)

    val nextState = state.withModifiedDocument(
        command.resourceLocation.documentPath, modifiedDocumentNotation)

    return NotationTransition(
        AddedResourceEvent(
            command.resourceLocation,
            contentDigest),
        nextState)
}


internal fun removeResource(
    state: GraphNotation,
    command: RemoveResourceCommand
): NotationTransition {
    val documentNotation = state.documents.map[command.resourceLocation.documentPath]

    checkNotNull(documentNotation) {
        "Document '${command.resourceLocation.documentPath}' does not exist"
    }
    checkNotNull(documentNotation.resources) {
        "Document '${command.resourceLocation.documentPath}' does not have resources"
    }
    check(command.resourceLocation.resourcePath in documentNotation.resources.digests) {
        "Resource '${command.resourceLocation}' does not exists"
    }

    val modifiedDocumentNotation =
        documentNotation.withoutResource(
            command.resourceLocation.resourcePath)

    val nextState = state.withModifiedDocument(
        command.resourceLocation.documentPath, modifiedDocumentNotation)

    return NotationTransition(
        RemovedResourceEvent(
            command.resourceLocation),
        nextState)
}
