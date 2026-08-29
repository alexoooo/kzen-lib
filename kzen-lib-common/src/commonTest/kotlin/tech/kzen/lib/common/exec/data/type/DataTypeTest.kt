package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.BinaryExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals


class DataTypeTest {
    private val text = DataType.Scalar(ScalarKind.Text)
    private val integer = DataType.Scalar(ScalarKind.Integer(32))


    @Test
    fun validatesIdentifiersScalarsRecordsMappingsAndUnions() {
        assertProblem(DataProblem.invalidIdentifier) { FieldId("") }
        assertProblem(DataProblem.invalidIdentifier) { FieldId("a", -1) }
        assertProblem(DataProblem.invalidIdentifier) { VariantId("") }
        assertProblem(DataProblem.invalidPath) { DataPathSegment.Element(-1) }
        assertProblem(DataProblem.invalidScalar) { ScalarKind.Integer(7) }
        assertProblem(DataProblem.invalidScalar) { ScalarKind.Floating(16) }

        assertProblem(DataProblem.invalidRecord) {
            DataType.Record(listOf(DataField(FieldId("a", 1), text)))
        }
        assertProblem(DataProblem.invalidRecord) {
            DataType.Record(listOf(
                DataField(FieldId("a"), text),
                DataField(FieldId("b"), text),
                DataField(FieldId("a", 1), text)))
        }
        assertProblem(DataProblem.invalidMapping) {
            DataType.Mapping(DataType.Scalar(ScalarKind.Text, true), text)
        }
        assertProblem(DataProblem.invalidMapping) {
            DataType.Mapping(DataType.Dynamic(), text)
        }
        assertProblem(DataProblem.invalidMapping) {
            DataType.Mapping(DataType.Listing(text), text)
        }
        assertProblem(DataProblem.invalidUnion) { DataType.Union(emptyList()) }
        assertProblem(DataProblem.invalidUnion) {
            DataType.Union(listOf(
                DataVariant(VariantId("same"), text),
                DataVariant(VariantId("same"), integer)))
        }
        assertProblem(DataProblem.invalidUnion) {
            DataType.Union(listOf(DataVariant(
                VariantId("nested"),
                DataType.Union(listOf(DataVariant(VariantId("inner"), text))))))
        }
    }


    @Test
    fun freezesAllConstructorCollections() {
        val mutableFields = mutableListOf(DataField(FieldId("a"), text))
        val record = DataType.Record(mutableFields)
        val recordDigest = record.digest()
        mutableFields += DataField(FieldId("b"), integer)
        assertEquals(listOf(FieldId("a")), record.fields.map { it.id })
        assertEquals(recordDigest, record.digest())

        val mutableVariants = mutableListOf(DataVariant(VariantId("a"), text))
        val union = DataType.Union(mutableVariants)
        mutableVariants += DataVariant(VariantId("b"), integer)
        assertEquals(listOf(VariantId("a")), union.variants.map { it.id })

        val mutableSegments = mutableListOf<DataPathSegment>(DataPathSegment.Field(FieldId("a")))
        val path = DataTypePath(mutableSegments)
        mutableSegments += DataPathSegment.ListingElement
        assertEquals(1, path.segments.size)

        val mutableGenerics = mutableListOf(TypeMetadata.string)
        val metadata = TypeMetadata(ClassName("example.Box"), mutableGenerics, false)
        mutableGenerics += TypeMetadata.int
        assertEquals(listOf(TypeMetadata.string), metadata.generics)

        val mutableMetadata = mutableMapOf(DataTypePath.root to metadata)
        val contract = DataContract(record, mutableMetadata)
        val digest = contract.declarationDigest
        mutableMetadata.clear()
        assertEquals(1, contract.nativeByPath.size)
        assertEquals(digest, contract.declarationDigest)
    }


