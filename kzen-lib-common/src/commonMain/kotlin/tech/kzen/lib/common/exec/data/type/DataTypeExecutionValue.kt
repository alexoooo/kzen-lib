package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.BooleanExecutionValue
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem


fun DataType.asExecutionValue(): MapExecutionValue =
    when (this) {
        is DataType.Scalar -> typeValue(
            "scalar",
            "scalar" to kind.asExecutionValue(),
            "nullable" to BooleanExecutionValue.of(nullable))

        is DataType.Record -> typeValue(
            "record",
            "fields" to ListExecutionValue(fields.map { field ->
                MapExecutionValue(mapOf(
                    "id" to field.id.asExecutionValue(),
                    "type" to field.type.asExecutionValue(),
                    "optional" to BooleanExecutionValue.of(field.optional)))
            }),
            "nullable" to BooleanExecutionValue.of(nullable))

        is DataType.Mapping -> typeValue(
            "mapping",
            "key" to key.asExecutionValue(),
            "value" to value.asExecutionValue(),
            "nullable" to BooleanExecutionValue.of(nullable))

        is DataType.Listing -> typeValue(
            "listing",
            "element" to element.asExecutionValue(),
            "nullable" to BooleanExecutionValue.of(nullable))

        is DataType.Union -> typeValue(
            "union",
            "variants" to ListExecutionValue(variants.map { variant ->
                MapExecutionValue(mapOf(
                    "id" to TextExecutionValue(variant.id.value),
                    "type" to variant.type.asExecutionValue()))
            }),
            "nullable" to BooleanExecutionValue.of(nullable))

        is DataType.Opaque -> typeValue(
            "opaque",
            "nullable" to BooleanExecutionValue.of(nullable))

        is DataType.Dynamic -> typeValue(
            "dynamic",
            "nullable" to BooleanExecutionValue.of(nullable))
    }


object DataTypeExecutionValue {
    fun decode(executionValue: ExecutionValue): DataType {
        val map = executionValue.requireMap("data type")
        val case = map.text("case")
        val nullable = map.boolean("nullable")

        return try {
            when (case) {
                "scalar" -> DataType.Scalar(decodeScalar(map.required("scalar")), nullable)
                "record" -> DataType.Record(map.list("fields").map { encoded ->
                    val field = encoded.requireMap("record field")
                    DataField(
                        decodeFieldId(field.required("id")),
                        decode(field.required("type")),
                        field.boolean("optional"))
                }, nullable)
                "mapping" -> DataType.Mapping(
                    decode(map.required("key")),
                    decode(map.required("value")),
                    nullable)
                "listing" -> DataType.Listing(decode(map.required("element")), nullable)
                "union" -> DataType.Union(map.list("variants").map { encoded ->
                    val variant = encoded.requireMap("union variant")
                    DataVariant(
                        VariantId(variant.text("id")),
                        decode(variant.required("type")))
                }, nullable)
                "opaque" -> DataType.Opaque(nullable)
                "dynamic" -> DataType.Dynamic(nullable)
                else -> invalidEncoding("Unknown data type case '$case'")
            }
        }
        catch (e: DataException) {
            throw e
        }
        catch (e: IllegalArgumentException) {
            invalidEncoding("Invalid $case type: ${e.message}")
        }
    }

    private fun decodeScalar(value: ExecutionValue): ScalarKind {
        val map = value.requireMap("scalar kind")
        return when (val kind = map.text("kind")) {
            "boolean" -> ScalarKind.Boolean
            "integer" -> ScalarKind.Integer(
                map.optionalText("bits")?.toInt(),
                map.boolean("signed"))
            "decimal" -> ScalarKind.Decimal
            "floating" -> ScalarKind.Floating(map.text("bits").toInt())
            "text" -> ScalarKind.Text
            "binary" -> ScalarKind.Binary
            "date" -> ScalarKind.Date
            "time" -> ScalarKind.Time
            "instant" -> ScalarKind.Instant
            "duration" -> ScalarKind.Duration
            "uuid" -> ScalarKind.Uuid
            else -> invalidEncoding("Unknown scalar kind '$kind'")
        }
    }

    private fun decodeFieldId(value: ExecutionValue): FieldId {
        val map = value.requireMap("field identifier")
        return FieldId(map.text("name"), map.text("occurrence").toInt())
    }
}


internal fun ScalarKind.asExecutionValue(): MapExecutionValue =
    when (this) {
        ScalarKind.Boolean -> scalarValue("boolean")
        is ScalarKind.Integer -> scalarValue(
            "integer",
            "bits" to TextExecutionValue(bits?.toString() ?: ""),
            "signed" to BooleanExecutionValue.of(signed))
        ScalarKind.Decimal -> scalarValue("decimal")
        is ScalarKind.Floating -> scalarValue(
            "floating",
            "bits" to TextExecutionValue(bits.toString()))
        ScalarKind.Text -> scalarValue("text")
        ScalarKind.Binary -> scalarValue("binary")
        ScalarKind.Date -> scalarValue("date")
        ScalarKind.Time -> scalarValue("time")
        ScalarKind.Instant -> scalarValue("instant")
        ScalarKind.Duration -> scalarValue("duration")
        ScalarKind.Uuid -> scalarValue("uuid")
    }


internal fun FieldId.asExecutionValue(): MapExecutionValue =
    MapExecutionValue(mapOf(
        "name" to TextExecutionValue(name),
        "occurrence" to TextExecutionValue(occurrence.toString())))


private fun typeValue(
    case: String,
    vararg entries: Pair<String, ExecutionValue>
): MapExecutionValue =
    MapExecutionValue(mapOf("case" to TextExecutionValue(case)) + entries)


private fun scalarValue(
    kind: String,
    vararg entries: Pair<String, ExecutionValue>
): MapExecutionValue =
    MapExecutionValue(mapOf("kind" to TextExecutionValue(kind)) + entries)


private fun ExecutionValue.requireMap(label: String): MapExecutionValue =
    this as? MapExecutionValue ?: invalidEncoding("$label must be a map")


private fun MapExecutionValue.required(key: String): ExecutionValue =
    values[key] ?: invalidEncoding("Missing '$key' in data type encoding")


private fun MapExecutionValue.text(key: String): String =
    (required(key) as? TextExecutionValue)?.value
        ?: invalidEncoding("'$key' must be text in data type encoding")


private fun MapExecutionValue.optionalText(key: String): String? =
    text(key).ifEmpty { null }


private fun MapExecutionValue.boolean(key: String): Boolean =
    (required(key) as? BooleanExecutionValue)?.value
        ?: invalidEncoding("'$key' must be boolean in data type encoding")


private fun MapExecutionValue.list(key: String): List<ExecutionValue> =
    (required(key) as? ListExecutionValue)?.values
        ?: invalidEncoding("'$key' must be a list in data type encoding")


private fun invalidEncoding(message: String): Nothing =
    throw DataException(DataProblem(DataProblem.invalidTypeEncoding, message))
