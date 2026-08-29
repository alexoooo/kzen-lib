package tech.kzen.lib.common.exec.data.shape

import kotlinx.serialization.Serializable
import tech.kzen.lib.common.exec.data.type.DataContract


@Serializable(with = DataShapeSerializer::class)
class DataShape(
    val itemType: DataContract,
    val provenance: ShapeProvenance,
    val stability: ShapeStability,
    diagnostics: List<SchemaDiagnostic> = emptyList()
) {
    companion object {
        fun ofExecutionValue(executionValue: tech.kzen.lib.common.exec.ExecutionValue): DataShape =
            DataShapeExecutionValue.decode(executionValue)
    }

    val diagnostics: List<SchemaDiagnostic> = diagnostics.toList()

    fun asExecutionValue(): tech.kzen.lib.common.exec.MapExecutionValue =
        DataShapeExecutionValue.encode(this)

    override fun equals(other: Any?): Boolean =
        this === other || other is DataShape &&
                itemType == other.itemType &&
                provenance == other.provenance &&
                stability == other.stability &&
                diagnostics == other.diagnostics

    override fun hashCode(): Int {
        var result = itemType.hashCode()
        result = 31 * result + provenance.hashCode()
        result = 31 * result + stability.hashCode()
        result = 31 * result + diagnostics.hashCode()
        return result
    }

    override fun toString(): String =
        "DataShape(itemType=$itemType, provenance=$provenance, stability=$stability, diagnostics=$diagnostics)"
}
