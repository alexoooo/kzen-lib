package tech.kzen.lib.common.model.locate


data class ObjectLocationSet(
        val values: Set<ObjectLocation>
) {
    companion object {
        val empty = ObjectLocationSet(setOf())


        fun locateAll(
                universe: Collection<ObjectLocation>,
                reference: ObjectReference,
                host: ObjectReferenceHost
        ): ObjectLocationSet {
            val candidates = mutableSetOf<ObjectLocation>()
            for (candidate in universe) {
                if (reference.name != candidate.objectPath.name ||
                        reference.path != null && reference.path != candidate.documentPath ||
                        reference.nesting != null && reference.nesting != candidate.objectPath.nesting) {
                    continue
                }

                candidates.add(candidate)
            }

            // TODO: perform reverse breadth first search
            if (candidates.size > 1 && host.documentPath != null) {
                val iterator = candidates.iterator()
                while (iterator.hasNext()) {
                    val objectLocation = iterator.next()
                    if (host.documentPath != objectLocation.documentPath) {
                        iterator.remove()
                    }
                }
            }

            return ObjectLocationSet(candidates)
        }
    }
}