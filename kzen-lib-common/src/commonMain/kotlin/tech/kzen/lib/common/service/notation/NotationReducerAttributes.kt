package tech.kzen.lib.common.service.notation

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.*
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.platform.collect.persistentMapOf


// Attribute-level structural command handlers, dispatched from NotationReducer.applyStructural.
// Split out of NotationReducer (G7a); pure functions over GraphNotation, no reducer state.


// The re-merge-inherited-value-before-local-edit invariant, shared by the five handlers below. STEP 1 (document
// lookup) stays at the call site so each keeps its own throw idiom; the edit lambda returns both the modified
// object and the event (two insert sites build their event from values computed inside the edit). Re-materializing
// the fully-merged attribute locally BEFORE the nested edit is load-bearing: a nested edit applied straight to the
// object's own notation would silently drop any part of the attribute that was inherited rather than local.
private fun remergeAttributeThenEdit(
    state: GraphNotation,
    documentNotation: DocumentNotation,
    objectLocation: ObjectLocation,
    mergeKey: AttributeName,
    edit: (objectWithMergedAttribute: ObjectNotation) -> Pair<ObjectNotation, NotationEvent>
): NotationTransition {
    val objectNotation = state.coalesce[objectLocation]
        ?: throw IllegalArgumentException("Not found: $objectLocation")

    val mergedAttributeNotation = state
        .mergeAttribute(objectLocation, mergeKey)
        ?: throw IllegalArgumentException("Not found: $objectLocation - $mergeKey")

    val objectWithMergedAttribute = objectNotation.upsertAttribute(mergeKey, mergedAttributeNotation)

    val (modifiedObjectNotation, event) = edit(objectWithMergedAttribute)

    val modifiedDocumentNotation = documentNotation.withModifiedObject(
        objectLocation.objectPath, modifiedObjectNotation)

    val nextState = state.withModifiedDocument(
        objectLocation.documentPath, modifiedDocumentNotation)

    return NotationTransition(event, nextState)
}


internal fun upsertAttribute(
    state: GraphNotation,
    command: UpsertAttributeCommand
): NotationTransition {
    val documentNotation = state.documents.map[command.objectLocation.documentPath]
            ?: throw IllegalArgumentException("Unknown document path: ${command.objectLocation.documentPath}")

    val objectNotation = state.coalesce[command.objectLocation]
            ?: throw IllegalArgumentException("Unknown object location: ${command.objectLocation}")

    val modifiedObjectNotation = objectNotation.upsertAttribute(
            AttributePath.ofName(command.attributeName), command.attributeNotation)

    val modifiedDocumentNotation = documentNotation.withModifiedObject(
            command.objectLocation.objectPath, modifiedObjectNotation)

    val nextState = state.withModifiedDocument(
            command.objectLocation.documentPath, modifiedDocumentNotation)

    val event = UpsertedAttributeEvent(
        command.objectLocation, command.attributeName, command.attributeNotation)

    return NotationTransition(event, nextState)
}


internal fun updateInAttribute(
    state: GraphNotation,
    command: UpdateInAttributeCommand
): NotationTransition {
    val documentNotation = state.documents.map[command.objectLocation.documentPath]!!

    return remergeAttributeThenEdit(
        state, documentNotation, command.objectLocation, command.attributePath.attribute
    ) { objectWithMergedAttribute ->
        val modifiedObjectNotation = objectWithMergedAttribute.upsertAttribute(
                command.attributePath, command.attributeNotation)

        val event = UpdatedInAttributeEvent(
            command.objectLocation, command.attributePath, command.attributeNotation)

        modifiedObjectNotation to event
    }
}


internal fun updateAllNestingsInAttribute(
    state: GraphNotation,
    command: UpdateAllNestingsInAttributeCommand
): NotationTransition {
    val documentNotation = state.documents.map[command.objectLocation.documentPath]!!

    return remergeAttributeThenEdit(
        state, documentNotation, command.objectLocation, command.attributeName
    ) { objectWithMergedAttribute ->
        var modifiedObjectNotation = objectWithMergedAttribute
        for (attributeNesting in command.attributeNestings) {
            modifiedObjectNotation =  modifiedObjectNotation.upsertAttribute(
                AttributePath(command.attributeName, attributeNesting),
                command.attributeNotation)
        }

        val event = UpdatedAllNestingsInAttributeEvent(
            command.objectLocation, command.attributeName, command.attributeNestings, command.attributeNotation)

        modifiedObjectNotation to event
    }
}


