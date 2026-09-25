package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypeAlgebra
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.MetadataContract
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.type.TypeAcceptance
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame


class DataValueMetadataTest {
    private val text = DataType.Scalar(ScalarKind.Text)
    private val long = DataType.Scalar(ScalarKind.Integer(64))
    private val fileMetadata = MetadataContract(DataType.Record(listOf(
        DataField(FieldId("name"), text),
        DataField(FieldId("size"), long))))
    private val row = DataContract(DataType.Record(listOf(DataField(FieldId("x"), text))))


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun metadataIsPartOfContractIdentityAndEncodingButNotOfChildren() {
        val withMetadata = row.withMetadata(fileMetadata)

        assertNotEquals(row, withMetadata)
        assertNotEquals(row.structuralDigest, withMetadata.structuralDigest)
        assertNotEquals(row.declarationDigest, withMetadata.declarationDigest)
        assertEquals(withMetadata, DataContract.ofExecutionValue(withMetadata.asExecutionValue()))
        assertEquals(row.asExecutionValue(), withMetadata.payload().asExecutionValue())

        assertNull(withMetadata.child(DataPathSegment.Field(FieldId("x"))).metadata)
        assertEquals(fileMetadata, withMetadata.expanded().metadata)
        assertEquals(fileMetadata, withMetadata.withFields(listOf(
            DataField(FieldId("y"), text) to DataContract(text))).metadata)
    }


    @Test
    fun metadataMustBePlainDataRecord() {
        assertFailsWith<DataException> { MetadataContract.of(DataContract(text)) }
        assertFailsWith<DataException> { MetadataContract(DataType.Record(emptyList(), nullable = true)) }
        assertFailsWith<DataException> { MetadataContract.of(DataContract(
            DataType.Record(listOf(DataField(FieldId("handle"), DataType.Opaque()))),
            mapOf(DataTypePath(listOf(DataPathSegment.Field(FieldId("handle")))) to TypeMetadata.any))) }
        assertFailsWith<DataException> { MetadataContract(
            DataType.Record(listOf(DataField(FieldId("handle"), DataType.Opaque())))) }
        assertFailsWith<DataException> { MetadataContract.of(fileMetadata.contract.withMetadata(fileMetadata)) }
        assertEquals(fileMetadata, MetadataContract.of(fileMetadata.contract))
    }


