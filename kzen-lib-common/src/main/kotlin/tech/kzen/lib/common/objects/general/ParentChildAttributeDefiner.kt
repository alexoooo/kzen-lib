package tech.kzen.lib.common.objects.general

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.context.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.context.definition.GraphDefinition
import tech.kzen.lib.common.context.definition.ListAttributeDefinition
import tech.kzen.lib.common.context.definition.ReferenceAttributeDefinition
import tech.kzen.lib.common.context.instance.GraphInstance
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation


@Suppress("unused")
class ParentChildAttributeDefiner: AttributeDefiner {
    companion object {
        private val parentAttributePath = AttributePath.ofName(
                AttributeName("parent"))
    }


    override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
        val children = mutableListOf<ObjectReference>()

        for (e in graphStructure.graphNotation.coalesce.values) {
            val parentNotation = graphStructure.graphNotation
                    .transitiveAttribute(e.key, parentAttributePath)
                    ?: continue

            val parentName =
                    (parentNotation as? ScalarAttributeNotation)?.value
                    ?: continue

            if (parentName != objectLocation.objectPath.name.value) {
                continue
            }

            children.add(e.key.toReference())
        }

        return AttributeDefinitionAttempt.success(
                ListAttributeDefinition(
                        children.map { ReferenceAttributeDefinition(it) }))
    }
}