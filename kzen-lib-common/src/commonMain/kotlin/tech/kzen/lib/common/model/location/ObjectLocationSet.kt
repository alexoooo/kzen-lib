package tech.kzen.lib.common.model.location

import tech.kzen.lib.common.model.obj.ObjectName


data class ObjectLocationSet(
        val values: Set<ObjectLocation>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = ObjectLocationSet(setOf())
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Locator {
        private val byName = mutableMapOf<ObjectName, MutableList<ObjectLocation>>()


        fun add(objectLocation: ObjectLocation) {
            val sameNameLocations = byName
                    .getOrPut(objectLocation.objectPath.name) {
                        mutableListOf()
                    }

            sameNameLocations.add(objectLocation)
        }


        fun addAll(objectLocations: Collection<ObjectLocation>) {
            for (objectLocation in objectLocations) {
                add(objectLocation)
            }
        }


        fun locateAll(reference: ObjectReference, host: ObjectReferenceHost): ObjectLocationSet {
            val objectName = reference.name.objectName
                ?: return empty

            val sameNameLocations = byName[objectName]
                ?: return empty

            val candidates = mutableSetOf<ObjectLocation>()

            for (candidate in sameNameLocations) {
                if (reference.path != null && reference.path != candidate.documentPath ||
                        reference.nesting != candidate.objectPath.nesting) {
                    continue
                }

                candidates.add(candidate)
            }

            // TODO: perform reverse breadth first search?
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