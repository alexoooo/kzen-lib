package tech.kzen.lib.common.service.notation

import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.*


// The only place notation commands are applied. NotationReducer is a thin dispatch facade: every command group's
// handlers are pure top-level functions in a sibling file — document/folder, object, attribute and resource handlers
// in NotationReducer{Documents,Objects,Attributes,Resources}.kt, the composite-attribute handlers in
// NotationReducerComposite.kt, and the semantic refactor + reference-analysis cluster in NotationReducerRefactor.kt.
// Only applySemantic needs instance state (codeReferenceRewriters, threaded into renameObjectRefactor); the structural
// dispatch is instance-independent, so it and StructuralBuffer are top-level (below), which is what lets the
// composite/refactor handlers compose commands through a buffer without holding a reducer reference.
class NotationReducer(
    // Domain-specific rewriters for object references embedded in free-form code attributes (e.g. kzen-auto's
    // Formula expressions). Consulted on object rename; empty by default so plain notation use needs no wiring.
    private val codeReferenceRewriters: List<CodeReferenceRewriter> = listOf()
) {
    fun applyStructural(
        graphNotation: GraphNotation,
        structuralNotationCommand: StructuralNotationCommand
    ): NotationTransition =
        applyStructuralCommand(graphNotation, structuralNotationCommand)


    fun applySemantic(
        graphDefinitionAttempt: GraphDefinitionAttempt,
        semanticNotationCommand: SemanticNotationCommand
    ): NotationTransition {
        return when (semanticNotationCommand) {
            is RenameObjectRefactorCommand ->
                renameObjectRefactor(
                    graphDefinitionAttempt,
                    semanticNotationCommand.objectLocation,
                    semanticNotationCommand.newName,
                    codeReferenceRewriters)

            is RenameDocumentRefactorCommand ->
                relocateDocumentRefactor(
                    graphDefinitionAttempt,
                    semanticNotationCommand.documentPath,
                    semanticNotationCommand.documentPath.withName(semanticNotationCommand.newName))

            is MoveDocumentRefactorCommand ->
                relocateDocumentRefactor(
                    graphDefinitionAttempt,
                    semanticNotationCommand.documentPath,
                    semanticNotationCommand.documentPath.copy(nesting = semanticNotationCommand.newNesting))

            is RenameFolderRefactorCommand ->
                relocateFolderRefactor(
                    graphDefinitionAttempt,
                    semanticNotationCommand.documentPath,
                    semanticNotationCommand.documentPath.withName(semanticNotationCommand.newName))

            is MoveFolderRefactorCommand ->
                relocateFolderRefactor(
                    graphDefinitionAttempt,
                    semanticNotationCommand.documentPath,
                    semanticNotationCommand.documentPath.copy(nesting = semanticNotationCommand.newNesting))

            is RelocateObjectTreeRefactorCommand ->
                relocateObjectTreeRefactor(
                    graphDefinitionAttempt,
                    semanticNotationCommand.objectLocation,
                    semanticNotationCommand.newObjectNesting,
                    semanticNotationCommand.newPositionInDocument)
        }
    }
}


// Threads a sequence of structural commands through the dispatcher, carrying the evolving GraphNotation. The
// composite-attribute handlers and the semantic refactors use it to build one aggregate event out of primitives.
internal class StructuralBuffer(
    var graphNotation: GraphNotation
) {
    fun apply(
        structuralNotationCommand: StructuralNotationCommand
    ): NotationEvent {
        val transition = applyStructuralCommand(graphNotation, structuralNotationCommand)
        graphNotation = transition.graphNotation
        return transition.notationEvent
    }
}


// Structural command dispatcher. Instance-independent (never consults codeReferenceRewriters), so it is top-level and
// callable from StructuralBuffer and the sibling handler files. The when stays exhaustive (no else).
internal fun applyStructuralCommand(
    graphNotation: GraphNotation,
    structuralNotationCommand: StructuralNotationCommand
): NotationTransition {
    return when (structuralNotationCommand) {
        is CreateDocumentCommand ->
            createDocument(graphNotation, structuralNotationCommand)

        is DeleteDocumentCommand ->
            deleteDocument(graphNotation, structuralNotationCommand)

        is CreateFolderCommand ->
            createFolder(graphNotation, structuralNotationCommand)

        is DeleteFolderCommand ->
            deleteFolder(graphNotation, structuralNotationCommand)

        is CopyDocumentCommand ->
            copyDocument(graphNotation, structuralNotationCommand)

        is SetDocumentObjectsCommand ->
            setDocumentObjects(graphNotation, structuralNotationCommand)


        is AddObjectCommand ->
            addObject(graphNotation, structuralNotationCommand)

        is RemoveObjectCommand ->
            removeObject(graphNotation, structuralNotationCommand)

        is ShiftObjectCommand ->
            shiftObject(graphNotation, structuralNotationCommand)

        is ShiftObjectTreeCommand ->
            shiftObjectTree(graphNotation, structuralNotationCommand)

        is RenameObjectCommand ->
            renameObject(graphNotation, structuralNotationCommand)

        is RenameNestedObjectCommand ->
            renameNestedObject(graphNotation, structuralNotationCommand)


        is UpsertAttributeCommand ->
            upsertAttribute(graphNotation, structuralNotationCommand)

        is UpdateInAttributeCommand ->
            updateInAttribute(graphNotation, structuralNotationCommand)

        is UpdateAllNestingsInAttributeCommand ->
            updateAllNestingsInAttribute(graphNotation, structuralNotationCommand)

        is UpdateAllValuesInAttributeCommand ->
            updateAllValuesInAttribute(graphNotation, structuralNotationCommand)

        is InsertListItemInAttributeCommand ->
            insertListItemInAttribute(graphNotation, structuralNotationCommand)

        is InsertAllListItemsInAttributeCommand ->
            insertAllListItemsInAttribute(graphNotation, structuralNotationCommand)

        is InsertMapEntryInAttributeCommand ->
            insertMapEntryInAttribute(graphNotation, structuralNotationCommand)

        is RemoveInAttributeCommand ->
            removeInAttribute(graphNotation, structuralNotationCommand)

        is RemoveListItemInAttributeCommand ->
            removeListItemInAttribute(graphNotation, structuralNotationCommand)

        is RemoveAllListItemsInAttributeCommand ->
            removeAllListItemsInAttribute(graphNotation, structuralNotationCommand)


        is ShiftInAttributeCommand ->
            shiftInAttribute(graphNotation, structuralNotationCommand)

        is AddObjectAtAttributeCommand ->
            addObjectAtAttribute(graphNotation, structuralNotationCommand)

        is InsertObjectInListAttributeCommand ->
            insertObjectInListAttribute(graphNotation, structuralNotationCommand)

        is RemoveObjectInAttributeCommand ->
            removeObjectInAttribute(graphNotation, structuralNotationCommand)


        is AddResourceCommand ->
            addResource(graphNotation, structuralNotationCommand)

        is RemoveResourceCommand ->
            removeResource(graphNotation, structuralNotationCommand)
    }
}
