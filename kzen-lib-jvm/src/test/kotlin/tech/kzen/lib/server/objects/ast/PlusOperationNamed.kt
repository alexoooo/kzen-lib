package tech.kzen.lib.server.objects.ast

import tech.kzen.lib.common.reflect.Reflect


@Reflect
class PlusOperationNamed(
    val addends: Map<String, DoubleExpression>
): DoubleExpression {
    override fun evaluate(): Double {
        var sum = 0.0
        for (operand in addends) {
            sum += operand.value.evaluate()
        }
        return sum
    }
}