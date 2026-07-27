package tech.kzen.lib.common.service.notation

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.definition.ReferenceAttributeDefinition
import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentSegment
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectNestingSegment
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.*
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.platform.collect.toPersistentList


// Semantic refactor command handlers + the reference-analysis they share, dispatched from NotationReducer.applySemantic.
// Split out of NotationReducer (G7a follow-up). Pure functions over GraphDefinitionAttempt except renameObjectRefactor,
// which takes the reducer's codeReferenceRewriters as a parameter (threaded in by applySemantic). Because the whole
// cluster moved together, only the four dispatched entry points are module-visible (internal) — every reference-analysis
// helper stays file-private, and the compound events are still built through StructuralBuffer (NotationReducer.kt).


internal fun renameObjectRefactor(
    graphDefinitionAttempt: GraphDefinitionAttempt,
    objectLocation: ObjectLocation,
    newName: ObjectName,
    codeReferenceRewriters: List<CodeReferenceRewriter>
): NotationTransition {
    val graphNotation = graphDefinitionAttempt.graphStructure.graphNotation
    check(objectLocation in graphNotation.coalesce.map)

    val buffer = StructuralBuffer(graphNotation)

    val nestedObjectLocations = graphNotation
        .documents[objectLocation.documentPath]!!
        .objects
        .notations
        .map
        .keys
        .filter { it.startsWith(objectLocation.objectPath) }
        .associateWith { renamedNestedObjectPath(objectLocation, newName, it) }

    val nestedObjects = nestedObjectLocations.map {
        nestedRenameObjectRefactor(
            ObjectLocation(objectLocation.documentPath, it.key),
            it.value.nesting,
            buffer,
            graphDefinitionAttempt
        )
    }

    val newObjectPath = objectLocation.objectPath.copy(name = newName)
    val newObjectLocation = objectLocation.copy(objectPath = newObjectPath)

    val adjustedReferenceCommands = adjustReferenceCommands(
        objectLocation, newObjectLocation, graphDefinitionAttempt)

    // Embedded code references (e.g. a Formula naming this step by its variable identifier) are rewritten in
    // place by the injected domain rewriters and applied within this same refactor — see CodeReferenceRewriter.
    val codeReferenceCommands = codeReferenceRewriters.flatMap {
        it.renameObjectReferences(objectLocation, newObjectLocation, graphDefinitionAttempt)
    }

    val adjustedReferenceEvents = (adjustedReferenceCommands + codeReferenceCommands)
        .map { buffer.apply(it) as UpdatedInAttributeEvent }
        .toList()

    val renamedObject = buffer
        .apply(RenameObjectCommand(objectLocation, newName))
        as RenamedObjectEvent

    return NotationTransition(
        RenamedObjectRefactorEvent(
            renamedObject,
            adjustedReferenceEvents,
            nestedObjects),
        buffer.graphNotation)
}


