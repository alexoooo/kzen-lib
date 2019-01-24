package tech.kzen.lib.common.api.model


class ObjectMap<T>(
        val values: Map<ObjectLocation, T>
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
        check(matches.size <= 1) { "Ambiguous: $reference - $matches" }

        if (matches.isEmpty()) {
            return null
        }
        return matches.iterator().next()
    }


    fun locateOptional(host: ObjectLocation, reference: ObjectReference): ObjectLocation? {
        val matches = locateAll(host, reference)

        check(matches.size <= 1) { "Ambiguous: $host - $reference - $matches" }

        if (matches.isEmpty()) {
            return null
        }
        return matches.iterator().next()
    }


    fun locateAll(reference: ObjectReference): Set<ObjectLocation> {
        val candidates = mutableSetOf<ObjectLocation>()
        for (candidate in values.keys) {
            if (reference.name != candidate.objectPath.name ||
                    reference.path != null && reference.path != candidate.bundlePath ||
                    reference.nesting != null && reference.nesting != candidate.objectPath.nesting) {
                continue
            }

            candidates.add(candidate)
        }
        return candidates
    }


    fun locateAll(host: ObjectLocation, reference: ObjectReference): Set<ObjectLocation> {
        // TODO: breadth-first-search from host
        return locateAll(reference)
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun names(): Set<ObjectName> =
//            values.keys.map(ObjectLocation::name).toSet()


    fun get(objectLocation: ObjectLocation): T {
        val instance = find(objectLocation)
        check(instance != null) { "Not found: $objectLocation" }
        return instance
    }


    fun find(objectLocation: ObjectLocation): T? {
        return values[objectLocation]
    }



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
}