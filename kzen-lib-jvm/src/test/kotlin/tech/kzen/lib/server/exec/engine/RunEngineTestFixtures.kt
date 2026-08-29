package tech.kzen.lib.server.exec.engine

import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.engine.Address
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.data.binding.BindingDefinition
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.materializeJvm
import tech.kzen.lib.common.service.store.normal.ObjectStableId


// Fixtures shared across the RunEngine test classes; single-class fixtures stay private in their class.


internal val rootId = ObjectStableId("root")
internal val mainBindingName = BindingName("main")
internal val mainBindingSchema = BindingSchema.of(BindingDefinition(
    mainBindingName,
    DataContract(DataType.Dynamic(nullable = true))))
internal val mainSignature = LogicSignature(BindingSchema.empty, mainBindingSchema)


internal fun bindingsOfMain(value: Any?): DataBindings =
    DataBindings.bind(mainBindingSchema, mainBindingName to LiteralDataValues.lift(value))


internal fun emptyBindings(): DataBindings = DataBindings.bind(BindingSchema.empty)


internal fun DataBindings.mainComponentValue(): Any? =
    requireValue(mainBindingName).materializeJvm()


internal fun logicOf(block: suspend (Execution) -> DataBindings): Logic =
    object: Logic {
        override fun signature() = mainSignature
        override suspend fun run(execution: Execution) = block(execution)
    }


// Park at checkpoints indefinitely (until cancelled or migrated away) — a [logicOf] tail for fixtures
// that must stay live at a wavefront.
internal suspend fun parkForever(execution: Execution): Nothing {
    while (true) {
        execution.checkpoint()
    }
}


/** Emits i = 1..n, with a checkpoint *before* each emit (so a fresh pause sits before any value). */
internal class StepsLogic(private val n: Int): Logic {
    override fun signature() = mainSignature

    override suspend fun run(execution: Execution): DataBindings {
        for (i in 1 .. n) {
            execution.checkpoint()
            execution.emit(Address.of("i"), ExecutionValue.of(i.toLong()))
            execution.log(ExecutionValue.of("i=$i"))
        }
        return bindingsOfMain(n)
    }
}


/** Named boundary before each element: checkpoint(step-i) then emit i, for i = 1..n. */
internal fun namedStepsLogic(n: Int, idPrefix: String = "step"): Logic =
    logicOf { execution ->
        for (i in 1 .. n) {
            execution.checkpoint(ObjectStableId("$idPrefix-$i"))
            execution.emit(Address.of("i"), ExecutionValue.of(i.toLong()))
        }
        bindingsOfMain(n)
    }


/** A migration-carryable accumulator that records whether it was disposed (the removed-element case). */
internal class CloseableCounter(var count: Long): AutoCloseable {
    @Volatile
    var closed = false
        private set

    override fun close() {
        closed = true
    }
}
