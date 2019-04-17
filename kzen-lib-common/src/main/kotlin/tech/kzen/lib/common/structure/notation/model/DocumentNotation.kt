package tech.kzen.lib.common.structure.notation.model

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectLocationMap
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.obj.ObjectPathMap
import tech.kzen.lib.platform.collect.toPersistentMap


data class DocumentNotation(
        val objects: ObjectPathMap<ObjectNotation>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = DocumentNotation(ObjectPathMap(
                mapOf<ObjectPath, ObjectNotation>().toPersistentMap()))
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
        return DocumentNotation(
                objects.updateEntry(objectPath, objectNotation))
    }


    fun withNewObject(
            positionedObjectPath: PositionedObjectPath,
            objectNotation: ObjectNotation
    ): DocumentNotation {
        return DocumentNotation(
                objects.insertEntry(positionedObjectPath, objectNotation))
    }


    fun withoutObject(
            objectPath: ObjectPath
    ): DocumentNotation {
        return DocumentNotation(
                objects.removeKey(objectPath))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return objects.toString()
    }
}
