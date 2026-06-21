package tech.kzen.lib.common.service.notation

import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdateInAttributeCommand


/**
 * Extension point for rewriting object references that are embedded *inside* free-form attribute values — e.g. a
 * Kotlin expression that names a prior step by its variable identifier — when that object is renamed.
 *
 * Unlike the typed reference attributes that [NotationReducer] adjusts generically (replacing the whole attribute
 * value with the new reference string), these references live within code that only the owning domain (e.g.
 * kzen-auto's script formulas) can parse, and must be rewritten in place. That domain knowledge is injected here
 * rather than baked into the generic reducer.
 *
 * Implementations MUST be platform-agnostic and deterministic, so a rename applied optimistically on the client
 * and authoritatively on the server produce identical results (matching digests, no forced refresh). The returned
 * commands are applied within the same rename refactor transition, alongside the typed-reference adjustments.
 */
interface CodeReferenceRewriter {
    fun renameObjectReferences(
        oldLocation: ObjectLocation,
        newLocation: ObjectLocation,
        graphDefinitionAttempt: GraphDefinitionAttempt
    ): List<UpdateInAttributeCommand>
}
