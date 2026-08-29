package tech.kzen.lib.common.exec.data.shape

import tech.kzen.lib.common.exec.BooleanExecutionValue
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract


object DataShapeExecutionValue {
    fun encode(shape: DataShape): MapExecutionValue =
        MapExecutionValue(mapOf(
            "itemType" to shape.itemType.asExecutionValue(),
            "provenance" to TextExecutionValue(shape.provenance.encode()),
            "stability" to shape.stability.asExecutionValue(),
            "diagnostics" to ListExecutionValue(shape.diagnostics.map { it.asExecutionValue() })))

    fun decode(executionValue: ExecutionValue): DataShape {
        val map = executionValue.requireMap("data shape")
        return DataShape(
            DataContract.ofExecutionValue(map.required("itemType")),
            decodeProvenance(map.text("provenance")),
            decodeStability(map.required("stability")),
            map.list("diagnostics").map { decodeDiagnostic(it) })
    }

    private fun decodeProvenance(encoded: String): ShapeProvenance =
        when (encoded) {
            "declared" -> ShapeProvenance.Declared
            "carried" -> ShapeProvenance.Carried
            "provider-reported" -> ShapeProvenance.ProviderReported
            "inferred" -> ShapeProvenance.Inferred
            "runtime-only" -> ShapeProvenance.RuntimeOnly
            else -> invalidShape("Unknown shape provenance '$encoded'")
        }

    private fun decodeStability(executionValue: ExecutionValue): ShapeStability {
        val map = executionValue.requireMap("shape stability")
        return when (val case = map.text("case")) {
            "stable" -> ShapeStability.Stable
            "provisional" -> {
                val coverage = map.required("coverage").requireMap("sample coverage")
                ShapeStability.Provisional(SampleCoverage(
                    coverage.text("observedItems").toLong(),
                    coverage.optionalText("observedBytes")?.toLong(),
                    coverage.boolean("complete")))
            }
            else -> invalidShape("Unknown shape stability '$case'")
        }
    }

    private fun decodeDiagnostic(executionValue: ExecutionValue): SchemaDiagnostic {
        val map = executionValue.requireMap("schema diagnostic")
        val severity = when (val encoded = map.text("severity")) {
            "info" -> DiagnosticSeverity.Info
            "warning" -> DiagnosticSeverity.Warning
            "error" -> DiagnosticSeverity.Error
            else -> invalidShape("Unknown diagnostic severity '$encoded'")
        }
        return SchemaDiagnostic(
            severity,
            map.text("code"),
            map.text("message"),
            map.optionalText("location"))
    }
}


private fun ShapeProvenance.encode(): String =
    when (this) {
        ShapeProvenance.Declared -> "declared"
        ShapeProvenance.Carried -> "carried"
        ShapeProvenance.ProviderReported -> "provider-reported"
        ShapeProvenance.Inferred -> "inferred"
        ShapeProvenance.RuntimeOnly -> "runtime-only"
    }


private fun ShapeStability.asExecutionValue(): MapExecutionValue =
    when (this) {
        ShapeStability.Stable -> MapExecutionValue(mapOf(
            "case" to TextExecutionValue("stable")))
        is ShapeStability.Provisional -> MapExecutionValue(mapOf(
            "case" to TextExecutionValue("provisional"),
            "coverage" to MapExecutionValue(mapOf(
                "observedItems" to TextExecutionValue(coverage.observedItems.toString()),
                "observedBytes" to coverage.observedBytes?.let { TextExecutionValue(it.toString()) }
                    .orNullExecutionValue(),
                "complete" to BooleanExecutionValue.of(coverage.complete)))))
    }


private fun SchemaDiagnostic.asExecutionValue(): MapExecutionValue =
    MapExecutionValue(mapOf(
        "severity" to TextExecutionValue(when (severity) {
            DiagnosticSeverity.Info -> "info"
            DiagnosticSeverity.Warning -> "warning"
            DiagnosticSeverity.Error -> "error"
        }),
        "code" to TextExecutionValue(code),
        "message" to TextExecutionValue(message),
        "location" to location?.let(::TextExecutionValue).orNullExecutionValue()))


private fun ExecutionValue?.orNullExecutionValue(): ExecutionValue = this ?: NullExecutionValue


private fun ExecutionValue.requireMap(label: String): MapExecutionValue =
    this as? MapExecutionValue ?: invalidShape("$label must be a map")


private fun MapExecutionValue.required(key: String): ExecutionValue =
    values[key] ?: invalidShape("Data shape is missing '$key'")


private fun MapExecutionValue.text(key: String): String =
    (required(key) as? TextExecutionValue)?.value
        ?: invalidShape("Data shape '$key' must be text")


private fun MapExecutionValue.optionalText(key: String): String? =
    when (val value = required(key)) {
        NullExecutionValue -> null
        is TextExecutionValue -> value.value
        else -> invalidShape("Data shape '$key' must be text or null")
    }


private fun MapExecutionValue.boolean(key: String): Boolean =
    (required(key) as? BooleanExecutionValue)?.value
        ?: invalidShape("Data shape '$key' must be boolean")


private fun MapExecutionValue.list(key: String): List<ExecutionValue> =
    (required(key) as? ListExecutionValue)?.values
        ?: invalidShape("Data shape '$key' must be a list")


private fun invalidShape(message: String): Nothing =
    throw DataException(DataProblem(DataProblem.invalidTypeEncoding, message))
