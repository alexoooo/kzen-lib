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
    class Locator: ObjectLocator {
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


        override fun locateAll(
            reference: ObjectReference,
            host: ObjectReferenceHost
        ): ObjectLocationSet {
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


        override fun locate(reference: ObjectReference): ObjectLocation {
            return locateOptional(reference)
                ?: throw IllegalArgumentException(missingMessage(reference, null))
        }


        override fun locate(
            reference: ObjectReference,
            host: ObjectReferenceHost
        ): ObjectLocation {
            return locateOptional(reference, host)
                ?: throw IllegalArgumentException(missingMessage(reference, host))
        }


        internal fun missingMessage(reference: ObjectReference, host: ObjectReferenceHost?): String {
            val sameName = reference.name.objectName?.let { byName[it] }.orEmpty()
            return LocateErrors.missingMessage(
                reference, host, sameName, byName.values.sumOf { it.size })
        }
    }
}