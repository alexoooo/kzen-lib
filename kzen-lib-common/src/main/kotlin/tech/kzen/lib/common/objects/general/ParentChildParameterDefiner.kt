package tech.kzen.lib.common.objects.general

import tech.kzen.lib.common.api.ParameterDefiner
import tech.kzen.lib.common.context.ObjectGraph
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.definition.ListAttributeDefinition
import tech.kzen.lib.common.definition.AttributeDefinition
import tech.kzen.lib.common.definition.ReferenceAttributeDefinition
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.NotationTree
import tech.kzen.lib.common.notation.model.ScalarAttributeNotation
import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.AttributeNesting
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectReference


@Suppress("unused")
class ParentChildParameterDefiner: ParameterDefiner {
    companion object {
        private val parentPath = AttributeNesting.ofAttribute(
                AttributeName("parent"))
    }


    override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            projectNotation: NotationTree,
            projectMetadata: GraphMetadata,
            projectDefinition: GraphDefinition,
            objectGraph: ObjectGraph
    ): AttributeDefinition {
        val children = mutableListOf<ObjectReference>()

        for (e in projectNotation.coalesce.values) {
            val parentNotation = projectNotation
                    .transitiveParameter(e.key, parentPath)
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