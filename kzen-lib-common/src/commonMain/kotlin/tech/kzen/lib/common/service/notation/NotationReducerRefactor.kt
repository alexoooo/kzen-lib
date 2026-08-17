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

    val referenceIndex = ReferenceIndex.of(graphDefinitionAttempt)
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
            referenceIndex
        )
    }

    val newObjectPath = objectLocation.objectPath.copy(name = newName)
    val newObjectLocation = objectLocation.copy(objectPath = newObjectPath)

    val adjustedReferenceCommands = adjustReferenceCommands(
        objectLocation, newObjectLocation, referenceIndex)

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

    val referenceIndex = ReferenceIndex.of(graphDefinitionAttempt)
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
            referenceIndex)
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
    referenceIndex: ReferenceIndex
): NestedObjectRename {
    val newObjectPath = objectLocation.objectPath.copy(nesting = newObjectNesting)
    val newObjectLocation = objectLocation.copy(objectPath = newObjectPath)

    val adjustedReferenceCommands = adjustReferenceCommands(
        objectLocation, newObjectLocation, referenceIndex)

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
    referenceIndex: ReferenceIndex
): List<UpdateInAttributeCommand> {
    val commands = mutableListOf<UpdateInAttributeCommand>()

    val newFullReference = newObjectLocation.toReference()
    for (referenceLocation in referenceIndex.referencesTo(objectLocation)) {
        val existingReference = referenceIndex.existingReference(referenceLocation)
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


/**
 * Every object reference in the graph, keyed by the location it resolves to.
 *
 * A refactor rewrites references for each of the objects it moves — every nested object of a renamed
 * container, every root object of every document under a moved folder — and resolving them means visiting
 * every object definition plus every `is:` / `meta:` entry in the notation. Scanning once per moved object
 * makes that whole-graph pass the inner loop, so the scan happens once per refactor instead and every
 * rewrite reads this index. The definition attempt a refactor works from never changes while it runs (the
 * buffer's edits are not fed back into it), so one index covers the whole refactor.
 */
private class ReferenceIndex private constructor(
    private val graphDefinitionAttempt: GraphDefinitionAttempt,
    private val byTarget: Map<ObjectLocation, Set<AttributeLocation>>
) {
    companion object {
        fun of(graphDefinitionAttempt: GraphDefinitionAttempt): ReferenceIndex {
            return ReferenceIndex(graphDefinitionAttempt, indexReferences(graphDefinitionAttempt))
        }
    }


    fun referencesTo(objectLocation: ObjectLocation): Set<AttributeLocation> {
        return byTarget[objectLocation]
            ?: setOf()
    }


    fun existingReference(referenceLocation: AttributeLocation): ObjectReference {
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


private fun indexReferences(
    graphDefinitionAttempt: GraphDefinitionAttempt
): Map<ObjectLocation, Set<AttributeLocation>> {
    val graphNotation = graphDefinitionAttempt.graphStructure.graphNotation
    val byTarget = mutableMapOf<ObjectLocation, MutableSet<AttributeLocation>>()

    fun index(target: ObjectLocation?, referencingAttribute: AttributeLocation) {
        if (target == null) {
            return
        }
        byTarget.getOrPut(target) { mutableSetOf() }.add(referencingAttribute)
    }

    fun indexObjectDefinition(hostObjectLocation: ObjectLocation, objectDefinition: ObjectDefinition) {
        val host = ObjectReferenceHost.ofLocation(hostObjectLocation)

        for (attributeReference in objectDefinition.attributeReferencesIncludingWeak()) {
            // Skip references that live in a derived/auto-wired attribute with no notation backing — e.g.
            // the synthetic NestedList step lists (and Autowired / ParentChild). They re-compute from object
            // structure after a rename/move, so there is nothing to rewrite; trying would throw in
            // updateInAttribute (which guards on this same null merged attribute).
            if (graphNotation.mergeAttribute(
                    hostObjectLocation, attributeReference.key.attribute) == null) {
                continue
            }

            val referencingAttribute = AttributeLocation(hostObjectLocation, attributeReference.key)
            val reference = attributeReference.value.objectReference

            // A reference is indexed under whatever each layer resolves it to, because a rename must follow
            // it through any of them. Notably a weak reference can name an object that has no definition and
            // no failure at all — an `abstract: true` archetype named by a `by: Nominal` data attribute (a
            // Context declaration, a branchArchetype) — and without the notation coalesce fallback, renaming
            // such an object silently leaves every weak reference to it dangling.
            index(graphDefinitionAttempt.objectDefinitions.locateOptional(reference, host), referencingAttribute)
            index(graphDefinitionAttempt.failures.locateOptional(reference, host), referencingAttribute)
            index(graphNotation.coalesce.locateOptional(reference, host), referencingAttribute)
        }
    }

    for (e in graphDefinitionAttempt.objectDefinitions.map) {
        indexObjectDefinition(e.key, e.value)
    }

    for (e in graphDefinitionAttempt.failures.map) {
        val partial = e.value.partial
            ?: continue

        indexObjectDefinition(e.key, partial)
    }

    indexIsReferences(graphNotation, ::index)

    return byTarget
}


private fun indexIsReferences(
    graphNotation: GraphNotation,
    index: (ObjectLocation?, AttributeLocation) -> Unit
) {
    fun resolve(value: String, host: ObjectReferenceHost): ObjectLocation? {
        return graphNotation.coalesce.locateOptional(ObjectReference.parse(value), host)
    }

    for ((hostObjectLocation, objectNotation) in graphNotation.coalesce.map) {
        val host = ObjectReferenceHost.ofLocation(hostObjectLocation)

        when (val isAttribute = objectNotation.get(NotationConventions.isAttributePath)) {
            is ScalarAttributeNotation -> {
                index(
                    resolve(isAttribute.value, host),
                    AttributeLocation(hostObjectLocation, NotationConventions.isAttributePath))
            }

            is ListAttributeNotation -> {
                // NB: multiple inheritance — each list element is a parent reference
                isAttribute.values.forEachIndexed { elementIndex, element ->
                    val scalar = element as? ScalarAttributeNotation
                        ?: return@forEachIndexed
                    index(
                        resolve(scalar.value, host),
                        AttributeLocation(
                            hostObjectLocation,
                            NotationConventions.isAttributePath.nest(AttributeSegment.ofIndex(elementIndex))))
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
                    index(
                        resolve(metaValue.value, host),
                        AttributeLocation(hostObjectLocation, metaAttributePath))
                }

                is MapAttributeNotation -> {
                    val nestedIs = metaValue.map[NotationConventions.isAttributeSegment]
                            as? ScalarAttributeNotation
                        ?: continue
                    index(
                        resolve(nestedIs.value, host),
                        AttributeLocation(
                            hostObjectLocation,
                            metaAttributePath.nest(NotationConventions.isAttributeSegment)))
                }

                else -> {}
            }
        }
    }
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
            documentPath, newDocumentPath, documentNotation, ReferenceIndex.of(graphDefinitionAttempt), buffer)

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
    referenceIndex: ReferenceIndex,
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
            rootObjectLocation, newObjectLocation, referenceIndex)

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
        descendantDocuments, oldContentNesting, folderPath, ::reNestPath, graphNotation,
        ReferenceIndex.of(graphDefinitionAttempt), buffer)

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
    graphNotation: GraphNotation,
    referenceIndex: ReferenceIndex,
    buffer: StructuralBuffer
): List<UpdatedInAttributeEvent> {
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

            for (referenceLocation in referenceIndex.referencesTo(oldObjectLocation)) {
                val existingReference = referenceIndex.existingReference(referenceLocation)
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
