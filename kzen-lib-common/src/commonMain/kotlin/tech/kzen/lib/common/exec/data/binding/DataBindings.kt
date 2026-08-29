package tech.kzen.lib.common.exec.data.binding

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.value.DataSnapshot
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.SnapshotPolicy
import tech.kzen.lib.common.exec.data.value.SnapshotResult


sealed interface BindingState {
    data object Unbound: BindingState
    data class Bound(
        val value: DataValue,
        val origin: BindingOrigin
    ): BindingState
}


enum class BindingOrigin {
    Supplied,
    Defaulted,
    Produced
}


class DataBindings private constructor(
    val schema: BindingSchema,
    states: List<BindingState>
) {
    companion object {
        /** Builds an enumerable request, retaining omitted required bindings as [BindingState.Unbound]. */
        fun assemble(
            schema: BindingSchema,
            supplied: List<Pair<BindingName, DataValue>> = emptyList()
        ): DataBindings = construct(schema, supplied, requireRequired = false)

        /** Builds executable inputs: defaults are applied once and every required binding must be supplied. */
        fun bind(
            schema: BindingSchema,
            supplied: List<Pair<BindingName, DataValue>> = emptyList()
        ): DataBindings = construct(schema, supplied, requireRequired = true)

        fun bind(schema: BindingSchema, vararg supplied: Pair<BindingName, DataValue>): DataBindings =
            bind(schema, supplied.toList())

        private fun construct(
            schema: BindingSchema,
            supplied: List<Pair<BindingName, DataValue>>,
            requireRequired: Boolean
        ): DataBindings {
            val duplicate = supplied
                .groupingBy { it.first }
                .eachCount()
                .entries
                .firstOrNull { it.value > 1 }
                ?.key
            if (duplicate != null) {
                fail(DataProblem.invalidIdentifier, "Duplicate supplied binding: '$duplicate'")
            }

            val suppliedByName = supplied.associate { (name, value) ->
                if (schema.find(name) == null) {
                    fail(DataProblem.invalidIdentifier, "Unknown supplied binding: '$name'")
                }
                name to value
            }

            val states = schema.definitions.map { definition ->
                val suppliedValue = suppliedByName[definition.name]
                when {
                    suppliedValue != null -> {
                        validateBindingValue(definition, suppliedValue)
                        BindingState.Bound(suppliedValue, BindingOrigin.Supplied)
                    }
                    definition.presence is DataPresence.Defaulted -> {
                        val value = definition.presence.default.snapshot.asDataValue()
                        validateBindingValue(definition, value)
                        BindingState.Bound(value, BindingOrigin.Defaulted)
                    }
                    requireRequired && definition.presence == DataPresence.Required ->
                        fail(DataProblem.missingValue, "Required binding '${definition.name}' is unbound")
                    else -> BindingState.Unbound
                }
            }
            return DataBindings(schema, states)
        }

        internal fun produced(schema: BindingSchema, states: List<BindingState>): DataBindings =
            DataBindings(schema, states)

        private fun fail(code: String, message: String): Nothing =
            throw DataException(DataProblem(code, message))
    }

    private val states: List<BindingState> = states.toList()

    init {
        require(this.states.size == schema.definitions.size) {
            "Binding state count must match schema definition count"
        }
    }

    operator fun get(name: BindingName): BindingState {
        val index = schema.indexOf(name)
        if (index < 0) {
            fail(DataProblem.invalidIdentifier, "Unknown binding: '$name'")
        }
        return states[index]
    }

    fun requireValue(name: BindingName): DataValue =
        when (val state = get(name)) {
            BindingState.Unbound -> fail(DataProblem.missingValue, "Binding '$name' is unbound")
            is BindingState.Bound -> state.value
        }

    fun entries(): List<Pair<BindingDefinition, BindingState>> =
        schema.definitions.zip(states)

    /** Whole-binding display policy only; sensitivity is deliberately not propagated to another binding. */
    fun snapshot(
        name: BindingName,
        policy: SnapshotPolicy = SnapshotPolicy()
    ): SnapshotResult {
        val definition = schema[name]
        val value = when (val state = get(name)) {
            BindingState.Unbound -> return SnapshotResult.Rejected(listOf(DataProblem(
                DataProblem.missingValue,
                "Binding '$name' is unbound")))
            is BindingState.Bound -> state.value
        }
        return DataSnapshot.capture(value, policy, definition.sensitive)
    }

    private fun fail(code: String, message: String): Nothing =
        throw DataException(DataProblem(code, message))
}