internal fun relocateObjectTreeRefactor(
    graphDefinitionAttempt: GraphDefinitionAttempt,
    objectLocation: ObjectLocation,
    newObjectNesting: ObjectNesting,
    newPositionInDocument: PositionRelation
): NotationTransition {
    val graphNotation = graphDefinitionAttempt.graphStructure.graphNotation
    check(objectLocation in graphNotation.coalesce.map)

    val documentPath = objectLocation.documentPath
    val oldRootPath = objectLocation.objectPath
    val newRootPath = oldRootPath.copy(nesting = newObjectNesting)

    // Reject re-parenting an object into its own subtree (e.g. an If into its own Then branch);
    // startsWith catches any descendant destination, the equality check the no-op case.
    require(newRootPath != oldRootPath && !newRootPath.startsWith(oldRootPath)) {
        "Cannot relocate an object into its own subtree: $oldRootPath -> $newRootPath"
    }

    val oldNestingSize = oldRootPath.nesting.segments.size

    // Root + every descendant, in current document order.
    val subtreePaths = graphNotation
        .documents[documentPath]!!
        .objects
        .notations
        .map
        .keys
        .filter { it == oldRootPath || it.startsWith(oldRootPath) }
        .toList()

    val buffer = StructuralBuffer(graphNotation)

    // Re-nest each subtree object by swapping its old root-nesting prefix for the new one (segments past
    // the prefix reference the root + inner containers by name, which are unchanged). Reuses the refactor
    // helper, so references into each object are rewritten as it moves. Re-nesting one object never moves
    // another's path, so each old location is still present when its command runs (same invariant as
    // renameObjectRefactor).
    val nestedObjectRenames = subtreePaths.map { path ->
        val newNesting = ObjectNesting(
            (newObjectNesting.segments +
                path.nesting.segments.subList(oldNestingSize, path.nesting.segments.size)
            ).toPersistentList())

        nestedRenameObjectRefactor(
            ObjectLocation(documentPath, path),
            newNesting,
            buffer,
            graphDefinitionAttempt)
    }

    // Reposition the now-re-nested subtree as a contiguous block (resolved against the doc minus subtree).
    val shiftedObjectTree = buffer
        .apply(ShiftObjectTreeCommand(
            ObjectLocation(documentPath, newRootPath), newPositionInDocument))
        as ShiftedObjectTreeEvent

    return NotationTransition(
        RelocatedObjectTreeRefactorEvent(nestedObjectRenames, shiftedObjectTree),
        buffer.graphNotation)
}


private fun renamedNestedObjectPath(
    containerObjectLocation: ObjectLocation,
    newName: ObjectName,
    nestedObjectPath: ObjectPath
): ObjectPath {
    val segments = nestedObjectPath.nesting.segments

    val prefix =
        segments.subList(0, containerObjectLocation.objectPath.nesting.segments.size)

    val containingSegment =
        segments[containerObjectLocation.objectPath.nesting.segments.size]

    val renamedSegment = ObjectNestingSegment(
        newName, containingSegment.attributePath)

    // The containing segment sits at index prefix.size; everything after it (the descendant's own deeper
    // nesting) must be preserved verbatim. Starting the suffix at prefix.size + 2 dropped the segment at
    // prefix.size + 1, re-parenting grandchildren up to the renamed container's branch.
    val suffix = segments.subList(prefix.size + 1, segments.size)

    return nestedObjectPath.copy(
        nesting = ObjectNesting((
            prefix + listOf(renamedSegment) + suffix
        ).toPersistentList()))
}


private fun nestedRenameObjectRefactor(
    objectLocation: ObjectLocation,
    newObjectNesting: ObjectNesting,
    buffer: StructuralBuffer,
    graphDefinitionAttempt: GraphDefinitionAttempt
): NestedObjectRename {
    val newObjectPath = objectLocation.objectPath.copy(nesting = newObjectNesting)
    val newObjectLocation = objectLocation.copy(objectPath = newObjectPath)

    val adjustedReferenceCommands = adjustReferenceCommands(
        objectLocation, newObjectLocation, graphDefinitionAttempt)

    val adjustedReferenceEvents = adjustedReferenceCommands
        .map { buffer.apply(it) as UpdatedInAttributeEvent }
        .toList()

    val renamedObject = buffer
        .apply(RenameNestedObjectCommand(objectLocation, newObjectNesting))
        as RenamedNestedObjectEvent

    return NestedObjectRename(
        renamedObject, adjustedReferenceEvents)
}


private fun adjustReferenceCommands(
    objectLocation: ObjectLocation,
    newObjectLocation: ObjectLocation,
    graphDefinitionAttempt: GraphDefinitionAttempt
): List<UpdateInAttributeCommand> {
    val commands = mutableListOf<UpdateInAttributeCommand>()

    val newFullReference = newObjectLocation.toReference()
    val referenceLocations = locateReferences(objectLocation, graphDefinitionAttempt)
    for (referenceLocation in referenceLocations) {
        val existingReference = existingReference(referenceLocation, graphDefinitionAttempt)
        val newReference = newFullReference.crop(existingReference.hasPath())
        if (existingReference == newReference) {
            continue
        }

        val newReferenceNotation = ScalarAttributeNotation(newReference.asString())
        commands.add(UpdateInAttributeCommand(
            referenceLocation.objectLocation,
            referenceLocation.attributePath,
            newReferenceNotation
        ))
    }

    return commands
}


