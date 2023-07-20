package tech.kzen.lib.common.model.structure.notation

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.*
import tech.kzen.lib.common.objects.bootstrap.BootstrapConventions
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible
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
        coalesce.values.keys
    }


    val coalesce: ObjectLocationMap<ObjectNotation> by lazy {
        val buffer = mutableMapOf<ObjectLocation, ObjectNotation>()
        documents.values.entries
                .flatMap { it.value.expand(it.key).values.entries }
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

        val builder = mutableListOf<ObjectLocation>()

        builder.add(objectLocation)

        val parentLocation = inheritanceParent(objectLocation)
        if (parentLocation != null) {
            val parentInheritanceChain = inheritanceChain(parentLocation)
            builder.addAll(parentInheritanceChain)
        }

        inheritanceChainCache[objectLocation] = builder

        return builder
    }


    private fun inheritanceParent(
        objectLocation: ObjectLocation
    ): ObjectLocation? {
        if (objectLocation == BootstrapConventions.rootObjectLocation ||
                objectLocation == BootstrapConventions.bootstrapObjectLocation) {
            return null
        }

        val notation = coalesce.values[objectLocation]
            ?: throw IllegalArgumentException("Missing: $objectLocation")

        val isAttribute = notation.get(NotationConventions.isAttributePath)
            ?: return BootstrapConventions.rootObjectLocation

        require(isAttribute is ScalarAttributeNotation) {
            "Scalar 'is' attribute expected: $objectLocation - $isAttribute"
        }

        val isValue = isAttribute.value
        val superReference = ObjectReference.parse(isValue)

        return coalesce.locate(superReference, ObjectReferenceHost.ofLocation(objectLocation))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun directAttribute(
        objectLocation: ObjectLocation,
        attributeName: AttributeName
    ): AttributeNotation? {
        return coalesce.values[objectLocation]?.get(attributeName)
    }


    fun directAttribute(
        objectLocation: ObjectLocation,
        attributePath: AttributePath
    ): AttributeNotation? {
        return coalesce.values[objectLocation]?.get(attributePath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun mergeObject(objectLocation: ObjectLocation): ObjectNotation? {
        if (objectLocation !in coalesce) {
            return null
        }

        val ancestors = inheritanceChain(objectLocation)

        val transitiveAttributes = ancestors
            .mapNotNull { coalesce[it] }
            .flatMap { it.attributes.values.keys }
            .toSet()
            .toList()

        val attributeValues: PersistentMap<AttributeName, AttributeNotation> = transitiveAttributes
            .filter { ! NotationConventions.isSpecial(it) }
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
        objectLocation: ObjectLocation,
        attributeName: AttributeName
    ): AttributeNotation {
        return firstAttribute(
                objectLocation, AttributePath.ofName(attributeName))
            ?: throw IllegalArgumentException("Unknown attribute: $objectLocation - $attributeName")
    }


    /**
     * Traverse inheritance chain (starting at objectLocation) until finding attributePath from the closest ancestor.
     */
    fun firstAttribute(
        objectLocation: ObjectLocation,
        attributePath: AttributePath
    ): AttributeNotation? {
        val notation = coalesce.values[objectLocation]
            ?: throw IllegalArgumentException("Unknown object location: $objectLocation")

        val attributeNotation = notation.get(attributePath)
        if (attributeNotation != null) {
            return attributeNotation
        }

        @Suppress("MoveVariableDeclarationIntoWhen", "RedundantSuppression")
        val isAttribute = notation.get(NotationConventions.isAttributePath)

        val superReference =
            when (isAttribute) {
                null ->
                    BootstrapConventions.rootObjectReference

                is ScalarAttributeNotation -> {
                    val isValue = isAttribute.value
                    ObjectReference.parse(isValue)
                }

                else ->
                    throw IllegalArgumentException("$objectLocation - $attributePath - $isAttribute")
            }

//        println("coalesce keys ($objectLocation - $attributePath - $superReference): " + coalesce.values.keys)
        val superLocation = coalesce.locateOptional(
            superReference, ObjectReferenceHost.ofLocation(objectLocation))

        if (superLocation == null ||
                objectLocation == BootstrapConventions.rootObjectLocation ||
                objectLocation == BootstrapConventions.bootstrapObjectLocation) {
            return null
        }

        return firstAttribute(superLocation, attributePath)
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
        check(documentPath !in documents.values) {
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

        for (e in documents.values) {
            if (! predicate.invoke(e.key)) {
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

            builder.addDigestibleOrderedMap(documents.values)

            digest = builder.digest()
        }
        return digest!!
    }
}