package tech.kzen.lib.common.objects.general

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ListAttributeDefinition
import tech.kzen.lib.common.model.definition.ReferenceAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
object ParentChildAttributeDefiner: AttributeDefiner {
//    companion object {
        private val parentAttributePath = AttributePath.ofName(
                AttributeName("parent"))
//    }


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
                    .firstAttribute(e.key, parentAttributePath)
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