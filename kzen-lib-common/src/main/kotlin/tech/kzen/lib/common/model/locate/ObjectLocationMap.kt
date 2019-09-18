package tech.kzen.lib.common.model.locate

import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.toPersistentMap


data class ObjectLocationMap<T>(
        val values: PersistentMap<ObjectLocation, T>
) {
    //-----------------------------------------------------------------------------------------------------------------
    val size: Int
        get() = values.size


    fun locate(reference: ObjectReference): ObjectLocation {
        return locateOptional(reference)
                ?: throw IllegalArgumentException("Missing: $reference")
    }


    fun locate(
            reference: ObjectReference,
            host: ObjectReferenceHost
    ): ObjectLocation {
        return locateOptional(reference, host)
                ?: throw IllegalArgumentException("Missing: $host - $reference")
    }


    fun locateOptional(reference: ObjectReference): ObjectLocation? {
        val matches = locateAll(reference)
        check(matches.values.size <= 1) { "Ambiguous: $reference - $matches" }

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

        check(matches.values.size <= 1) { "Ambiguous: $host - $reference - $matches" }

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
        return ObjectLocationSet.locateAll(values.keys, reference, host)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun containsKey(objectLocation: ObjectLocation): Boolean {
        return values.containsKey(objectLocation)
    }


    operator fun get(objectLocation: ObjectLocation): T? {
        return values[objectLocation]
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun filter(allowed: Set<DocumentNesting>): ObjectLocationMap<T> {
        return ObjectLocationMap(values
                .filter { e -> allowed.any(e.key.documentPath::startsWith) }
                .toPersistentMap())
    }


    fun filter(predicate: (Pair<ObjectLocation, T>) -> Boolean): ObjectLocationMap<T> {
        return ObjectLocationMap(values
                .filter { predicate(it.toPair()) }
                .toPersistentMap())
    }


    fun put(objectLocation: ObjectLocation, instance: T): ObjectLocationMap<T> {
        return ObjectLocationMap(values.put(objectLocation, instance))
    }
}