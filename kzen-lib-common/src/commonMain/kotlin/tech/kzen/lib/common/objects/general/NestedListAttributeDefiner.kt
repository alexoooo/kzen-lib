package tech.kzen.lib.common.objects.general

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ListAttributeDefinition
import tech.kzen.lib.common.model.definition.ReferenceAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.reflect.Reflect


/**
 * Auto-wires the list of objects nested directly under this object at the given attribute, in
 * document (insertion) order. Lets a parent reference its sub-objects by structure rather than an
 * explicit list of references — e.g. Script steps, where step order is the
 * order of the step objects in the document. Emits weak references so the constructor parameter
 * materializes as List<ObjectLocation> (see DefinitionAttributeCreator)
 */
@Reflect
object NestedListAttributeDefiner: AttributeDefiner {
    override fun define(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        graphStructure: GraphStructure,
        partialGraphDefinition: GraphDefinition,
        partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
        val documentNotation = graphStructure.graphNotation.documents[objectLocation.documentPath]
            ?: return AttributeDefinitionAttempt.success(ListAttributeDefinition(listOf()))

        val childReferences = documentNotation
            .directNestedObjectPaths(objectLocation.objectPath, attributeName)
            .map { childPath ->
                ReferenceAttributeDefinition(
                    ObjectLocation(objectLocation.documentPath, childPath).toReference(),
                    weak = true,
                    nullable = false)
            }

        return AttributeDefinitionAttempt.success(
            ListAttributeDefinition(childReferences))
    }
}
