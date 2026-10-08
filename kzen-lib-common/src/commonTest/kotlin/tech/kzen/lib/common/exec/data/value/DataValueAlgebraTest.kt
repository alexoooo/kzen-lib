package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.data.type.DataConstraint
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs


class DataValueAlgebraTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val text = DataType.Scalar(ScalarKind.Text)
    private val integer = DataType.Scalar(ScalarKind.Integer(32))
    private val texts = DataContract(DataType.Listing(text))
    private val textLists = DataContract(DataType.Listing(DataType.Listing(text)))
    private val integerListsByText = DataContract(DataType.Mapping(text, DataType.Listing(integer)))


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun anEmptyCollectionIsAcceptedByAnyCollectionOfItsKind() {
        val emptyList = LiteralDataValues.lift(emptyList<Any>())
        val emptyMap = LiteralDataValues.lift(emptyMap<String, Any>())

        assertEquals(TypeAcceptance.Accepted, DataValueAlgebra.isAssignable(texts, emptyList))
        assertEquals(TypeAcceptance.Accepted, DataValueAlgebra.isAssignable(textLists, emptyList))
        assertEquals(TypeAcceptance.Accepted, DataValueAlgebra.isAssignable(integerListsByText, emptyMap))

        assertIs<TypeAcceptance.Rejected>(DataValueAlgebra.isAssignable(integerListsByText, emptyList))
        assertIs<TypeAcceptance.Rejected>(DataValueAlgebra.isAssignable(texts, emptyMap))
        assertIs<TypeAcceptance.Rejected>(DataValueAlgebra.isAssignable(DataContract(text), emptyList))
    }


    @Test
    fun designTimeAssignabilityStillRejectsAnUnknownElement() {
        val emptyList = LiteralDataValues.lift(emptyList<Any>())

        assertIs<TypeAcceptance.Rejected>(DataTypeAlgebra.isAssignable(texts, emptyList.contract))
        assertIs<TypeAcceptance.Rejected>(DataTypeAlgebra.isAssignable(
            texts, DataContract(DataType.Listing(DataType.Dynamic()))))
        assertIs<TypeAcceptance.Rejected>(DataTypeAlgebra.isAssignable(
            DataType.Mapping(text, integer), DataType.Mapping(DataType.Dynamic(false), DataType.Dynamic())))
    }


    @Test
    fun aNestedEmptyCollectionIsAcceptedBesideConformingSiblings() {
        val mixed = LiteralDataValues.lift(listOf(emptyList<Any>(), listOf("a")))
        assertEquals(TypeAcceptance.Accepted, DataValueAlgebra.isAssignable(textLists, mixed))

        val entries = LiteralDataValues.lift(mapOf("a" to emptyList<Any>(), "b" to listOf(1)))
        assertEquals(TypeAcceptance.Accepted, DataValueAlgebra.isAssignable(integerListsByText, entries))

        val record = DataContract(DataType.Record(listOf(DataField(FieldId("tags"), DataType.Listing(text)))))
        assertEquals(
            TypeAcceptance.Accepted,
            DataValueAlgebra.isAssignable(record, LiteralDataValues.lift(recordOf("tags" to emptyList<Any>()))))
    }


    @Test
    fun whatACollectionHoldsIsStillChecked() {
        val rejected = listOf(
            texts to LiteralDataValues.lift(listOf(1)),
            texts to LiteralDataValues.lift(listOf("a", null)),
            textLists to LiteralDataValues.lift(listOf(emptyList<Any>(), listOf(1))),
            integerListsByText to LiteralDataValues.lift(mapOf("a" to emptyList<Any>(), "b" to listOf("x"))),
            integerListsByText to LiteralDataValues.lift(mapOf(1 to emptyList<Any>(), 2 to listOf(1))))
        for ((expected, value) in rejected) {
            val rejection = assertIs<TypeAcceptance.Rejected>(
                DataValueAlgebra.isAssignable(expected, value), "${value.type}")
            val contractRejection = assertIs<TypeAcceptance.Rejected>(
                DataTypeAlgebra.isAssignable(expected, value.contract))
            assertEquals(contractRejection.problem.message, rejection.problem.message)
        }

        val missingField = DataContract(DataType.Record(listOf(
            DataField(FieldId("tags"), DataType.Listing(text)),
            DataField(FieldId("name"), text))))
        assertIs<TypeAcceptance.Rejected>(DataValueAlgebra.isAssignable(
            missingField, LiteralDataValues.lift(recordOf("tags" to emptyList<Any>()))))
    }


    @Test
    fun aNullHeldWhereNullIsAllowedIsAccepted() {
        val nullableTexts = DataContract(DataType.Listing(text, nullable = true))
        val nullList = LiteralDataValues.lift(null, DataContract(DataType.Listing(DataType.Dynamic(), nullable = true)))

        assertEquals(TypeAcceptance.Accepted, DataValueAlgebra.isAssignable(nullableTexts, nullList))
        assertIs<TypeAcceptance.Rejected>(DataValueAlgebra.isAssignable(texts, nullList))
    }


    @Test
    fun validationAcceptsAnEmptyCollectionAndChecksTheConstraintsOfWhatIsHeld() {
        assertEquals(emptyList(), DataValueAlgebra.validate(texts, LiteralDataValues.lift(emptyList<Any>())))

        val symbols = DataContract(
            DataType.Listing(DataType.Listing(text)),
            constraintsByPath = mapOf(
                DataTypePath(listOf(DataPathSegment.ListingElement, DataPathSegment.ListingElement)) to
                        listOf(DataConstraint.SymbolSet(listOf("a", "b")))))
        assertEquals(
            emptyList(),
            DataValueAlgebra.validate(symbols, LiteralDataValues.lift(listOf(emptyList<Any>(), listOf("a")))))
        assertEquals(
            1,
            DataValueAlgebra.validate(symbols, LiteralDataValues.lift(listOf(emptyList<Any>(), listOf("c")))).size)
    }


    @Test
    fun metadataHoldingAnEmptyCollectionIsAcceptedByItsTypedContract() {
        val tagged = MetadataContract(DataType.Record(listOf(DataField(FieldId("tags"), DataType.Listing(text)))))
        val metadata = ValueMetadata.of(LiteralDataValues.lift(recordOf("tags" to emptyList<Any>())))
        val value = LiteralDataValues.lift("x").withMetadata(metadata)

        assertEquals(
            TypeAcceptance.Accepted,
            DataValueAlgebra.isAssignable(DataContract(text).withMetadata(tagged), value))
        assertIs<TypeAcceptance.Rejected>(DataValueAlgebra.isAssignable(
            DataContract(text).withMetadata(tagged),
            LiteralDataValues.lift("x").withMetadata(
                ValueMetadata.of(LiteralDataValues.lift(recordOf("tags" to listOf(1)))))))
    }
}