    @Test
    fun payloadOnlyExpectationAcceptsAnyMetadataAndDeclaredMetadataIsChecked() {
        val actual = row.withMetadata(fileMetadata)
        assertEquals(TypeAcceptance.Accepted, DataTypeAlgebra.isAssignable(row, actual))

        val wantsName = row.withMetadata(MetadataContract(DataType.Record(listOf(DataField(FieldId("name"), text)))))
        assertEquals(TypeAcceptance.Accepted, DataTypeAlgebra.isAssignable(wantsName, actual))
        assertIs<TypeAcceptance.Rejected>(DataTypeAlgebra.isAssignable(wantsName, row))

        val wantsYear = row.withMetadata(MetadataContract(DataType.Record(listOf(DataField(FieldId("year"), long)))))
        assertIs<TypeAcceptance.Rejected>(DataTypeAlgebra.isAssignable(wantsYear, actual))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun valueExposesMetadataBesidePayload() {
        val metadata = ValueMetadata.of(LiteralDataValues.lift(recordOf("name" to "a.csv", "size" to 3L), fileMetadata.contract))
        val payload = LiteralDataValues.lift(recordOf("x" to "1"), row)
        val value = payload.withMetadata(metadata)

        assertSame(metadata, value.metadata)
        assertEquals(row, value.payloadContract)
        assertEquals(row.withMetadata(fileMetadata), value.contract)
        assertSame(value.contract, value.contract)
        assertEquals(row, value.payload().contract)
        assertEquals("a.csv", metadata.access.readText(metadata.access.field(metadata.root, FieldId("name"))))

        assertFailsWith<DataException> { ValueMetadata.of(LiteralDataValues.lift("text")) }
        assertFailsWith<DataAccessException> { ValueMetadata.of(value) }
    }


    @Test
    fun validationChecksDeclaredMetadataValues() {
        val metadata = ValueMetadata.of(LiteralDataValues.lift(recordOf("name" to "a.csv", "size" to 3L), fileMetadata.contract))
        val value = LiteralDataValues.lift(recordOf("x" to "1"), row).withMetadata(metadata)

        assertEquals(emptyList(), DataValueAlgebra.validate(row, value))
        assertEquals(emptyList(), DataValueAlgebra.validate(row.withMetadata(fileMetadata), value))
        assertEquals(1, DataValueAlgebra.validate(row.withMetadata(fileMetadata), value.payload()).size)
    }


    @Test
    fun snapshotCarriesMetadata() {
        val metadata = ValueMetadata.of(LiteralDataValues.lift(recordOf("name" to "a.csv", "size" to 3L), fileMetadata.contract))
        val value = LiteralDataValues.lift(recordOf("x" to "1"), row).withMetadata(metadata)

        val snapshot = assertIs<SnapshotResult.Complete>(DataSnapshot.capture(value)).snapshot
        val restored = snapshot.asDataValue()
        assertEquals(value.contract, restored.contract)
        val restoredMetadata = restored.metadata!!
        assertEquals(3L, restoredMetadata.access.readLong(
            restoredMetadata.access.field(restoredMetadata.root, FieldId("size"))))

        val payloadOnly = assertIs<SnapshotResult.Complete>(DataSnapshot.capture(value.payload())).snapshot
        assertNotEquals(payloadOnly, snapshot)
        assertEquals(snapshot, payloadOnly.withMetadata(snapshot.metadata))

        val metadataSnapshot = checkNotNull(snapshot.metadata)
        assertEquals(fileMetadata.structural, metadataSnapshot.type)
        assertEquals(metadataSnapshot, MetadataSnapshot.of(metadataSnapshot.snapshot))
        assertFailsWith<DataException> { MetadataSnapshot.of(snapshot) }
        assertFailsWith<DataException> { MetadataSnapshot.of(DataSnapshot.of(DataType.Scalar(ScalarKind.Text), TextExecutionValue("a"))) }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun overlaySetsFieldsInPlaceOrAppendsThem() {
        val base = LiteralDataValues.lift(recordOf("name" to "a.csv", "size" to 3L), fileMetadata.contract)
        val composed = DataOverlay.record(base, listOf(
            FieldId("size") to LiteralDataValues.lift("large"),
            FieldId("year") to LiteralDataValues.lift(2024L)))

        val record = assertIs<DataType.Record>(composed.type)
        assertEquals(listOf("name", "size", "year"), record.fields.map { it.id.name })
        assertEquals(text, record.fields[1].type)
        assertEquals(
            DataOverlay.contract(fileMetadata.contract, listOf(
                FieldId("size") to DataContract(text),
                FieldId("year") to DataContract(long))),
            composed.contract)
        assertEquals("a.csv", composed.access.readText(composed.access.field(composed.root, FieldId("name"))))
        assertEquals("large", composed.access.readText(composed.access.field(composed.root, FieldId("size"))))
        assertEquals(2024L, composed.access.readLong(composed.access.field(composed.root, FieldId("year"))))
        assertEquals(3, composed.access.size(composed.root))

        val snapshot = assertIs<SnapshotResult.Complete>(DataSnapshot.capture(composed)).snapshot
        assertEquals(composed.type, snapshot.type)
    }


    @Test
    fun parentChainNestsWithoutCopyingAndIsUsableAsMetadata() {
        val archive = LiteralDataValues.lift(recordOf("name" to "data.tar.gz", "size" to 100L), fileMetadata.contract)
        val member = DataOverlay.record(
            LiteralDataValues.lift(recordOf("name" to "a.csv", "size" to 3L), fileMetadata.contract),
            listOf(FieldId("parent") to archive))
        val memberMetadata = ValueMetadata.of(member)
        val rows = (1..3).map {
            LiteralDataValues.lift(recordOf("x" to "$it"), row).withMetadata(memberMetadata)
        }

        rows.forEach { assertSame(memberMetadata, it.metadata) }
        val contract = rows.first().contract
        val parentType = assertIs<DataType.Record>(contract.metadata!!.contract.child(
            DataPathSegment.Field(FieldId("parent"))).structural)
        assertEquals(listOf("name", "size"), parentType.fields.map { it.id.name })

        val parent = member.access.field(member.root, FieldId("parent"))
        assertEquals("data.tar.gz", member.access.readText(member.access.field(parent, FieldId("name"))))

        val nested = DataOverlay.withMetadataFields(rows.first(), listOf(FieldId("year") to LiteralDataValues.lift(2024L)))
        assertEquals(listOf("name", "size", "parent", "year"),
            nested.metadata!!.type.fields.map { it.id.name })
        assertEquals(
            DataOverlay.withMetadataFields(rows.first().contract, listOf(FieldId("year") to DataContract(long))),
            nested.contract)
    }


    @Test
    fun overlayRejectsRepeatedAndNonRecordTargets() {
        val value = LiteralDataValues.lift(1L)
        assertFailsWith<DataException> { DataOverlay.record(null, listOf(
            FieldId("a") to value, FieldId("a") to value)) }
        assertFailsWith<DataException> { DataOverlay.record(value, listOf(FieldId("a") to value)) }
    }
}
