package tech.kzen.lib.common.model.structure.notation

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectLocationMap
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.resource.ResourceListing
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.collect.toPersistentMap


data class DocumentNotation(
    val objects: DocumentObjectNotation,
    val resources: ResourceListing?
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = DocumentNotation(DocumentObjectNotation.empty, null)

        val className = ClassName(
                "tech.kzen.lib.common.model.structure.notation.DocumentNotation")


        fun ofObjectsWithEmptyOrNullResources(
                objects: DocumentObjectNotation,
                directory: Boolean
        ): DocumentNotation {
            return DocumentNotation(
                    objects,
                    ResourceListing.emptyOrNull(directory))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var digest: Digest? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun expand(path: DocumentPath): ObjectLocationMap<ObjectNotation> {
        val values = mutableMapOf<ObjectLocation, ObjectNotation>()

        for (e in objects.notations.values) {
            values[ObjectLocation(path, e.key)] = e.value
        }

        return ObjectLocationMap(values.toPersistentMap())
    }


    //-----------------------------------------------------------------------------------------------------------------
//    val digest: Digest by lazy {
//        val combiner = Digest.UnorderedCombiner()
//        for (e in objects) {
//            combiner.add(Digest.ofXoShiRo256StarStar(e.key))
//            combiner.add(Digest.ofXoShiRo256StarStar(e.value))
//        }
//        return@lazy combiner.combine()
//    }

//    fun nameAt(index: Int): ObjectName {
//        return objects.values.keys.toList()[index].name
//    }


    fun indexOf(objectPath: ObjectPath): PositionIndex {
        return PositionIndex(objects.notations.values.keys.indexOf(objectPath))
    }


//    fun equalsInOrder(other: DocumentNotation): Boolean {
//        return this == other &&
//                objects.keys.toList() == other.objects.keys.toList()
//    }


    //-----------------------------------------------------------------------------------------------------------------
    fun withModifiedObject(
            objectPath: ObjectPath,
            objectNotation: ObjectNotation
    ): DocumentNotation {
        return copy(objects = objects
                .withModifiedObject(objectPath, objectNotation))
    }


    fun withNewObject(
            positionedObjectPath: PositionedObjectPath,
            objectNotation: ObjectNotation
    ): DocumentNotation {
        return copy(objects = objects
                .withNewObject(positionedObjectPath, objectNotation))
    }


    fun withoutObject(
            objectPath: ObjectPath
    ): DocumentNotation {
        return copy(objects = objects
                .withoutObject(objectPath))
    }


    fun withNewResource(
            resourcePath: ResourcePath,
            contentDigest: Digest
    ): DocumentNotation {
        val resources = resources
                ?: throw IllegalStateException("Resources not allowed")

        return copy(resources = resources
                .withNewResource(resourcePath, contentDigest))
    }


    fun withoutResource(
            resourcePath: ResourcePath
    ): DocumentNotation {
        val resources = resources
                ?: throw IllegalStateException("Resources not allowed")

        return copy(resources = resources
                .withoutResource(resourcePath))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addDigest(digest())
    }


    override fun digest(): Digest {
        if (digest == null) {
            val builder = Digest.Builder()

            builder.addDigestible(objects)
            builder.addDigestibleNullable(resources)

            digest = builder.digest()
        }
        return digest!!
    }


    override fun toString(): String {
        return "[$objects | $resources]"
    }
}
