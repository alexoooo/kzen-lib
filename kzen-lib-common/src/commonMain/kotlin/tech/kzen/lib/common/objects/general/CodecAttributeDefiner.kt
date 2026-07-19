package tech.kzen.lib.common.objects.general

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.codec.NotationCodec


/**
 * An [AttributeDefiner] that binds one attribute through a [NotationCodec], removing the fetch-cast-wrap
 * boilerplate each spec definer used to repeat. A concrete `@Reflect object` subclass supplies the codec
 * and the read strategy; notation selects it via `by:` exactly as before (this base is abstract, so it is
 * never registered or instantiated on its own).
 *
 * @param inheritanceMerge  `true`  reads the attribute merged across the inheritance chain (archetype
 *                                  defaults fold in), via `mergeAttribute`; `false` reads the object's own
 *                                  attribute verbatim (insertion order preserved), via `firstAttribute`.
 */
abstract class CodecAttributeDefiner<T>(
    private val codec: NotationCodec<T>,
    private val inheritanceMerge: Boolean
): AttributeDefiner {
    override fun define(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        graphStructure: GraphStructure,
        partialGraphDefinition: GraphDefinition,
        partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
        val graphNotation = graphStructure.graphNotation

        val attributeNotation = (
            if (inheritanceMerge) {
                graphNotation.mergeAttribute(objectLocation, attributeName)
            }
            else {
                graphNotation.firstAttribute(objectLocation, attributeName.asAttributePath())
            })
            ?: return AttributeDefinitionAttempt.failure(
                "'$attributeName' attribute notation not found: $objectLocation")

        return try {
            AttributeDefinitionAttempt.success(
                ValueAttributeDefinition(codec.parse(attributeNotation)))
        }
        catch (e: Exception) {
            AttributeDefinitionAttempt.failure(
                "Invalid '$attributeName' notation ($objectLocation): ${e.message}")
        }
    }
}