private fun existingReference(
    referenceLocation: AttributeLocation,
    graphDefinitionAttempt: GraphDefinitionAttempt
): ObjectReference {
    val attributePath = referenceLocation.attributePath

    // NB: top-level 'is:' (incl. list-element 'is[i]:') and meta-attribute inheritance refs
    //  live in the notation, not the definition
    if (isInheritancePath(attributePath) || isMetaInheritancePath(attributePath)) {
        val notation = graphDefinitionAttempt
                .graphStructure
                .graphNotation
                .coalesce
                .map[referenceLocation.objectLocation]!!
        val scalar = notation.get(attributePath) as ScalarAttributeNotation
        return ObjectReference.parse(scalar.value)
    }

    val existingReferenceDefinition =
        graphDefinitionAttempt
            .objectDefinitions[referenceLocation.objectLocation]
            ?.get(attributePath)
        ?: graphDefinitionAttempt
            .failures[referenceLocation.objectLocation]!!
            .partial!!
            .get(attributePath)
    return (existingReferenceDefinition as ReferenceAttributeDefinition).objectReference!!
}


private fun isInheritancePath(path: AttributePath): Boolean {
    if (path == NotationConventions.isAttributePath) {
        return true
    }
    // NB: list-element 'is[i]:' for multiple inheritance
    if (path.attribute != NotationConventions.isAttributeName) {
        return false
    }
    val segments = path.nesting.segments
    return segments.size == 1 && segments.first().asIndex() != null
}


private fun isMetaInheritancePath(path: AttributePath): Boolean {
    if (path.attribute != NotationConventions.metaAttributeName) {
        return false
    }
    val segments = path.nesting.segments
    return when (segments.size) {
        1 -> true
        2 -> segments.last() == NotationConventions.isAttributeSegment
        else -> false
    }
}


private fun locateReferences(
    objectLocation: ObjectLocation,
    graphDefinitionAttempt: GraphDefinitionAttempt
): Set<AttributeLocation> {
    val referenceLocations = mutableSetOf<AttributeLocation>()

    fun locateInObjectDefinition(hostObjectLocation: ObjectLocation, objectDefinition: ObjectDefinition) {
        val attributeReferences =
                objectDefinition.attributeReferencesIncludingWeak()

        for (attributeReference in attributeReferences) {
            if (!isReferenced(
                    objectLocation,
                    attributeReference.value.objectReference,
                    ObjectReferenceHost.ofLocation(hostObjectLocation),
                    graphDefinitionAttempt)) {
                continue
            }

            // Skip references that live in a derived/auto-wired attribute with no notation backing — e.g.
            // the synthetic NestedList step lists (and Autowired / ParentChild). They re-compute from object
            // structure after a rename/move, so there is nothing to rewrite; trying would throw in
            // updateInAttribute (which guards on this same null merged attribute).
            if (graphDefinitionAttempt.graphStructure.graphNotation.mergeAttribute(
                    hostObjectLocation, attributeReference.key.attribute) == null) {
                continue
            }

            val referencingAttribute = AttributeLocation(hostObjectLocation, attributeReference.key)
            referenceLocations.add(referencingAttribute)
        }
    }

    for (e in graphDefinitionAttempt.objectDefinitions.map) {
        locateInObjectDefinition(e.key, e.value)
    }

    for (e in graphDefinitionAttempt.failures.map) {
        val partial = e.value.partial
            ?: continue

        locateInObjectDefinition(e.key, partial)
    }

    referenceLocations.addAll(locateIsReferences(objectLocation, graphDefinitionAttempt))

    return referenceLocations
}


