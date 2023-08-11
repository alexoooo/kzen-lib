package tech.kzen.lib.common.model.location

import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.toPersistentMap


data class ObjectLocationMap<T>(
        val values: PersistentMap<ObjectLocation, T>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val empty = ObjectLocationMap(mapOf<ObjectLocation, Any>().toPersistentMap())

        fun <T> empty(): ObjectLocationMap<T> {
            @Suppress("UNCHECKED_CAST")
            return empty as ObjectLocationMap<T>
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var locatorCache: ObjectLocationSet.Locator? = null


    val size: Int
        get() = values.size


    fun isEmpty(): Boolean {
        return values.isEmpty()
    }


    fun locate(reference: ObjectReference): ObjectLocation {
        return locateOptional(reference)
                ?: throw IllegalArgumentException(
                        "Missing: $reference | ${values.keys.map { it.documentPath }.toSet()}")
    }


    fun locate(
        reference: ObjectReference,
        host: ObjectReferenceHost
    ): ObjectLocation {
        return locateOptional(reference, host)
                ?: throw IllegalArgumentException(
                        "Missing: $host - $reference | ${values.keys.map { it.documentPath }.toSet()}")
    }


    fun locateOptional(reference: ObjectReference): ObjectLocation? {
        val matches = locateAll(reference)

        check(matches.values.size <= 1) {
            "Ambiguous: $reference - $matches"
        }

        if (matches.values.isEmpty()) {
            return null
        }
        return matches.values.iterator().next()
    }


    fun locateOptional(
        reference: ObjectReference,
        host: ObjectReferenceHost
    ): ObjectLocation? {
        val matches = locateAll(reference, host)

        check(matches.values.size <= 1) {
            "Ambiguous: $host - $reference - $matches"
        }

        if (matches.values.isEmpty()) {
            return null
        }
        return matches.values.iterator().next()
    }


    fun locateAll(reference: ObjectReference): ObjectLocationSet {
        return locateAll(reference, ObjectReferenceHost.global)
    }


    fun locateAll(
        reference: ObjectReference,
        host: ObjectReferenceHost
    ): ObjectLocationSet {
        if (locatorCache == null) {
            locatorCache = ObjectLocationSet.Locator()
            locatorCache!!.addAll(values.keys)
        }

        return locatorCache!!.locateAll(reference, host)
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun containsKey(objectLocation: ObjectLocation): Boolean {
//        return values.containsKey(objectLocation)
//    }

    operator fun contains(objectLocation: ObjectLocation): Boolean {
        return objectLocation in values
    }


    operator fun get(objectLocation: ObjectLocation): T? {
        return values[objectLocation]
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun filterObjectLocations(allowed: Set<ObjectLocation>): ObjectLocationMap<T> {
        return ObjectLocationMap(values
            .filter { e -> e.key in allowed }
            .toPersistentMap())
    }


    fun filterDocumentNestings(allowed: Set<DocumentNesting>): ObjectLocationMap<T> {
        return ObjectLocationMap(values
                .filter { e -> allowed.any(e.key.documentPath::startsWith) }
                .toPersistentMap())
    }


    fun filterBy(predicate: (Pair<ObjectLocation, T>) -> Boolean): ObjectLocationMap<T> {
        return ObjectLocationMap(values
                .filter { predicate(it.toPair()) }
                .toPersistentMap())
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun put(objectLocation: ObjectLocation, instance: T): ObjectLocationMap<T> {
        return ObjectLocationMap(values.put(objectLocation, instance))
    }
}