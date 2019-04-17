package tech.kzen.lib.common.model.obj

import tech.kzen.lib.common.structure.notation.model.PositionedObjectPath
import tech.kzen.lib.platform.collect.PersistentMap


data class ObjectPathMap<T>(
        val values: PersistentMap<ObjectPath, T>
) {
    //-----------------------------------------------------------------------------------------------------------------
    operator fun get(objectPath: ObjectPath): T {
        return values[objectPath]
                ?: throw IllegalArgumentException("Not found: $objectPath")
    }


//    fun nameAt(index: Int): ObjectName {
//        return values.keys.toList()[index].name
//    }
//
//
//    fun indexOf(objectName: ObjectName): Int {
//        return values.keys.indexOf(ObjectPath(objectName))
//    }


    fun equalsInOrder(other: ObjectPathMap<T>): Boolean {
        if (values != other.values) {
            return false
        }

        val iteratorA = values.keys.iterator()
        val iteratorB = other.values.keys.iterator()

        while (iteratorA.hasNext()) {
            if (iteratorA.next() != iteratorB.next()) {
                return false
            }
        }

        return true
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun updateEntry(
            key: ObjectPath,
            value: T
    ): ObjectPathMap<T> {
        check(key in values) { "Not found: $key" }
        return ObjectPathMap(values.put(key, value))
    }


    fun insertEntry(
            key: PositionedObjectPath,
            value: T
    ): ObjectPathMap<T> {
        check(key.objectPath !in values) { "Already exists: $key" }
        check(0 <= key.positionIndex.value &&
                key.positionIndex.value <= values.size) {
            "Index (${key.positionIndex.value}) must be in [0, ${values.size}]"
        }

        if (key.positionIndex.value == values.size) {
            return ObjectPathMap(values.put(key.objectPath, value))
        }

        return ObjectPathMap(
                values.insert(key.objectPath, value, key.positionIndex.value))

//        val buffer = mutableMapOf<ObjectPath, T>()
//
//        val iterator = values.entries.iterator()
//
//        var nextIndex = 0
//        while (true) {
//            if (nextIndex == key.positionIndex.value) {
//                break
//            }
//            nextIndex++
//
//            val entry = iterator.next()
//            buffer[entry.key] = entry.value
//        }
//
//        buffer[key.objectPath] = value
//
//        while (iterator.hasNext()) {
//            val entry = iterator.next()
//            buffer[entry.key] = entry.value
//        }
//
//        return ObjectPathMap(buffer.toPersistentMap())
    }


    fun removeKey(
            key: ObjectPath
    ): ObjectPathMap<T> {
        check(values.containsKey(key)) { "Not found: $key" }
        return ObjectPathMap(values.remove(key))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return values.toString()
    }
}