package tech.kzen.lib.common.exec.data.shape

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


class DataShapeTest {
    private val contract = DataContract(DataType.Record(listOf(
        DataField(FieldId("name"), DataType.Scalar(ScalarKind.Text)),
        DataField(FieldId("count"), DataType.Scalar(ScalarKind.Integer(32), nullable = true)))))


    @Test
    fun everyProvenanceAndStabilityRoundTripsThroughBothCodecs() {
        for (provenance in ShapeProvenance.entries) {
            val shapes = listOf(
                DataShape(contract, provenance, ShapeStability.Stable),
                DataShape(
                    contract,
                    provenance,
                    ShapeStability.Provisional(SampleCoverage(25, 1024, complete = true)),
                    listOf(
                        SchemaDiagnostic(
                            DiagnosticSeverity.Info,
                            "schema.sampled",
                            "Schema was sampled"),
                        SchemaDiagnostic(
                            DiagnosticSeverity.Warning,
                            "schema.lossy",
                            "A provider detail was omitted",
                            "part/column"),
                        SchemaDiagnostic(
                            DiagnosticSeverity.Error,
                            "schema.conflict",
                            "Declared and provider types conflict"))))

            for (shape in shapes) {
                assertEquals(shape, DataShape.ofExecutionValue(shape.asExecutionValue()))
                assertEquals(shape, Json.decodeFromString<DataShape>(Json.encodeToString(shape)))
            }
        }
    }


    @Test
    fun shapeResultCoversUnavailableAndObserved() {
        val results = listOf<DataShapeResult>(
            DataShapeResult.Unavailable,
            DataShapeResult.Observed(DataShape(
                contract,
                ShapeProvenance.Declared,
                ShapeStability.Stable)))

        for (result in results) {
            assertEquals(result, DataShapeResult.ofExecutionValue(result.asExecutionValue()))
            assertEquals(result, Json.decodeFromString<DataShapeResult>(Json.encodeToString(result)))
        }
    }


    @Test
    fun contractNativeMetadataRoundTripsInsideShape() {
        val field = FieldId("nested")
        val structural = DataType.Record(listOf(DataField(field, DataType.Opaque())))
        val contract = DataContract(structural, mapOf(
            DataTypePath.root to TypeMetadata(ClassName("example.Root"), emptyList(), false),
            DataTypePath(listOf(DataPathSegment.Field(field))) to
                    TypeMetadata(ClassName("example.Nested"), emptyList(), false)))
        val shape = DataShape(
            contract,
            ShapeProvenance.Carried,
            ShapeStability.Stable)

        assertEquals(shape, DataShape.ofExecutionValue(shape.asExecutionValue()))
        assertEquals(shape, Json.decodeFromString<DataShape>(Json.encodeToString(shape)))
    }


    @Test
    fun freezesDiagnosticsAndValidatesCoverageAndDiagnostics() {
        val diagnostics = mutableListOf(SchemaDiagnostic(
            DiagnosticSeverity.Info,
            "schema.info",
            "Information"))
        val shape = DataShape(
            contract,
            ShapeProvenance.RuntimeOnly,
            ShapeStability.Stable,
            diagnostics)
        diagnostics.clear()
        assertEquals(1, shape.diagnostics.size)

        assertFailsWith<DataException> { SampleCoverage(0) }
        assertFailsWith<DataException> { SampleCoverage(1, 0) }
        assertFailsWith<DataException> {
            SchemaDiagnostic(DiagnosticSeverity.Error, "", "message")
        }
        assertFailsWith<DataException> {
            SchemaDiagnostic(DiagnosticSeverity.Error, "schema.error", "")
        }
    }
}
