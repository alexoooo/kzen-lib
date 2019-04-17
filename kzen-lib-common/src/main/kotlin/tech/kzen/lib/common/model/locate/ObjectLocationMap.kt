package tech.kzen.lib.common.model.locate

import tech.kzen.lib.platform.collect.PersistentMap


data class ObjectLocationMap<T>(
        val values: PersistentMap<ObjectLocation, T>
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun locate(reference: ObjectReference): ObjectLocation {
        return locateOptional(reference)
                ?: throw IllegalArgumentException("Missing: $reference")
    }


    fun locate(host: ObjectLocation, reference: ObjectReference): ObjectLocation {
        return locateOptional(host, reference)
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


    fun locateOptional(host: ObjectLocation, reference: ObjectReference): ObjectLocation? {
        val matches = locateAll(host, reference)

        check(matches.values.size <= 1) { "Ambiguous: $host - $reference - $matches" }

        if (matches.values.isEmpty()) {
            return null
        }
        return matches.values.iterator().next()
    }


    fun locateAll(reference: ObjectReference): ObjectLocationSet {
        val candidates = mutableSetOf<ObjectLocation>()
        for (candidate in values.keys) {
            if (reference.name != candidate.objectPath.name ||
                    reference.path != null && reference.path != candidate.documentPath ||
                    reference.nesting != null && reference.nesting != candidate.objectPath.nesting) {
                continue
            }

            candidates.add(candidate)
        }
        return ObjectLocationSet(candidates)
    }


    fun locateAll(host: ObjectLocation, reference: ObjectReference): ObjectLocationSet {
        // TODO: breadth-first-search from host
        return locateAll(reference)
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun names(): Set<ObjectName> =
//            values.keys.map(ObjectLocation::name).toSet()


    fun containsKey(objectLocation: ObjectLocation): Boolean {
        return values.containsKey(objectLocation)
    }


    fun get(objectLocation: ObjectLocation): T? {
        return values[objectLocation]
    }

//    fun get(objectLocation: ObjectLocation): T {
//        val instance = find(objectLocation)
//        check(instance != null) { "Not found: $objectLocation" }
//        return instance
//    }
//
//
//    fun find(objectLocation: ObjectLocation): T? {
//        return values[objectLocation]
//    }



//    fun get(name: ObjectName): T {
//        val instance = find(name)
//        check(instance != null) { "Not found: $name" }
//        return instance
//    }
//
//
//    fun find(name: ObjectName): T? {
//        val candidates = mutableListOf<T>()
//        for (e in values) {
//            if (e.key.name == name) {
//                candidates.add(e.value)
//            }
//        }
//
//        if (candidates.isEmpty()) {
//            return null
//        }
//
//        check(candidates.size == 1) { "Ambiguous: $name - $candidates" }
//
//        return candidates[0]
//    }


    //-----------------------------------------------------------------------------------------------------------------
    fun put(objectLocation: ObjectLocation, instance: T): ObjectLocationMap<T> {
        return ObjectLocationMap(values.put(objectLocation, instance))
    }
}