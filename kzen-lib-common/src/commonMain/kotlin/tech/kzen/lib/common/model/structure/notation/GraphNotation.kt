package tech.kzen.lib.common.model.structure.notation

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.location.*
import tech.kzen.lib.common.objects.bootstrap.BootstrapConventions
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible
import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.toPersistentMap


@Suppress("unused")
data class GraphNotation(
    val documents: DocumentPathMap<DocumentNotation>
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = GraphNotation(DocumentPathMap(
            mapOf<DocumentPath, DocumentNotation>().toPersistentMap()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    val objectLocations: Set<ObjectLocation> by lazy {
        coalesce.map.keys
    }


    val coalesce: ObjectLocationMap<ObjectNotation> by lazy {
        val buffer = mutableMapOf<ObjectLocation, ObjectNotation>()
        documents.map.entries
            .flatMap { it.value.expand(it.key).map.entries }
            .forEach { buffer[it.key] = it.value }
        ObjectLocationMap(buffer.toPersistentMap())
    }


    private val inheritanceChainCache = mutableMapOf<ObjectLocation, List<ObjectLocation>>()


    private var digest: Digest? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun inheritanceChain(objectLocation: ObjectLocation): List<ObjectLocation> {
        val cached = inheritanceChainCache[objectLocation]
        if (cached != null) {
            return cached
        }

        return buildInheritanceChain(objectLocation, mutableSetOf())
    }


    /**
     * Linearize the (possibly multiple-inheritance) ancestor graph into a C3-like order: most-derived first,
     * primary parent ahead of mix-ins, with shared ancestors and root sinking to the end via keep-last dedup.
     * Keep-last is total (never raises), so a partial/broken hierarchy still starts. The `visiting` set guards
     * against 'is' cycles, which multiple inheritance makes more plausible.
     */
    private fun buildInheritanceChain(
        objectLocation: ObjectLocation,
        visiting: MutableSet<ObjectLocation>
    ): List<ObjectLocation> {
        val cached = inheritanceChainCache[objectLocation]
        if (cached != null) {
            return cached
        }

        if (! visiting.add(objectLocation)) {
            // NB: cycle detected — contribute just this location and stop descending (not cached)
            return listOf(objectLocation)
        }

        val builder = mutableListOf<ObjectLocation>()
        builder.add(objectLocation)
        for (parentLocation in inheritanceParents(objectLocation)) {
            builder.addAll(buildInheritanceChain(parentLocation, visiting))
        }
        visiting.remove(objectLocation)

        val linearized = dedupKeepLast(builder)
        inheritanceChainCache[objectLocation] = linearized
        return linearized
    }


    private fun dedupKeepLast(locations: List<ObjectLocation>): List<ObjectLocation> {
        val seen = mutableSetOf<ObjectLocation>()
        val reversedUnique = mutableListOf<ObjectLocation>()
        for (i in locations.indices.reversed()) {
            val location = locations[i]
            if (seen.add(location)) {
                reversedUnique.add(location)
            }
        }
        return reversedUnique.reversed()
    }


    private fun inheritanceParents(
        objectLocation: ObjectLocation
    ): List<ObjectLocation> {
        if (objectLocation == BootstrapConventions.rootObjectLocation ||
                objectLocation == BootstrapConventions.bootstrapObjectLocation) {
            return listOf()
        }

        val notation = coalesce.map[objectLocation]
            ?: throw IllegalArgumentException("Missing: $objectLocation")

        val isAttribute = notation.get(NotationConventions.isAttributePath)
            ?: return listOf(BootstrapConventions.rootObjectLocation)

        val referenceHost = ObjectReferenceHost.ofLocation(objectLocation)

        return when (isAttribute) {
            is ScalarAttributeNotation ->
                listOf(inheritanceParent(isAttribute.value, referenceHost))

            is ListAttributeNotation -> {
                // NB: multiple inheritance — a non-empty list of parent references (mix-ins);
                //  an empty list degrades to no-super so startup survives
                if (isAttribute.values.isEmpty()) {
                    listOf(BootstrapConventions.rootObjectLocation)
                }
                else {
                    isAttribute.values.map { element ->
                        require(element is ScalarAttributeNotation) {
                            "Scalar 'is' list element expected: $objectLocation - $element"
                        }
                        inheritanceParent(element.value, referenceHost)
                    }
                }
            }

            else ->
                throw IllegalArgumentException(
                    "Scalar or list 'is' attribute expected: $objectLocation - $isAttribute")
        }
    }


    private fun inheritanceParent(
        isValue: String,
        referenceHost: ObjectReferenceHost
    ): ObjectLocation {
        val superReference = ObjectReference.parse(isValue)

        // NB: dangling 'is:' (e.g. parent renamed/removed) degrades to no-super so startup survives
        return coalesce.locateOptional(superReference, referenceHost)
            ?: BootstrapConventions.rootObjectLocation
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun directAttribute(
        objectLocation: ObjectLocation,
        attributeName: AttributeName
    ): AttributeNotation? {
        return coalesce.map[objectLocation]?.get(attributeName)
    }


    fun directAttribute(
        objectLocation: ObjectLocation,
        attributePath: AttributePath
    ): AttributeNotation? {
        return coalesce.map[objectLocation]?.get(attributePath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun mergeObject(objectLocation: ObjectLocation): ObjectNotation? {
        if (objectLocation !in coalesce) {
            return null
        }

        val ancestors = inheritanceChain(objectLocation)

        val transitiveAttributes = ancestors
            .mapNotNull { coalesce[it] }
            .flatMap { it.attributes.map.keys }
            .toSet()
            .toList()

        val attributeValues: PersistentMap<AttributeName, AttributeNotation> = transitiveAttributes
            .filter { !NotationConventions.isSpecial(it) }
            .map { it to mergeAttribute(it, ancestors) }
            .filter { it.second != null }
            .map { it.first to it.second!! }
            .toPersistentMap()

        return ObjectNotation(AttributeNameMap(attributeValues))
    }


    fun mergeAttribute(
        objectLocation: ObjectLocation,
        attribute: AttributeName
    ): AttributeNotation? {
        val ancestors = inheritanceChain(objectLocation)
        return mergeAttribute(attribute, ancestors)
    }


    fun mergeAttribute(
        objectLocation: ObjectLocation,
        attributePath: AttributePath
    ): AttributeNotation? {
        val notation = mergeAttribute(objectLocation, attributePath.attribute)
        return notation?.get(attributePath.nesting)
    }


    private fun mergeAttribute(
        attribute: AttributeName,
        ancestors: List<ObjectLocation>
    ): AttributeNotation? {
        var notation: AttributeNotation? = null

        for (ancestor in ancestors) {
            val directAttributeNotation = directAttribute(ancestor, attribute)
                ?: continue

            notation = notation?.merge(directAttributeNotation) ?: directAttributeNotation
        }

        return notation
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun firstAttribute(
        attributeLocation: AttributeLocation
    ): AttributeNotation {
        return firstAttribute(
            attributeLocation.objectLocation, attributeLocation.attributePath)
            ?: throw IllegalArgumentException("Unknown attribute: $attributeLocation")
    }


    fun firstAttribute(
        objectLocation: ObjectLocation,
        attributeName: AttributeName
    ): AttributeNotation {
        return firstAttribute(
            objectLocation, AttributePath.ofName(attributeName))
            ?: throw IllegalArgumentException("Unknown attribute: $objectLocation - $attributeName")
    }


    /**
     * Traverse inheritance chain (starting at objectLocation) until finding attributePath from the closest ancestor.
     * Uses the linearized inheritance chain so single- and multiple-inheritance resolve consistently with merge.
     */
    fun firstAttribute(
        objectLocation: ObjectLocation,
        attributePath: AttributePath
    ): AttributeNotation? {
        for (ancestor in inheritanceChain(objectLocation)) {
            val attributeNotation = coalesce.map[ancestor]?.get(attributePath)
            if (attributeNotation != null) {
                return attributeNotation
            }
        }
        return null
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun getString(attributeLocation: AttributeLocation): String {
        return getString(attributeLocation.objectLocation, attributeLocation.attributePath)
    }


    fun getString(objectLocation: ObjectLocation, attributePath: AttributePath): String {
        val firstAttribute = firstAttribute(objectLocation, attributePath)
            ?: throw IllegalArgumentException("Not found: $objectLocation.$attributePath")

        return firstAttribute.asString()
            ?: throw IllegalArgumentException("Expected string ($objectLocation.$attributePath): $firstAttribute")
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun withNewDocument(
        documentPath: DocumentPath,
        documentNotation: DocumentNotation
    ): GraphNotation {
        check(documentPath !in documents.map) {
            "Already exists: $documentPath"
        }
        check(documentPath.directory == (documentNotation.resources != null)) {
            "Unexpected resources for path: $documentPath - ${documentNotation.resources}"
        }

        return GraphNotation(
            documents.put(documentPath, documentNotation))
    }


    fun withModifiedDocument(
        documentPath: DocumentPath,
        documentNotation: DocumentNotation
    ): GraphNotation {
        check(documentPath in documents) {
            "Not found: $documentPath"
        }
        return GraphNotation(
            documents.put(documentPath, documentNotation))
    }


    fun withoutDocument(
        documentPath: DocumentPath
    ): GraphNotation {
        check(documentPath in documents) {
            "Already absent: $documentPath"
        }
        return GraphNotation(
            documents.remove(documentPath))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun filter(allowed: Set<DocumentNesting>): GraphNotation {
        return GraphNotation(
            documents.filter(allowed))
    }


    fun filterPaths(predicate: (DocumentPath) -> Boolean): GraphNotation {
        val filteredDocuments = mutableMapOf<DocumentPath, DocumentNotation>()

        for (e in documents.map) {
            if (!predicate.invoke(e.key)) {
                continue
            }

            filteredDocuments[e.key] = e.value
        }

        return GraphNotation(DocumentPathMap(filteredDocuments.toPersistentMap()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addDigest(digest())
    }


    override fun digest(): Digest {
        if (digest == null) {
            val builder = Digest.Builder()

            builder.addDigestibleOrderedMap(documents.map)

            digest = builder.digest()
        }
        return digest!!
    }
}