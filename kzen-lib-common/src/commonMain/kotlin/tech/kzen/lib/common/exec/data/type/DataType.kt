package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


sealed interface DataType: Digestible {
    val nullable: Boolean

    companion object {
        fun ofExecutionValue(executionValue: tech.kzen.lib.common.exec.ExecutionValue): DataType =
            DataTypeExecutionValue.decode(executionValue)
    }

    override fun digest(sink: Digest.Sink) {
        asExecutionValue().digest(sink)
    }

    data class Scalar(
        val kind: ScalarKind,
        override val nullable: Boolean = false
    ): DataType

    class Record(
        fields: List<DataField>,
        override val nullable: Boolean = false
    ): DataType {
        val fields: List<DataField> = fields.toList()

        init {
            validateRecordFields(this.fields)
        }

        override fun equals(other: Any?): Boolean =
            this === other || other is Record && fields == other.fields && nullable == other.nullable

        override fun hashCode(): Int = 31 * fields.hashCode() + nullable.hashCode()

        override fun toString(): String = "Record(fields=$fields, nullable=$nullable)"
    }

    data class Mapping(
        val key: DataType,
        val value: DataType,
        override val nullable: Boolean = false
    ): DataType {
        init {
            val validKey = key is Scalar && !key.nullable ||
                    key is Dynamic && !key.nullable
            if (!validKey) {
                throw DataException(DataProblem(
                    DataProblem.invalidMapping,
                    "Mapping key must be a non-null scalar or non-null dynamic type: $key"))
            }
        }
    }

    data class Listing(
        val element: DataType,
        override val nullable: Boolean = false
    ): DataType

    class Union(
        variants: List<DataVariant>,
        override val nullable: Boolean = false
    ): DataType {
        val variants: List<DataVariant> = variants.toList()

        init {
            if (this.variants.isEmpty()) {
                throw DataException(DataProblem(
                    DataProblem.invalidUnion,
                    "Union must contain at least one variant"))
            }
            val duplicateIds = this.variants.groupBy { it.id }.filterValues { it.size > 1 }.keys
            if (duplicateIds.isNotEmpty()) {
                throw DataException(DataProblem(
                    DataProblem.invalidUnion,
                    "Union variant identifiers must be unique: $duplicateIds"))
            }
            val nested = this.variants.firstOrNull { it.type is Union }
            if (nested != null) {
                throw DataException(DataProblem(
                    DataProblem.invalidUnion,
                    "Union variant '${nested.id}' must not contain a nested union"))
            }
        }

        override fun equals(other: Any?): Boolean =
            this === other || other is Union && variants == other.variants && nullable == other.nullable

        override fun hashCode(): Int = 31 * variants.hashCode() + nullable.hashCode()

        override fun toString(): String = "Union(variants=$variants, nullable=$nullable)"
    }

    data class Opaque(
        override val nullable: Boolean = false
    ): DataType

    data class Dynamic(
        override val nullable: Boolean = true
    ): DataType
}


private fun validateRecordFields(fields: List<DataField>) {
    val nextOccurrenceByName = mutableMapOf<String, Int>()
    val closedNames = mutableSetOf<String>()
    var previousName: String? = null

    for ((id) in fields) {
        if (previousName != null && id.name != previousName) {
            closedNames += previousName
        }
        if (id.name in closedNames) {
            throw DataException(DataProblem(
                DataProblem.invalidRecord,
                "Record occurrences for field '${id.name}' must be contiguous"))
        }

        val expectedOccurrence = nextOccurrenceByName[id.name] ?: 0
        if (id.occurrence != expectedOccurrence) {
            throw DataException(DataProblem(
                DataProblem.invalidRecord,
                "Record field '${id.name}' occurrence ${id.occurrence} " +
                        "must be $expectedOccurrence"))
        }
        nextOccurrenceByName[id.name] = expectedOccurrence + 1
        previousName = id.name
    }
}


internal fun DataType.withNullability(nullable: Boolean): DataType =
    when (this) {
        is DataType.Scalar -> copy(nullable = nullable)
        is DataType.Record -> DataType.Record(fields, nullable)
        is DataType.Mapping -> copy(nullable = nullable)
        is DataType.Listing -> copy(nullable = nullable)
        is DataType.Union -> DataType.Union(variants, nullable)
        is DataType.Opaque -> copy(nullable = nullable)
        is DataType.Dynamic -> copy(nullable = nullable)
    }
