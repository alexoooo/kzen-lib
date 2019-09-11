package tech.kzen.lib.common.model.structure.notation

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectLocationMap
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.obj.ObjectPathMap
import tech.kzen.lib.common.model.structure.resource.ResourceListing
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.platform.collect.toPersistentMap


data class DocumentNotation(
        val objects: ObjectPathMap<ObjectNotation>,
        val resources: ResourceListing?
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val emptyWithoutResources = DocumentNotation(
                ObjectPathMap.empty(), null)

        val emptyWithResources = DocumentNotation(
                ObjectPathMap.empty(), ResourceListing.empty)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun expand(path: DocumentPath): ObjectLocationMap<ObjectNotation> {
        val values = mutableMapOf<ObjectLocation, ObjectNotation>()

        for (e in objects.values) {
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
        return PositionIndex(objects.values.keys.indexOf(objectPath))
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
        return copy(objects = objects.updateEntry(objectPath, objectNotation))
    }


    fun withNewObject(
            positionedObjectPath: PositionedObjectPath,
            objectNotation: ObjectNotation
    ): DocumentNotation {
        return copy(objects = objects.insertEntry(positionedObjectPath, objectNotation))
    }


    fun withoutObject(
            objectPath: ObjectPath
    ): DocumentNotation {
        return copy(objects = objects.removeKey(objectPath))
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
    override fun toString(): String {
        return "[$objects | $resources]"
    }
}
