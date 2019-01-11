package tech.kzen.lib.common.notation.model

import tech.kzen.lib.common.api.model.*


data class BundleNotation(
        val objects: BundleMap<ObjectNotation>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = BundleNotation(BundleMap(mapOf()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun expand(path: BundlePath): ObjectMap<ObjectNotation> {
        val values = mutableMapOf<ObjectLocation, ObjectNotation>()

        for (e in objects.values) {
            values[ObjectLocation(path, e.key)] = e.value
        }

        return ObjectMap(values)
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
//
//
    fun indexOf(objectName: ObjectPath): PositionIndex {
        return PositionIndex(objects.values.keys.indexOf(objectName))
    }

//
//    fun equalsInOrder(other: BundleNotation): Boolean {
//        return this == other &&
//                objects.keys.toList() == other.objects.keys.toList()
//    }


    //-----------------------------------------------------------------------------------------------------------------
    fun withModifiedObject(
            objectPath: ObjectPath,
            objectNotation: ObjectNotation
    ): BundleNotation {
        return BundleNotation(
                objects.updateEntry(objectPath, objectNotation))
    }


    fun withNewObject(
            objectPath: PositionedObjectPath,
            objectNotation: ObjectNotation
    ): BundleNotation {
        return BundleNotation(objects.insertEntry(objectPath, objectNotation))
    }


    fun withoutObject(
            objectPath: ObjectPath
    ): BundleNotation {
        return BundleNotation(objects.removeKey(objectPath))
    }
}
