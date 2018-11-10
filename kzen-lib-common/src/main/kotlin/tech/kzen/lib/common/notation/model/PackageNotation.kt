package tech.kzen.lib.common.notation.model


data class PackageNotation(
        val objects: Map<String, ObjectNotation>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = PackageNotation(mapOf())
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

    fun nameAt(index: Int): String {
        return objects.keys.toList()[index]
    }


    fun indexOf(objectName: String): Int {
        return objects.keys.indexOf(objectName)
    }


    fun equalsInOrder(other: PackageNotation): Boolean {
        return this == other &&
                objects.keys.toList() == other.objects.keys.toList()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun withModifiedObject(
            objectName: String,
            objectNotation: ObjectNotation
    ): PackageNotation {
        check(objects.containsKey(objectName)) { "Not found: $objectName" }

        val buffer = mutableMapOf<String, ObjectNotation>()

        for (e in objects) {
            buffer[e.key] =
                    if (e.key == objectName) {
                        objectNotation
                    }
                    else {
                        e.value
                    }
        }

        return PackageNotation(buffer)
    }


    fun withNewObject(
            objectName: String,
            objectNotation: ObjectNotation,
            index: Int = objects.size
    ): PackageNotation {
        check(! objects.containsKey(objectName)) { "Already exists: $objectName" }
        check(0 <= index && index <= objects.size) { "Index must be in [0, ${objects.size}]" }

        val buffer = mutableMapOf<String, ObjectNotation>()

        val iterator = objects.entries.iterator()

        var nextIndex = 0
        while (true) {
            if (nextIndex == index) {
                break
            }
            nextIndex++

            val entry = iterator.next()
            buffer[entry.key] = entry.value
        }

        buffer[objectName] = objectNotation

        while (iterator.hasNext()) {
            val entry = iterator.next()
            buffer[entry.key] = entry.value
        }

        return PackageNotation(buffer)
    }


    fun withoutObject(
            objectName: String
    ): PackageNotation {
        check(objects.containsKey(objectName)) { "Not found: $objectName" }

        val buffer = mutableMapOf<String, ObjectNotation>()

        for (e in objects) {
            if (e.key == objectName) {
                continue
            }

            buffer[e.key] = e.value
        }

        return PackageNotation(buffer)
    }
}
