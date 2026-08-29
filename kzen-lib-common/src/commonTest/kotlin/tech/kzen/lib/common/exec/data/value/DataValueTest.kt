package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataVariant
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.VariantId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue


class DataValueTest {
    @Test
    fun literalRecordTraversesByNodesAndSpecializedReads() {
        val bytes = byteArrayOf(1, 2, 3)
        val value = LiteralDataValues.lift(recordOf(
            "name" to "alpha",
            "count" to 7,
            "enabled" to true,
            "bytes" to bytes))
        bytes[0] = 9

        val record = assertIs<DataType.Record>(value.type)
        assertEquals(listOf("name", "count", "enabled", "bytes"), record.fields.map { it.id.name })
        val name = value.access.field(value.root, FieldId("name"))
        val count = value.access.field(value.root, FieldId("count"))
        val enabled = value.access.field(value.root, FieldId("enabled"))
        val binary = value.access.field(value.root, FieldId("bytes"))
        assertEquals("alpha", value.access.readText(name))
        assertEquals(7, value.access.readLong(count))
        assertTrue(value.access.readBoolean(enabled))
        assertContentEquals(byteArrayOf(1, 2, 3), value.access.readBinary(binary))
        assertFailsWith<DataAccessException> { value.access.readText(value.root) }
    }


    @Test
    fun absentNullPresentAndUnionSelectionStayDistinct() {
        val optional = FieldId("optional")
        val nullable = FieldId("nullable")
        val contract = DataContract(DataType.Record(listOf(
            DataField(optional, DataType.Scalar(ScalarKind.Text), optional = true),
            DataField(nullable, DataType.Scalar(ScalarKind.Text, nullable = true)))))
        val value = LiteralDataValues.lift(recordOf("nullable" to null), contract)

        assertEquals(DataState.Absent, value.access.state(value.access.field(value.root, optional)))
        assertEquals(DataState.Null, value.access.state(value.access.field(value.root, nullable)))

        val text = VariantId("text")
        val number = VariantId("number")
        val unionContract = DataContract(DataType.Union(listOf(
            DataVariant(text, DataType.Scalar(ScalarKind.Text)),
            DataVariant(number, DataType.Scalar(ScalarKind.Integer(32))))))
        val union = LiteralDataValues.union(unionContract, text, "selected")
        assertEquals(text, union.access.activeVariant(union.root))
        assertEquals("selected", union.access.readText(union.access.selected(union.root)))
    }


    @Test
    fun mappingKeysAreTypedUnknownOrOpaqueAndCanonicalCollisionsFail() {
        val empty = LiteralDataValues.lift(emptyMap<String, String>())
        val emptyType = assertIs<DataType.Mapping>(empty.type)
        assertIs<DataType.Dynamic>(emptyType.key)
        assertTrue(!emptyType.key.nullable)

        val text = LiteralDataValues.lift(linkedMapOf("a" to 1, "b" to 2))
        val textType = assertIs<DataType.Mapping>(text.type)
        assertEquals(ScalarKind.Text, assertIs<DataType.Scalar>(textType.key).kind)
        assertEquals(
            1,
            text.access.readLong(text.access.entry(text.root, TextExecutionValue("a"))))

        val mixed = linkedMapOf<Any, Any>("a" to 1, 2 to 2)
        val opaque = LiteralDataValues.lift(mixed)
        assertIs<DataType.Opaque>(opaque.type)
        assertTrue(opaque.access.native(opaque.root) === mixed)

        val collision = linkedMapOf<Any, Any>(1 to "a", 1L to "b")
        val failure = assertFailsWith<DataException> { LiteralDataValues.lift(collision) }
        assertEquals(DataProblem.mappingKeyCollision, failure.problem.code)
    }


    @Test
    fun inferredContainersCarryFallbackMetadataForNestedOpaqueValues() {
        val first = Any()
        val second = Any()
        val value = LiteralDataValues.lift(listOf(first, second))

        val listing = assertIs<DataType.Listing>(value.type)
        assertIs<DataType.Opaque>(listing.element)
        assertTrue(value.access.native(value.access.element(value.root, 0)) === first)
        assertTrue(value.access.native(value.access.element(value.root, 1)) === second)
    }


    @Test
    fun validationWalkIsExplicitAndReportsExactFieldPath() {
        val value = LiteralDataValues.lift(recordOf("name" to "alpha"))
        assertEquals(emptyList(), DataValueAlgebra.validate(value.contract, value))

        val hostile = object: ValueAccess by value.access {
            override fun field(node: DataNode, field: FieldId): DataNode {
                throw DataAccessException(DataProblem(
                    DataProblem.invalidValue,
                    "hostile field",
                    listOf(DataPathSegment.Field(field))))
            }
        }
        val shallow = DataValue(hostile, value.root)
        val problem = DataValueAlgebra.validate(value.contract, shallow).single()
        assertEquals(listOf(DataPathSegment.Field(FieldId("name"))), problem.path)
    }


    @Test
    fun recordLiteralRejectsBadNamesAndCopiesItsFieldList() {
        assertFailsWith<DataException> { recordOf("" to 1) }
        assertFailsWith<DataException> { recordOf("a" to 1, "a" to 2) }
    }
}