private fun locateIsReferences(
    objectLocation: ObjectLocation,
    graphDefinitionAttempt: GraphDefinitionAttempt
): Set<AttributeLocation> {
    val referenceLocations = mutableSetOf<AttributeLocation>()
    val graphNotation = graphDefinitionAttempt.graphStructure.graphNotation

    for ((hostObjectLocation, objectNotation) in graphNotation.coalesce.map) {
        val host = ObjectReferenceHost.ofLocation(hostObjectLocation)

        when (val isAttribute = objectNotation.get(NotationConventions.isAttributePath)) {
            is ScalarAttributeNotation -> {
                if (resolvesToTarget(graphNotation, isAttribute.value, host, objectLocation)) {
                    referenceLocations.add(
                            AttributeLocation(hostObjectLocation, NotationConventions.isAttributePath))
                }
            }

            is ListAttributeNotation -> {
                // NB: multiple inheritance — each list element is a parent reference
                isAttribute.values.forEachIndexed { index, element ->
                    val scalar = element as? ScalarAttributeNotation
                        ?: return@forEachIndexed
                    if (resolvesToTarget(graphNotation, scalar.value, host, objectLocation)) {
                        referenceLocations.add(AttributeLocation(
                                hostObjectLocation,
                                NotationConventions.isAttributePath.nest(AttributeSegment.ofIndex(index))))
                    }
                }
            }

            else -> {}
        }

        // NB: meta.<attr>: OldName  (scalar)  and  meta.<attr>.is: OldName  (map) are both inheritance refs
        val metaAttribute = objectNotation.get(NotationConventions.metaAttributePath) as? MapAttributeNotation
            ?: continue

        for ((metaSegment, metaValue) in metaAttribute.map) {
            val metaAttributePath = NotationConventions.metaAttributePath.nest(metaSegment)

            when (metaValue) {
                is ScalarAttributeNotation -> {
                    if (resolvesToTarget(graphNotation, metaValue.value, host, objectLocation)) {
                        referenceLocations.add(AttributeLocation(hostObjectLocation, metaAttributePath))
                    }
                }

                is MapAttributeNotation -> {
                    val nestedIs = metaValue.map[NotationConventions.isAttributeSegment]
                            as? ScalarAttributeNotation
                        ?: continue
                    if (resolvesToTarget(graphNotation, nestedIs.value, host, objectLocation)) {
                        referenceLocations.add(AttributeLocation(
                                hostObjectLocation,
                                metaAttributePath.nest(NotationConventions.isAttributeSegment)))
                    }
                }

                else -> {}
            }
        }
    }

    return referenceLocations
}


private fun resolvesToTarget(
    graphNotation: GraphNotation,
    value: String,
    host: ObjectReferenceHost,
    target: ObjectLocation
): Boolean {
    val reference = ObjectReference.parse(value)
    return graphNotation.coalesce.locateOptional(reference, host) == target
}


private fun isReferenced(
    targetLocation: ObjectLocation,
    reference: ObjectReference,
    host: ObjectReferenceHost,
    graphDefinitionAttempt: GraphDefinitionAttempt
): Boolean {
    val referencedLocation = graphDefinitionAttempt
        .objectDefinitions
        .locateOptional(reference, host)

    if (referencedLocation == targetLocation) {
        return true
    }

    val partialReferencedLocation = graphDefinitionAttempt
        .failures
        .locateOptional(reference, host)

    return partialReferencedLocation == targetLocation
}


