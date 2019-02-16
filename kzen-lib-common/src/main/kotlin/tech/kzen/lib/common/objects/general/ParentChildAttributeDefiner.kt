package tech.kzen.lib.common.objects.general

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.AttributePath
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectReference
import tech.kzen.lib.common.context.GraphInstance
import tech.kzen.lib.common.definition.AttributeDefinition
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.definition.ListAttributeDefinition
import tech.kzen.lib.common.definition.ReferenceAttributeDefinition
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.notation.model.ScalarAttributeNotation


@Suppress("unused")
class ParentChildAttributeDefiner: AttributeDefiner {
    companion object {
        private val parentPath = AttributePath.ofAttribute(
                AttributeName("parent"))
    }


    override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
    ): AttributeDefinition {
        val children = mutableListOf<ObjectReference>()

        for (e in graphStructure.graphNotation.coalesce.values) {
            val parentNotation = graphStructure.graphNotation
                    .transitiveAttribute(e.key, parentPath)
                    ?: continue

            val parentName =
                    (parentNotation as? ScalarAttributeNotation)?.value
                    ?: continue

            if (parentName != objectLocation.objectPath.name.value) {
                continue
            }

            children.add(e.key.toReference())
        }

        return ListAttributeDefinition(
                children.map { ReferenceAttributeDefinition(it) })
    }
}