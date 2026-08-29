package tech.kzen.lib.common.exec.data.binding

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.platform.platformSynchronized


/** Concurrent Job/Logic output collector. Schema order is identity; repeated writes are last-write-wins. */
class ProducedBindingsBuilder(
    val schema: BindingSchema
) {
    private val lock = Any()
    private val values = mutableMapOf<BindingName, DataValue>()
    private val chronology = mutableListOf<BindingName>()

    fun set(name: BindingName, value: DataValue) {
        val definition = schema.find(name)
            ?: fail(DataProblem.invalidIdentifier, "Unknown produced binding: '$name'")
        validateBindingValue(definition, value)
        platformSynchronized(lock) {
            values[name] = value
            chronology.add(name)
        }
    }

    /** Trace metadata only; binding enumeration remains in schema order. */
    fun yieldChronology(): List<BindingName> =
        platformSynchronized(lock) { chronology.toList() }

    fun settle(): DataBindings = platformSynchronized(lock) {
        val states = schema.definitions.map { definition ->
            val value = values[definition.name]
            when {
                value != null -> BindingState.Bound(value, BindingOrigin.Produced)
                definition.presence is DataPresence.Defaulted -> BindingState.Bound(
                    definition.presence.default.snapshot.asDataValue(),
                    BindingOrigin.Defaulted)
                definition.presence == DataPresence.Required ->
                    fail(DataProblem.missingValue, "Required output '${definition.name}' was not produced")
                else -> BindingState.Unbound
            }
        }
        DataBindings.produced(schema, states)
    }

    private fun fail(code: String, message: String): Nothing =
        throw DataException(DataProblem(code, message))
}
