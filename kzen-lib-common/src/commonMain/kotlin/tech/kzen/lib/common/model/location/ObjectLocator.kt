package tech.kzen.lib.common.model.location


interface ObjectLocator {
    fun locateAll(
        reference: ObjectReference,
        host: ObjectReferenceHost
    ): ObjectLocationSet


    fun locateAll(reference: ObjectReference): ObjectLocationSet {
        return locateAll(reference, ObjectReferenceHost.global)
    }



    fun locate(reference: ObjectReference): ObjectLocation
//    {
//        return locateOptional(reference)
//            ?: throw IllegalArgumentException(
//                "Missing: $reference | ${values.keys.map { it.documentPath }.toSet()}")
//    }


    fun locate(
        reference: ObjectReference,
        host: ObjectReferenceHost
    ): ObjectLocation
//    {
//        return locateOptional(reference, host)
//            ?: throw IllegalArgumentException(
//                "Missing: $host - $reference | ${values.keys.map { it.documentPath }.toSet()}")
//    }


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
}