// Relocate a single document to a new path (rename = same nesting / new name; move = same name / new
// nesting). Copies old→new, deletes old, and rewrites references into the document's root objects.
internal fun relocateDocumentRefactor(
    graphDefinitionAttempt: GraphDefinitionAttempt,
    documentPath: DocumentPath,
    newDocumentPath: DocumentPath
): NotationTransition {
    val graphNotation = graphDefinitionAttempt.graphStructure.graphNotation
    val documentNotation = graphNotation.documents.map[documentPath]
    require(documentNotation != null) {
        "documentPath missing: $documentPath - ${graphNotation.documents.map.keys}"
    }
    require(newDocumentPath !in graphNotation.documents.map) {
        "Destination already exists: $newDocumentPath"
    }
    val buffer = StructuralBuffer(graphNotation)

    val createdWithNewName = buffer
        .apply(CopyDocumentCommand(
            documentPath,
            newDocumentPath
        ))
        as CopiedDocumentEvent

    val removedUnderOldName = buffer
        .apply(DeleteDocumentCommand(documentPath))
        as DeletedDocumentEvent

    val adjustedReferenceEvents = adjustReferencesForRenamedDocument(
            documentPath, newDocumentPath, documentNotation, graphDefinitionAttempt, buffer)

    return NotationTransition(
        RenamedDocumentRefactorEvent(
            createdWithNewName,
            removedUnderOldName,
            adjustedReferenceEvents
        ),
        buffer.graphNotation)
}


private fun adjustReferencesForRenamedDocument(
    documentPath: DocumentPath,
    newDocumentPath: DocumentPath,
    documentNotation: DocumentNotation,
    graphDefinitionAttempt: GraphDefinitionAttempt,
    buffer: StructuralBuffer
): List<UpdatedInAttributeEvent> {
    // NB: only top-level (root) objects cross-document reference are currently supported
    val rootObjectPaths = documentNotation
        .objects
        .notations
        .map
        .keys
        .filter { it.nesting.isRoot() }

    val allAdjustedReferenceEvents = mutableListOf<UpdatedInAttributeEvent>()

    for (adjustedObjectPath in rootObjectPaths) {
        val rootObjectLocation = ObjectLocation(documentPath, adjustedObjectPath)
        val newObjectLocation = ObjectLocation(newDocumentPath, adjustedObjectPath)

        val adjustedReferenceCommands = adjustReferenceCommands(
            rootObjectLocation, newObjectLocation, graphDefinitionAttempt)

        val adjustedReferenceEvents = adjustedReferenceCommands
            .map { buffer.apply(it) as UpdatedInAttributeEvent }

        allAdjustedReferenceEvents.addAll(adjustedReferenceEvents)
    }

    return allAdjustedReferenceEvents
}


// Relocate a folder and its whole subtree (rename = same nesting / new name; move = same name / new
// nesting). Every nested document/folder is re-nested by swapping the old content-nesting prefix for the
// new one, and references into the moved objects are rewritten — including intra-subtree references
// between two moved documents (handled by adjusting at the referencing object's FINAL location, below).
internal fun relocateFolderRefactor(
    graphDefinitionAttempt: GraphDefinitionAttempt,
    folderPath: DocumentPath,
    newFolderPath: DocumentPath
): NotationTransition {
    val graphNotation = graphDefinitionAttempt.graphStructure.graphNotation

    check(folderPath.folder) { "Not a folder: $folderPath" }
    check(newFolderPath.folder) { "Not a folder: $newFolderPath" }
    require(folderPath in graphNotation.documents.map) {
        "Folder missing: $folderPath - ${graphNotation.documents.map.keys}"
    }
    require(newFolderPath !in graphNotation.documents.map) {
        "Destination already exists: $newFolderPath"
    }

    // "foo" at nesting N holds its contents at N + foo
    val oldContentNesting = folderPath.nesting.plus(DocumentSegment(folderPath.name.value))
    val newContentNesting = newFolderPath.nesting.plus(DocumentSegment(newFolderPath.name.value))

    require(!newContentNesting.startsWith(oldContentNesting)) {
        "Cannot move a folder into itself or a descendant: $folderPath -> $newFolderPath"
    }

    fun reNestPath(path: DocumentPath): DocumentPath {
        if (path == folderPath) {
            return newFolderPath
        }
        return path.copy(nesting = path.nesting.replacePrefix(oldContentNesting, newContentNesting))
    }

    // documents + nested folders strictly under the old content nesting
    val descendants = graphNotation.documents.map.keys
        .filter { it.nesting.startsWith(oldContentNesting) }
    val descendantFolders = descendants.filter { it.folder }
    val descendantDocuments = descendants.filter { !it.folder }

    val buffer = StructuralBuffer(graphNotation)

    // 1. create the new folder and its nested folders (empty folders persist this way too)
    val createdFolder = buffer
        .apply(CreateFolderCommand(newFolderPath)) as CreatedFolderEvent
    val createdSubfolders = descendantFolders.map {
        buffer.apply(CreateFolderCommand(reNestPath(it))) as CreatedFolderEvent
    }

    // 2. copy each descendant document to its new location (body byte-identical at this stage)
    val copiedDocuments = descendantDocuments.map {
        buffer.apply(CopyDocumentCommand(it, reNestPath(it))) as CopiedDocumentEvent
    }

    // 3. rewrite references into the moved objects, AT the referencing object's final location (so an
    //    inside→inside reference lands on the just-made copy, which DirectGraphStore then persists)
    val adjustedReferences = adjustReferencesForRelocatedFolder(
        descendantDocuments, oldContentNesting, folderPath, ::reNestPath, graphDefinitionAttempt, buffer)

    // 4. cascade-delete the old subtree (the folder's own entry + everything under its content nesting)
    val removedFolder = buffer
        .apply(DeleteFolderCommand(folderPath)) as DeletedFolderEvent

    return NotationTransition(
        RenamedFolderRefactorEvent(
            createdFolder, createdSubfolders, copiedDocuments, adjustedReferences, removedFolder),
        buffer.graphNotation)
}


