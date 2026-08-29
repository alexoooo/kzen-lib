package tech.kzen.lib.server.exec.engine

import kotlinx.coroutines.runBlocking
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.binding.BindingDefinition
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.lib.common.exec.data.binding.BindingOrigin
import tech.kzen.lib.common.exec.data.binding.BindingSchema
import tech.kzen.lib.common.exec.data.binding.BindingState
import tech.kzen.lib.common.exec.data.binding.DataBindings
import tech.kzen.lib.common.exec.data.binding.DataDefault
import tech.kzen.lib.common.exec.data.binding.DataPresence
import tech.kzen.lib.common.exec.data.binding.ProducedBindingsBuilder
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataSnapshot
import tech.kzen.lib.common.exec.data.value.DefaultDataAdapterRegistry
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.materializeJvm
import tech.kzen.lib.common.exec.engine.Execution
import tech.kzen.lib.common.exec.engine.Logic
import tech.kzen.lib.common.exec.engine.LogicFailure
import tech.kzen.lib.common.exec.engine.LogicSignature
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.service.store.normal.ObjectStableId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame


class RunEngineBindingTest {
    private val main = BindingName("main")
    private val textType = DataType.Scalar(ScalarKind.Text)
    private val nullableText = DataType.Scalar(ScalarKind.Text, nullable = true)


    @Test
    fun bindingNativeRootPreservesDefaultNullAndNativeIdentityThroughSettlement() = runBlocking {
        val registry = DefaultDataAdapterRegistry()
        val token = NativeToken("same")
        val tokenValue = registry.lift(token)
        val signature = LogicSignature(
            BindingSchema.of(
                BindingDefinition(BindingName("token"), tokenValue.contract),
                BindingDefinition(
                    BindingName("fallback"),
                    DataContract(textType),
                    DataPresence.Defaulted(DataDefault(
                        DataSnapshot.of(textType, TextExecutionValue("default"))))),
                BindingDefinition(BindingName("nullable"), DataContract(nullableText))),
            BindingSchema.of(BindingDefinition(main, tokenValue.contract)))

        val logic = bindingLogic(signature) { execution ->
            assertEquals(
                BindingOrigin.Defaulted,
                assertIs<BindingState.Bound>(execution.inputs[BindingName("fallback")]).origin)
            assertEquals(
                BindingOrigin.Supplied,
                assertIs<BindingState.Bound>(execution.inputs[BindingName("nullable")]).origin)
            val output = ProducedBindingsBuilder(signature.outputs)
            output.set(main, execution.inputs.requireValue(BindingName("token")))
            output.settle()
        }
        val inputs = DataBindings.bind(
            signature.inputs,
            BindingName("token") to tokenValue,
            BindingName("nullable") to LiteralDataValues.lift(null, DataContract(nullableText)))
        val engine = RunEngine(logic, rootId, inputs)

        try {
            engine.resume()
            val success = assertIs<Outcome.Success>(engine.await())
            assertSame(token, success.value.requireValue(main).materializeJvm())
        }
        finally {
            engine.close()
            registry.close()
        }
    }


    @Test
    fun hostedBindingsAreReadableAfterChildSettlement() = runBlocking {
        val childSignature = LogicSignature(
            BindingSchema.of(BindingDefinition(
                BindingName("value"), DataContract(textType), DataPresence.Required)),
            BindingSchema.of(BindingDefinition(main, DataContract(textType))))
        val child = bindingLogic(childSignature) { execution ->
            ProducedBindingsBuilder(childSignature.outputs).apply {
                set(main, execution.inputs.requireValue(BindingName("value")))
            }.settle()
        }
        val parentSignature = LogicSignature(
            BindingSchema.empty,
            BindingSchema.of(BindingDefinition(main, DataContract(textType))))
        val parent = bindingLogic(parentSignature) { execution ->
            val childInputs = DataBindings.bind(
                childSignature.inputs,
                BindingName("value") to LiteralDataValues.lift("hosted"))
            val hosted = execution.host(
                ObjectStableId("child"), child, childInputs, ObjectStableId("call"))
            ProducedBindingsBuilder(parentSignature.outputs).apply {
                set(main, hosted.requireValue(main))
            }.settle()
        }
        val engine = RunEngine(parent, rootId, DataBindings.bind(parentSignature.inputs))

        try {
            engine.resume()
            val success = assertIs<Outcome.Success>(engine.await())
            assertEquals("hosted", success.value.mainComponentValue())
        }
        finally {
            engine.close()
        }
    }


    @Test
    fun hostedMissingInputAndOutputFailAtTheChildLikeLegacyFailure() = runBlocking {
        val requiredInput = LogicSignature(
            BindingSchema.of(BindingDefinition(BindingName("required"), DataContract(textType))),
            BindingSchema.empty)
        val missingInputChild = bindingLogic(requiredInput) {
            error("child code must not run when required input is absent")
        }
        val missingInput = runHostingFailure(
            missingInputChild,
            DataBindings.assemble(requiredInput.inputs),
            ObjectStableId("missing-input"))
        assertEquals(ObjectStableId("missing-input"), missingInput.at)
        assertEquals(true, missingInput.message.contains("Required binding 'required' is unbound"))

        val requiredOutput = LogicSignature(
            BindingSchema.empty,
            BindingSchema.of(BindingDefinition(main, DataContract(textType))))
        val missingOutputChild = bindingLogic(requiredOutput) {
            DataBindings.assemble(requiredOutput.outputs)
        }
        val missingOutput = runHostingFailure(
            missingOutputChild,
            DataBindings.bind(requiredOutput.inputs),
            ObjectStableId("missing-output"))
        assertEquals(ObjectStableId("missing-output"), missingOutput.at)
        assertEquals(true, missingOutput.message.contains("Required output 'main' was not produced"))

        val legacyId = ObjectStableId("legacy-failure")
        val legacy = object: Logic {
            override fun signature() = LogicSignature.empty
            override suspend fun run(execution: Execution): DataBindings =
                throw LogicFailure("legacy boom")
        }
        val legacyFailure = runLegacyHostingFailure(legacy, legacyId)
        assertEquals(legacyId, legacyFailure.at)
        assertEquals("legacy boom", legacyFailure.message)
    }


    private suspend fun runHostingFailure(
        child: Logic,
        childInputs: DataBindings,
        childId: ObjectStableId
    ): Outcome.Failed {
        val parent = bindingLogic(LogicSignature.empty) { execution ->
            execution.host(childId, child, childInputs)
            DataBindings.bind(BindingSchema.empty)
        }
        return runFailure(parent, DataBindings.bind(BindingSchema.empty))
    }


    private suspend fun runLegacyHostingFailure(child: Logic, childId: ObjectStableId): Outcome.Failed {
        val parent = bindingLogic(LogicSignature.empty) { execution ->
            execution.host(childId, child)
            DataBindings.bind(BindingSchema.empty)
        }
        return runFailure(parent, DataBindings.bind(BindingSchema.empty))
    }


    private suspend fun runFailure(logic: Logic, inputs: DataBindings): Outcome.Failed {
        val engine = RunEngine(logic, rootId, inputs)
        return try {
            engine.resume()
            assertIs<Outcome.Failed>(engine.await())
        }
        finally {
            engine.close()
        }
    }


    private fun bindingLogic(
        signature: LogicSignature,
        block: suspend (Execution) -> DataBindings
    ): Logic = object: Logic {
        override fun signature() = signature
        override suspend fun run(execution: Execution) = block(execution)
    }


    private data class NativeToken(val value: String)
}
