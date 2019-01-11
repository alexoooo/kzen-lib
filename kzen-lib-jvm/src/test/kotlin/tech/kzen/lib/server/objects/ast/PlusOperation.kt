package tech.kzen.lib.server.objects.ast


@Suppress("unused")
class PlusOperation(
        private val addends: List<DoubleExpression>
): DoubleExpression {
    override fun evaluate(): Double {
        var sum = 0.0
        for (operand in addends) {
            sum += operand.evaluate()
        }
        return sum
    }
}