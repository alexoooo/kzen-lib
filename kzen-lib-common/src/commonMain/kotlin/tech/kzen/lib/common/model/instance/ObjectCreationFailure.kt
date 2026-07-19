package tech.kzen.lib.common.model.instance

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost


data class ObjectCreationFailure(
    val errorMessage: String,

    /** Strong references that could not be resolved / were required-but-empty (unsatisfiable objects). */
    val unsatisfiedReferences: List<UnsatisfiedReference> = listOf(),

    /** Locations of upstream objects whose own creation failed (this object was skipped, not attempted). */
    val failedDependencies: Set<ObjectLocation> = setOf()
) {
    data class UnsatisfiedReference(
        val objectReference: ObjectReference,
        val attributePath: AttributePath?,
        val host: ObjectReferenceHost
    ) {
        override fun toString(): String {
            return "$objectReference at ${attributePath ?: "<creator>"} @ $host"
        }
    }
}
