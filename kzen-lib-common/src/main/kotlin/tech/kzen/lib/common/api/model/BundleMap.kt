package tech.kzen.lib.common.api.model

import tech.kzen.lib.common.structure.notation.model.PositionedObjectPath


data class BundleMap<T>(
        val values: Map<ObjectPath, T>
) {
    //-----------------------------------------------------------------------------------------------------------------
//    fun nameAt(index: Int): ObjectName {
//        return values.keys.toList()[index].name
//    }
//
//
//    fun indexOf(objectName: ObjectName): Int {
//        return values.keys.indexOf(ObjectPath(objectName))
//    }


    fun equalsInOrder(other: BundleMap<T>): Boolean {
        return this == other &&
                values.keys.toList() == other.values.keys.toList()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun updateEntry(
            key: ObjectPath,
            value: T
    ): BundleMap<T> {
        check(values.containsKey(key)) { "Not found: $key" }

        val buffer = mutableMapOf<ObjectPath, T>()

        for (e in values) {
            buffer[e.key] =
                    if (e.key == key) {
                        value
                    }
                    else {
                        e.value
                    }
        }

        return BundleMap(buffer)
    }



    fun insertEntry(
            key: PositionedObjectPath,
            value: T
    ): BundleMap<T> {
        check(! values.containsKey(key.objectPath)) { "Already exists: $key" }
        check(0 <= key.positionIndex.value &&
                key.positionIndex.value <= values.size) {
            "Index must be in [0, ${values.size}]"
        }

        val buffer = mutableMapOf<ObjectPath, T>()

        val iterator = values.entries.iterator()

        var nextIndex = 0
        while (true) {
            if (nextIndex == key.positionIndex.value) {
                break
            }
            nextIndex++

            val entry = iterator.next()
            buffer[entry.key] = entry.value
        }

        buffer[key.objectPath] = value

        while (iterator.hasNext()) {
            val entry = iterator.next()
            buffer[entry.key] = entry.value
        }

        return BundleMap(buffer)
    }


    fun removeKey(
            key: ObjectPath
    ): BundleMap<T> {
        check(values.containsKey(key)) { "Not found: $key" }

        val buffer = mutableMapOf<ObjectPath, T>()

        for (e in values) {
            if (e.key == key) {
                continue
            }

            buffer[e.key] = e.value
        }

        return BundleMap(buffer)
    }


    override fun toString(): String {
        return values.toString()
    }
}