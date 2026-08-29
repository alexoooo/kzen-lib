package tech.kzen.lib.common.exec.data.binding

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataTypeAlgebra
import tech.kzen.lib.common.exec.data.type.TypeAcceptance
import tech.kzen.lib.common.exec.data.value.DataSnapshot
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible
import kotlin.jvm.JvmInline


@JvmInline
value class BindingName(val value: String) {
    init {
        if (value.isBlank()) {
            throw DataException(DataProblem(
                DataProblem.invalidIdentifier,
                "Binding name must not be blank"))
        }
        if (value != value.trim()) {
            throw DataException(DataProblem(
                DataProblem.invalidIdentifier,
                "Binding name must be canonical (no surrounding whitespace): '$value'"))
        }
    }

    override fun toString(): String = value
}


sealed interface DataPresence {
    data object Required: DataPresence
    data object Optional: DataPresence
    data class Defaulted(val default: DataDefault): DataPresence
}


data class DataDefault(
    val snapshot: DataSnapshot
)


data class BindingDefinition(
    val name: BindingName,
    val contract: DataContract,
    val presence: DataPresence = DataPresence.Required,
    val sensitive: Boolean = false
) {
    init {
        val default = (presence as? DataPresence.Defaulted)?.default
        if (default != null) {
            if (contract.nativeByPath.isNotEmpty()) {
                invalid(
                    DataProblem.nativeTypeMissing,
                    "Binding '$name' requires native values and cannot have a literal snapshot default")
            }
            when (val acceptance = DataTypeAlgebra.isAssignable(
                contract.structural, default.snapshot.type)) {
                TypeAcceptance.Accepted -> {}
                is TypeAcceptance.Rejected -> invalid(
                    acceptance.problem.code,
                    "Default for binding '$name' is incompatible: ${acceptance.problem.message}")
            }
        }
    }
}


class BindingSchema private constructor(
    definitions: List<BindingDefinition>
): Digestible {
    companion object {
        val empty = BindingSchema(emptyList())

        fun of(definitions: List<BindingDefinition>): BindingSchema = BindingSchema(definitions)

        fun of(vararg definitions: BindingDefinition): BindingSchema = BindingSchema(definitions.toList())
    }

    val definitions: List<BindingDefinition> = definitions.toList()
    private val byName: Map<BindingName, BindingDefinition>
    private val indexByName: Map<BindingName, Int>

    init {
        val duplicate = this.definitions
            .groupingBy { it.name }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        if (duplicate != null) {
            invalid(DataProblem.invalidIdentifier, "Duplicate binding declaration: '$duplicate'")
        }
        byName = this.definitions.associateBy { it.name }
        indexByName = this.definitions.mapIndexed { index, definition ->
            definition.name to index
        }.toMap()
    }

    operator fun get(name: BindingName): BindingDefinition =
        find(name) ?: invalid(DataProblem.invalidIdentifier, "Unknown binding: '$name'")

    fun find(name: BindingName): BindingDefinition? = byName[name]

    internal fun indexOf(name: BindingName): Int = indexByName[name] ?: -1

    override fun digest(sink: Digest.Sink) {
        sink.addInt(definitions.size)
        for (definition in definitions) {
            sink.addUtf8(definition.name.value)
            sink.addDigestible(definition.contract)
            when (val presence = definition.presence) {
                DataPresence.Required -> sink.addUtf8("required")
                DataPresence.Optional -> sink.addUtf8("optional")
                is DataPresence.Defaulted -> {
                    sink.addUtf8("defaulted")
                    sink.addDigestible(presence.default.snapshot)
                }
            }
            sink.addBoolean(definition.sensitive)
        }
    }
}


internal fun validateBindingValue(
    definition: BindingDefinition,
    value: tech.kzen.lib.common.exec.data.value.DataValue
) {
    val state = value.access.state(value.root)
    if (state == tech.kzen.lib.common.exec.data.value.DataState.Absent) {
        invalid(DataProblem.invalidState, "Binding '${definition.name}' cannot bind an absent root")
    }
    if (state == tech.kzen.lib.common.exec.data.value.DataState.Null &&
        !definition.contract.structural.nullable
    ) {
        invalid(DataProblem.invalidState, "Binding '${definition.name}' does not allow null")
    }

    if (definition.contract != value.contract) {
        when (val acceptance = DataTypeAlgebra.isAssignable(
            definition.contract.structural, value.contract.structural)) {
            TypeAcceptance.Accepted -> {}
            is TypeAcceptance.Rejected -> invalid(
                acceptance.problem.code,
                "Binding '${definition.name}' is incompatible: ${acceptance.problem.message}")
        }
    }

    for ((path, expectedNative) in definition.contract.nativeByPath) {
        val actualNative = value.contract.nativeByPath[path]
            ?: invalid(
                DataProblem.nativeTypeMissing,
                "Binding '${definition.name}' requires native metadata at $path")
        if (!expectedNative.nullable && actualNative.nullable) {
            invalid(
                DataProblem.nativeTypeIncompatible,
                "Binding '${definition.name}' requires non-null native metadata at $path")
        }
    }
}


private fun invalid(code: String, message: String): Nothing =
    throw DataException(DataProblem(code, message))
