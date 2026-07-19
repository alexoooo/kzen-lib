package tech.kzen.lib.common.model.definition

import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost


sealed class AttributeDefinitionAttempt {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun success(definition: AttributeDefinition): AttributeDefinitionSuccess {
            return AttributeDefinitionSuccess(definition)
        }

        fun failure(error: String): AttributeDefinitionFailure {
            return AttributeDefinitionFailure(error)
        }

        fun failure(
            error: String,
            unresolvedReference: ObjectReference?,
            referenceHost: ObjectReferenceHost?
        ): AttributeDefinitionFailure {
            return AttributeDefinitionFailure(error, unresolvedReference, referenceHost)
        }
    }
}


data class AttributeDefinitionSuccess(
    val value: AttributeDefinition
): AttributeDefinitionAttempt()


data class AttributeDefinitionFailure(
    val errorMessage: String,

    /** Which reference failed to resolve, when the failure is reference-shaped (else null). */
    val unresolvedReference: ObjectReference? = null,

    /** What the reference was resolved against (host scoping), when known. */
    val referenceHost: ObjectReferenceHost? = null
): AttributeDefinitionAttempt()