internal fun updateAllValuesInAttribute(
    state: GraphNotation,
    command: UpdateAllValuesInAttributeCommand
): NotationTransition {
    val documentNotation = state.documents.map[command.objectLocation.documentPath]!!

    return remergeAttributeThenEdit(
        state, documentNotation, command.objectLocation, command.attributeName
    ) { objectWithMergedAttribute ->
        var modifiedObjectNotation = objectWithMergedAttribute
        for ((nesting, notation) in command.nestingNotations) {
            modifiedObjectNotation =  modifiedObjectNotation.upsertAttribute(
                AttributePath(command.attributeName, nesting),
                notation)
        }

        val event = UpdatedAllValuesInAttributeEvent(
            command.objectLocation, command.attributeName, command.nestingNotations)

        modifiedObjectNotation to event
    }
}


internal fun insertListItemInAttribute(
    state: GraphNotation,
    command: InsertListItemInAttributeCommand
): NotationTransition {
    val documentNotation = state.documents.map[command.objectLocation.documentPath]
        ?: throw IllegalArgumentException("Not found: ${command.objectLocation.documentPath}")

    return remergeAttributeThenEdit(
        state, documentNotation, command.objectLocation, command.containingList.attribute
    ) { objectWithMergedAttribute ->
        val listInAttribute = objectWithMergedAttribute
            .get(command.containingList) as? ListAttributeNotation
            ?: throw IllegalStateException(
                "List attribute expected: ${command.objectLocation} - ${command.containingList}")

        val indexInList = command.indexInList.resolve(listInAttribute.values.size)

        val listWithInsert = listInAttribute.insert(indexInList, command.item)

        val modifiedObjectNotation = objectWithMergedAttribute.upsertAttribute(
                command.containingList, listWithInsert)

        val event = InsertedListItemInAttributeEvent(
                command.objectLocation, command.containingList, indexInList, listInAttribute)

        modifiedObjectNotation to event
    }
}


internal fun insertAllListItemsInAttribute(
    state: GraphNotation,
    command: InsertAllListItemsInAttributeCommand
): NotationTransition {
    val documentNotation = state.documents.map[command.objectLocation.documentPath]
        ?: throw IllegalArgumentException("Not found: ${command.objectLocation.documentPath}")

    return remergeAttributeThenEdit(
        state, documentNotation, command.objectLocation, command.containingList.attribute
    ) { objectWithMergedAttribute ->
        val listInAttribute = objectWithMergedAttribute
            .get(command.containingList) as? ListAttributeNotation
            ?: throw IllegalStateException(
                "List attribute expected: ${command.objectLocation} - ${command.containingList}")

        val indexInList = command.indexInList.resolve(listInAttribute.values.size)

        val listWithInsert = listInAttribute.insertAll(indexInList, command.items)

        val modifiedObjectNotation = objectWithMergedAttribute.upsertAttribute(
            command.containingList, listWithInsert)

        val event = InsertedAllListItemsInAttributeEvent(
            command.objectLocation, command.containingList, indexInList, command.items)

        modifiedObjectNotation to event
    }
}


