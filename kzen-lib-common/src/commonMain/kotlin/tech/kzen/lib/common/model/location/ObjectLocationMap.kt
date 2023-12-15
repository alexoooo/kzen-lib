package tech.kzen.lib.common.model.location

import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.toPersistentMap


data class ObjectLocationMap<T>(
    val map: PersistentMap<ObjectLocation, T>
):
    ObjectLocator
{
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
        get() = map.size


    fun isEmpty(): Boolean {
        return map.isEmpty()
    }


    override fun locate(reference: ObjectReference): ObjectLocation {
        return locateOptional(reference)
            ?: throw IllegalArgumentException(
                "Missing: $reference | ${map.keys.map { it.documentPath }.toSet()}")
    }


    override fun locate(
        reference: ObjectReference,
        host: ObjectReferenceHost
    ): ObjectLocation {
        return locateOptional(reference, host)
            ?: throw IllegalArgumentException(
                "Missing: $host - $reference | ${map.keys.map { it.documentPath }.toSet()}")
    }


//    fun locateOptional(reference: ObjectReference): ObjectLocation? {
//        val matches = locateAll(reference)
//
//        check(matches.values.size <= 1) {
//            "Ambiguous: $reference - $matches"
//        }
//
//        if (matches.values.isEmpty()) {
//            return null
//        }
//        return matches.values.iterator().next()
//    }
//
//
//    fun locateOptional(
//        reference: ObjectReference,
//        host: ObjectReferenceHost
//    ): ObjectLocation? {
//        val matches = locateAll(reference, host)
//
//        check(matches.values.size <= 1) {
//            "Ambiguous: $host - $reference - $matches"
//        }
//
//        if (matches.values.isEmpty()) {
//            return null
//        }
//        return matches.values.iterator().next()
//    }
//
//
//    fun locateAll(reference: ObjectReference): ObjectLocationSet {
//        return locateAll(reference, ObjectReferenceHost.global)
//    }


    override fun locateAll(
        reference: ObjectReference,
        host: ObjectReferenceHost
    ): ObjectLocationSet {
        val locator = locator()
        return locator.locateAll(reference, host)
    }


    private fun locator(): ObjectLocator {
        if (locatorCache == null) {
            locatorCache = ObjectLocationSet.Locator()
            locatorCache!!.addAll(map.keys)
        }
        return locatorCache!!
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun containsKey(objectLocation: ObjectLocation): Boolean {
//        return values.containsKey(objectLocation)
//    }

    operator fun contains(objectLocation: ObjectLocation): Boolean {
        return objectLocation in map
    }


    operator fun get(objectLocation: ObjectLocation): T? {
        return map[objectLocation]
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun filterObjectLocations(allowed: Set<ObjectLocation>): ObjectLocationMap<T> {
        return ObjectLocationMap(map
            .filter { e -> e.key in allowed }
            .toPersistentMap())
    }


    fun filterDocumentNestings(allowed: Set<DocumentNesting>): ObjectLocationMap<T> {
        return ObjectLocationMap(map
            .filter { e -> allowed.any(e.key.documentPath::startsWith) }
            .toPersistentMap())
    }


    fun filterBy(predicate: (Pair<ObjectLocation, T>) -> Boolean): ObjectLocationMap<T> {
        return ObjectLocationMap(map
            .filter { predicate(it.toPair()) }
            .toPersistentMap())
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun put(objectLocation: ObjectLocation, instance: T): ObjectLocationMap<T> {
        return ObjectLocationMap(map.put(objectLocation, instance))
    }
}