private fun adjustReferencesForRelocatedFolder(
    movedDocuments: List<DocumentPath>,
    oldContentNesting: DocumentNesting,
    oldFolderPath: DocumentPath,
    reNestPath: (DocumentPath) -> DocumentPath,
    graphDefinitionAttempt: GraphDefinitionAttempt,
    buffer: StructuralBuffer
): List<UpdatedInAttributeEvent> {
    val graphNotation = graphDefinitionAttempt.graphStructure.graphNotation

    fun finalReferencingLocation(referencingObjectLocation: ObjectLocation): ObjectLocation {
        val documentPath = referencingObjectLocation.documentPath
        val insideSubtree = documentPath == oldFolderPath ||
                documentPath.nesting.startsWith(oldContentNesting)
        return if (insideSubtree) {
            referencingObjectLocation.copy(documentPath = reNestPath(documentPath))
        }
        else {
            referencingObjectLocation
        }
    }

    val allAdjustedReferenceEvents = mutableListOf<UpdatedInAttributeEvent>()

    for (movedDocument in movedDocuments) {
        // NB: only top-level (root) objects cross-document reference are currently supported
        val rootObjectPaths = graphNotation.documents.map[movedDocument]!!
            .objects
            .notations
            .map
            .keys
            .filter { it.nesting.isRoot() }

        val newDocumentPath = reNestPath(movedDocument)

        for (rootObjectPath in rootObjectPaths) {
            val oldObjectLocation = ObjectLocation(movedDocument, rootObjectPath)
            val newFullReference = ObjectLocation(newDocumentPath, rootObjectPath).toReference()

            val referenceLocations = locateReferences(oldObjectLocation, graphDefinitionAttempt)
            for (referenceLocation in referenceLocations) {
                val existingReference = existingReference(referenceLocation, graphDefinitionAttempt)
                val newReference = newFullReference.crop(existingReference.hasPath())
                if (existingReference == newReference) {
                    continue
                }

                val finalObjectLocation = finalReferencingLocation(referenceLocation.objectLocation)
                val newReferenceNotation = ScalarAttributeNotation(newReference.asString())

                val event = buffer.apply(UpdateInAttributeCommand(
                    finalObjectLocation,
                    referenceLocation.attributePath,
                    newReferenceNotation
                )) as UpdatedInAttributeEvent

                allAdjustedReferenceEvents.add(event)
            }
        }
    }

    return allAdjustedReferenceEvents
}
