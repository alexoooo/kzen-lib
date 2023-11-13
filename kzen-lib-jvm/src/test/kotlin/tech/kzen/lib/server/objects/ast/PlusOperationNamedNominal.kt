package tech.kzen.lib.server.objects.ast

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class PlusOperationNamedNominal(
    val addends: Map<String, ObjectLocation>
): DoubleExpression {
    override fun evaluate(): Double {
        TODO()
    }
}