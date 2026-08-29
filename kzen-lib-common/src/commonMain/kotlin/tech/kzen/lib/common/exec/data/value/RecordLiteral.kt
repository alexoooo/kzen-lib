package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem


data class RecordLiteralField(
    val name: String,
    val value: Any?
)


class RecordLiteral internal constructor(fields: List<RecordLiteralField>) {
    val fields: List<RecordLiteralField> = fields.toList()
}


fun recordOf(vararg fields: Pair<String, Any?>): RecordLiteral {
    val names = mutableSetOf<String>()
    val copied = fields.map { (name, value) ->
        if (name.isBlank()) {
            throw DataException(DataProblem(
                DataProblem.invalidRecord,
                "Record literal field name must not be blank"))
        }
        if (!names.add(name)) {
            throw DataException(DataProblem(
                DataProblem.invalidRecord,
                "Record literal field names must be unique: '$name'"))
        }
        RecordLiteralField(name, value)
    }
    return RecordLiteral(copied)
}