internal fun insertMapEntryInAttribute(
    state: GraphNotation,
    command: InsertMapEntryInAttributeCommand
): NotationTransition {
    val documentNotation = state.documents.map[command.objectLocation.documentPath]!!
    val objectNotation = state.coalesce[command.objectLocation]!!

    val containingAttribute = objectNotation.get(command.containingMap)

    require(containingAttribute == null || containingAttribute is MapAttributeNotation) {
        "Map expected: ${command.containingMap} - $containingAttribute"
    }

    val containingMapExists = containingAttribute != null

    val containingMapSize = containingAttribute?.map?.size ?: 0
    val indexInMap = command.indexInMap.resolve(containingMapSize)

    val createdAncestors = mutableListOf<AttributePath>()

    val modifiedObjectNotation =
        if (!containingMapExists) {
            require(command.createAncestorsIfAbsent) {
                "Containing map missing: ${command.containingMap}"
            }

            require(indexInMap.value == 0) {
                "Index out of bounds in empty map: ${command.indexInMap}"
            }

            val containerNotation = MapAttributeNotation(persistentMapOf(
                command.mapKey to command.value
            ))

            var missingAncestorChain = containerNotation

            var furthestPresentAncestor: AttributePath? = command.containingMap
            while (objectNotation.get(furthestPresentAncestor!!) == null) {
                createdAncestors.add(furthestPresentAncestor)

                if (furthestPresentAncestor.nesting.segments.isEmpty()) {
                    furthestPresentAncestor = null
                    break
                }

                val missingKey = furthestPresentAncestor.nesting.segments.last()
                missingAncestorChain = MapAttributeNotation(persistentMapOf(
                    missingKey to missingAncestorChain
                ))

                furthestPresentAncestor = furthestPresentAncestor.parent()
            }

            @Suppress("SENSELESS_COMPARISON")
            if (furthestPresentAncestor == null) {
                objectNotation.upsertAttribute(command.containingMap.attribute, missingAncestorChain)
            }
            else {
                val presentAncestorNotation = objectNotation.get(furthestPresentAncestor)
                require(presentAncestorNotation is MapAttributeNotation) {
                    "Map expected: $presentAncestorNotation"
                }
                val keyInPresentAncestor = missingAncestorChain.map.keys.first()
                val notionUnderPresentAncestor = missingAncestorChain.map[keyInPresentAncestor]!!

                objectNotation.upsertAttribute(
                    furthestPresentAncestor,
                    presentAncestorNotation.put(keyInPresentAncestor, notionUnderPresentAncestor))
            }
        }
        else {
            val mapWithInsert = containingAttribute.insert(
                command.value, command.mapKey, indexInMap)

            objectNotation.upsertAttribute(
                command.containingMap, mapWithInsert)
        }

    val modifiedDocumentNotation = documentNotation.withModifiedObject(
            command.objectLocation.objectPath, modifiedObjectNotation)

    val nextState = state.withModifiedDocument(
            command.objectLocation.documentPath, modifiedDocumentNotation)

    val event = InsertedMapEntryInAttributeEvent(
        command.objectLocation,
        command.containingMap,
        indexInMap,
        command.mapKey,
        command.value,
        createdAncestors.reversed())

    return NotationTransition(event, nextState)
}


internal fun removeInAttribute(
    state: GraphNotation,
    command: RemoveInAttributeCommand
): NotationTransition {
    val documentNotation = state.documents.map[command.objectLocation.documentPath]!!
    val objectNotation = state.coalesce[command.objectLocation]!!

    val containerPath = command.attributePath.parent()
    val containerNotation = objectNotation.get(containerPath)
            as? StructuredAttributeNotation
        ?: throw IllegalArgumentException("Structured container expected: " +
                "$containerPath - ${objectNotation.get(containerPath)}")

    val lastSegment = command.attributePath.nesting.segments.last()

    val containerWithoutElement =
            when (containerNotation) {
                is ListAttributeNotation -> {
                    val parsedIndex = PositionIndex(lastSegment.asIndex()!!)
                    containerNotation.remove(parsedIndex)
                }

                is MapAttributeNotation -> {
                    containerNotation.remove(lastSegment)
                }
            }

    val modifiedObjectNotation =
        if (containerWithoutElement.isEmpty() && command.removeContainerIfEmpty) {
            removeEmptyContainer(objectNotation, containerPath)
        }
        else {
            objectNotation.upsertAttribute(
                containerPath, containerWithoutElement)
        }

    val modifiedDocumentNotation = documentNotation.withModifiedObject(
            command.objectLocation.objectPath, modifiedObjectNotation)

    val nextState = state.withModifiedDocument(
            command.objectLocation.documentPath, modifiedDocumentNotation)

    val event = RemovedInAttributeEvent(
            command.objectLocation, command.attributePath)

    return NotationTransition(event, nextState)
}


