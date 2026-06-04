package tech.kzen.lib.common.objects.base

import tech.kzen.lib.common.api.AttributeCreator
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.ObjectDefinition
import tech.kzen.lib.common.model.definition.ServiceAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.context.environment.GraphEnvironment


/**
 * Fills a constructor parameter annotated [tech.kzen.lib.common.reflect.Service] by resolving it
 * from the host [GraphEnvironment] (rather than from notation). Selected by [AttributeObjectCreator]
 * whenever the parameter's definition is a [ServiceAttributeDefinition].
 */
@Reflect
object ServiceAttributeCreator: AttributeCreator {
    override fun create(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        graphStructure: GraphStructure,
        objectDefinition: ObjectDefinition,
        partialGraphInstance: GraphInstance,
        environment: GraphEnvironment
    ): Any? {
        val attributeDefinition = objectDefinition.attributeDefinitions.map[attributeName]
            ?: throw IllegalArgumentException(
                "Service attribute definition missing: $objectLocation - $attributeName")

        check(attributeDefinition is ServiceAttributeDefinition) {
            "Service attribute expected: $objectLocation - $attributeName - $attributeDefinition"
        }

        return environment.resolve(attributeDefinition.serviceClassName)
    }
}
