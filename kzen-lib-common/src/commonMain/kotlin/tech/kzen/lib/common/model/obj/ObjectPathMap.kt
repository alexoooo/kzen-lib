package tech.kzen.lib.common.model.obj

import tech.kzen.lib.common.model.structure.notation.PositionedObjectPath
import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.toPersistentMap


data class ObjectPathMap<T>(
    val map: PersistentMap<ObjectPath, T>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val empty = ObjectPathMap(mapOf<ObjectPath, Any>().toPersistentMap())

        fun <T> empty(): ObjectPathMap<T> {
            @Suppress("UNCHECKED_CAST")
            return empty as ObjectPathMap<T>
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    operator fun get(objectPath: ObjectPath): T? {
        return map[objectPath]
    }

    
    fun equalsInOrder(other: ObjectPathMap<T>): Boolean {
        return map.equalsInOrder(other.map)
//        if (values != other.values) {
//            return false
//        }
//
//        val iteratorA = values.keys.iterator()
//        val iteratorB = other.values.keys.iterator()
//
//        while (iteratorA.hasNext()) {
//            if (iteratorA.next() != iteratorB.next()) {
//                return false
//            }
//        }
//
//        return true
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun updateEntry(
        key: ObjectPath,
        value: T
    ): ObjectPathMap<T> {
        check(key in map) { "Not found: $key" }
        return ObjectPathMap(map.put(key, value))
    }


    fun insertEntry(
        key: PositionedObjectPath,
        value: T
    ): ObjectPathMap<T> {
        check(key.objectPath !in map) { "Already exists: $key" }
        check(0 <= key.positionIndex.value &&
                key.positionIndex.value <= map.size
        ) {
            "Index (${key.positionIndex.value}) must be in [0, ${map.size}]"
        }

        if (key.positionIndex.value == map.size) {
            return ObjectPathMap(map.put(key.objectPath, value))
        }

        return ObjectPathMap(
                map.insert(key.objectPath, value, key.positionIndex.value))
    }


    fun removeKey(
        key: ObjectPath
    ): ObjectPathMap<T> {
        check(map.containsKey(key)) { "Not found: $key" }
        return ObjectPathMap(map.remove(key))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return map.toString()
    }
}