internal fun removeListItemInAttribute(
    state: GraphNotation,
    command: RemoveListItemInAttributeCommand
): NotationTransition {
    val documentNotation = state.documents.map[command.objectLocation.documentPath]!!
    val objectNotation = state.coalesce[command.objectLocation]!!

    val containerPath = command.containingList
    val containerNotation = objectNotation.get(containerPath)
            as? ListAttributeNotation
        ?: throw IllegalArgumentException("List expected: " +
                "$containerPath - ${objectNotation.get(containerPath)}")

    val firstIndex = containerNotation.values.indexOfFirst { it == command.item }
    require(firstIndex != -1) { "List does not contain item: ${command.item} - $containerNotation" }

    val lastIndex = containerNotation.values.indexOfLast { it == command.item }
    require(firstIndex == lastIndex) {
        "List contains item duplicates: ${command.item} - $containerNotation"
    }

    val itemIndex = PositionIndex(firstIndex)
    val containerWithoutElement = containerNotation.remove(itemIndex)

    val modifiedObjectNotation =
        if (containerWithoutElement.isEmpty() && command.removeContainerIfEmpty) {
            removeEmptyContainer(objectNotation, containerPath)
        }
        else {
            objectNotation.upsertAttribute(
                containerPath, containerWithoutElement)
        }

    val modifiedDocumentNotation = documentNotation.withModifiedObject(
            command.objectLocation.objectPath, modifiedObjectNotation)

    val nextState = state.withModifiedDocument(
            command.objectLocation.documentPath, modifiedDocumentNotation)

    val removedAttributePath = command.containingList.nest(
        AttributeSegment.ofIndex(firstIndex))

    val event = RemovedInAttributeEvent(
            command.objectLocation, removedAttributePath)

    return NotationTransition(event, nextState)
}


internal fun removeAllListItemsInAttribute(
    state: GraphNotation,
    command: RemoveAllListItemsInAttributeCommand
): NotationTransition {
    val documentNotation = state.documents.map[command.objectLocation.documentPath]!!
    val objectNotation = state.coalesce[command.objectLocation]!!

    val containerPath = command.containingList
    val containerNotation = objectNotation.get(containerPath)
            as? ListAttributeNotation
        ?: throw IllegalArgumentException("List expected: " +
                "$containerPath - ${objectNotation.get(containerPath)}")

    val removedAttributePaths = mutableListOf<AttributePath>()
    var nextObjectNotation = objectNotation
    var nextContainerNotation = containerNotation

    for (item in command.items) {
        val firstIndex = nextContainerNotation.values.indexOfFirst { it == item }
        require(firstIndex != -1) { "List does not contain item: $item - $nextContainerNotation" }

        val lastIndex = nextContainerNotation.values.indexOfLast { it == item }
        require(firstIndex == lastIndex) {
            "List contains item duplicates: $item - $nextContainerNotation"
        }

        val itemIndex = PositionIndex(firstIndex)
        val containerWithoutElement = nextContainerNotation.remove(itemIndex)

        val modifiedObjectNotation =
            if (containerWithoutElement.isEmpty() && command.removeContainerIfEmpty) {
                removeEmptyContainer(nextObjectNotation, containerPath)
            }
            else {
                nextObjectNotation.upsertAttribute(
                    containerPath, containerWithoutElement)
            }

        val removedAttributePath = command.containingList.nest(
            AttributeSegment.ofIndex(firstIndex))

        removedAttributePaths.add(removedAttributePath)

        nextContainerNotation = containerWithoutElement
        nextObjectNotation = modifiedObjectNotation
    }

    val event = RemovedAllInAttributeEvent(
        command.objectLocation, removedAttributePaths)

    val modifiedDocumentNotation = documentNotation.withModifiedObject(
        command.objectLocation.objectPath, nextObjectNotation)

    val nextState = state.withModifiedDocument(
        command.objectLocation.documentPath, modifiedDocumentNotation)

    return NotationTransition(event, nextState)
}


private fun removeEmptyContainer(
    objectNotation: ObjectNotation,
    containerPath: AttributePath
): ObjectNotation {
    val containerParent = containerPath.parent()
    val parentNotion = objectNotation.get(containerParent)
            as StructuredAttributeNotation

    if (containerPath.nesting.segments.isEmpty()) {
        return objectNotation.removeAttribute(containerPath.attribute)
    }

    val containerSegment = containerPath.nesting.segments.last()

    val parentWithoutContainer =
        when (parentNotion) {
            is MapAttributeNotation -> {
                parentNotion.remove(containerSegment)
            }

            is ListAttributeNotation -> {
                parentNotion.remove(PositionIndex(containerSegment.asIndex()!!))
            }
        }

    return objectNotation.upsertAttribute(
        containerParent, parentWithoutContainer)
}