    @Test
    fun validatesAndRebasesNativeMetadata() {
        val opaqueField = FieldId("payload")
        val structural = DataType.Record(listOf(
            DataField(opaqueField, DataType.Opaque(nullable = true)),
            DataField(FieldId("items"), DataType.Listing(text))))
        val opaquePath = DataTypePath(listOf(DataPathSegment.Field(opaqueField)))
        val elementPath = DataTypePath(listOf(
            DataPathSegment.Field(FieldId("items")),
            DataPathSegment.ListingElement))

        val opaqueMetadata = TypeMetadata(ClassName("example.Payload"), emptyList(), true)
        val elementMetadata = TypeMetadata(ClassName("kotlin.String"), emptyList(), false)
        val contract = DataContract(structural, mapOf(
            opaquePath to opaqueMetadata,
            elementPath to elementMetadata))

        val items = contract.child(DataPathSegment.Field(FieldId("items")))
        assertEquals(DataType.Listing(text), items.structural)
        assertEquals(
            mapOf(DataTypePath(listOf(DataPathSegment.ListingElement)) to elementMetadata),
            items.nativeByPath)
        assertEquals(items, contract.child(DataPathSegment.Field(FieldId("items"))))

        assertProblem(DataProblem.invalidContract) { DataContract(structural) }
        assertProblem(DataProblem.invalidPath) {
            DataContract(structural, mapOf(
                DataTypePath(listOf(DataPathSegment.Field(FieldId("missing")))) to elementMetadata,
                opaquePath to opaqueMetadata))
        }
        assertProblem(DataProblem.invalidContract) {
            DataContract(
                DataType.Dynamic(false),
                mapOf(DataTypePath.root to elementMetadata))
        }
        assertProblem(DataProblem.invalidContract) {
            DataContract(
                DataType.Mapping(text, integer),
                mapOf(
                    DataTypePath(listOf(DataPathSegment.MappingKey)) to elementMetadata))
        }
        assertProblem(DataProblem.invalidContract) {
            DataContract(
                DataType.Opaque(nullable = true),
                mapOf(DataTypePath.root to elementMetadata))
        }
    }


    @Test
    fun structuralAndDeclarationDigestsSeparateNativeIdentity() {
        val field = FieldId("value")
        val structural = DataType.Record(listOf(DataField(field, text)))
        val nestedPath = DataTypePath(listOf(DataPathSegment.Field(field)))
        val first = DataContract(structural, mapOf(
            DataTypePath.root to TypeMetadata(ClassName("example.First"), emptyList(), false),
            nestedPath to TypeMetadata(ClassName("kotlin.String"), emptyList(), false)))
        val rootDifference = DataContract(structural, mapOf(
            DataTypePath.root to TypeMetadata(ClassName("example.Second"), emptyList(), false),
            nestedPath to TypeMetadata(ClassName("kotlin.String"), emptyList(), false)))
        val nestedDifference = DataContract(structural, mapOf(
            DataTypePath.root to TypeMetadata(ClassName("example.First"), emptyList(), false),
            nestedPath to TypeMetadata(ClassName("example.Text"), emptyList(), false)))

        assertEquals(first.structuralDigest, rootDifference.structuralDigest)
        assertEquals(first.structuralDigest, nestedDifference.structuralDigest)
        assertNotEquals(first.declarationDigest, rootDifference.declarationDigest)
        assertNotEquals(first.declarationDigest, nestedDifference.declarationDigest)
    }


    @Test
    fun pathsAndProblemsFreezeMutableInputs() {
        val mutablePath = mutableListOf<DataPathSegment>(DataPathSegment.Field(FieldId("a")))
        val problem = DataProblem("test", "test problem", mutablePath)
        mutablePath.clear()
        assertEquals(listOf(DataPathSegment.Field(FieldId("a"))), problem.path)

        val entry = DataPathSegment.Entry(ScalarKind.Text, TextExecutionValue("key"))
        assertEquals("/entry:text:\"key\"", DataTypePath(listOf(entry)).toString())

        val mutableBytes = byteArrayOf(1, 2, 3)
        val binaryEntry = DataPathSegment.Entry(
            ScalarKind.Binary,
            BinaryExecutionValue(mutableBytes))
        mutableBytes[0] = 9
        assertEquals(
            BinaryExecutionValue(byteArrayOf(1, 2, 3)),
            binaryEntry.key)
    }


    private fun assertProblem(code: String, block: () -> Unit) {
        val failure = assertFailsWith<DataException>(block = block)
        assertEquals(code, failure.problem.code)
    }
}
