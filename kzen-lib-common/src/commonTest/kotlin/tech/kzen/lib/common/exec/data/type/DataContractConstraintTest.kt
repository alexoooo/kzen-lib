package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.value.DataValueAlgebra
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.recordOf
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue


class DataContractConstraintTest {
    private val nodeId = DefinitionId("fixture.Node")
    private val nodeDefinition = DataType.Record(listOf(
        DataField(FieldId("label"), DataType.Scalar(ScalarKind.Text)),
        DataField(FieldId("next"), DataType.Reference(nodeId, nullable = true))))

    private val row = DataType.Record(listOf(
        DataField(FieldId("name"), DataType.Scalar(ScalarKind.Text)),
        DataField(FieldId("size"), DataType.Scalar(ScalarKind.Integer(64, true))),
        DataField(FieldId("modified"), DataType.Scalar(ScalarKind.Instant, true))))

    private val kinds = DataConstraint.SymbolSet(listOf("file", "directory", "link", "other"))
    private val kindPath = path(DataPathSegment.Field(FieldId("kind")))
    private val listing = DataType.Record(listOf(
        DataField(FieldId("name"), DataType.Scalar(ScalarKind.Text)),
        DataField(FieldId("kind"), DataType.Scalar(ScalarKind.Text, nullable = true))))
    private val listingContract = DataContract(listing, constraintsByPath = mapOf(kindPath to listOf(kinds)))


    @Test
    fun unconstrainedContractsEncodeAsBefore() {
        // Declaration digests captured before the constraint layer existed
        val digests = listOf(
            DataContract(DataType.Scalar(ScalarKind.Text)),
            DataContract(row, mapOf(path(DataPathSegment.Field(FieldId("name"))) to TypeMetadata.string)),
            DataContract(nodeDefinition, definitions = mapOf(nodeId to nodeDefinition))
        ).map { it.declarationDigest.asString() }

        assertEquals(
            listOf(
                "nvjh0_-1ait78u_1k66o3a_-negn2j",
                "sa6gu2_1tt3vi0_-3n2h0i_-1vtnbqu",
                "fd0ggq_-1malhlr_-1aisl9o_-j12bvm"),
            digests)
        assertTrue("constraints" !in DataContract(row).asExecutionValue().values.keys)
    }


    @Test
    fun symbolSetsRejectEmptyDuplicateAndMisplacedDeclarations() {
        assertInvalid(DataProblem.invalidConstraint) { DataConstraint.SymbolSet(emptyList()) }
        assertInvalid(DataProblem.invalidConstraint) { DataConstraint.SymbolSet(listOf("a", "b", "a")) }

        assertInvalid(DataProblem.invalidConstraint, "only text positions take a symbol set") {
            DataContract(row, constraintsByPath = mapOf(
                path(DataPathSegment.Field(FieldId("size"))) to listOf(kinds)))
        }
        assertInvalid(DataProblem.invalidPath) {
            DataContract(row, constraintsByPath = mapOf(kindPath to listOf(kinds)))
        }
        assertInvalid(DataProblem.invalidPath, "a path cannot reach into a definition") {
            DataContract(
                nodeDefinition,
                definitions = mapOf(nodeId to nodeDefinition),
                constraintsByPath = mapOf(
                    path(DataPathSegment.Field(FieldId("next")), DataPathSegment.Field(FieldId("label")))
                        to listOf(kinds)))
        }
        assertInvalid(DataProblem.invalidConstraint, "one constraint per kind at a path") {
            DataContract(listing, constraintsByPath = mapOf(
                kindPath to listOf(kinds, DataConstraint.SymbolSet(listOf("file")))))
        }
    }


    @Test
    fun constraintsFollowNavigationIncludingMappingKeys() {
        assertEquals(listOf(kinds), listingContract.child(DataPathSegment.Field(FieldId("kind")))
            .constraintsByPath[DataTypePath.root])
        assertTrue(listingContract.child(DataPathSegment.Field(FieldId("name"))).constraintsByPath.isEmpty())

        val byKind = DataContract(
            DataType.Mapping(DataType.Scalar(ScalarKind.Text), DataType.Scalar(ScalarKind.Integer(64))),
            constraintsByPath = mapOf(path(DataPathSegment.MappingKey) to listOf(kinds)))
        assertEquals(listOf(kinds), byKind.child(DataPathSegment.MappingKey).constraintsByPath[DataTypePath.root])

        val nested = DataContract(
            DataType.Listing(listing),
            constraintsByPath = mapOf(path(DataPathSegment.ListingElement, kindPath.segments.single()) to listOf(kinds)))
        assertEquals(listingContract, nested.child(DataPathSegment.ListingElement))
    }


    @Test
    fun constraintsAreDeclarationNotStructuralIdentity() {
        val unconstrained = DataContract(listing)
        assertNotEquals(unconstrained, listingContract)
        assertEquals(unconstrained.structuralDigest, listingContract.structuralDigest)
        assertNotEquals(unconstrained.declarationDigest, listingContract.declarationDigest)

        val decoded = DataContract.ofExecutionValue(listingContract.asExecutionValue())
        assertEquals(listingContract, decoded)
        assertEquals(listingContract.declarationDigest, decoded.declarationDigest)
        assertEquals(listOf("file", "directory", "link", "other"),
            (decoded.constraintsByPath.getValue(kindPath).single() as DataConstraint.SymbolSet).symbols,
            "declared order survives the round trip")

        val unknownKind = assertFailsWith<DataException> {
            DataConstraint.ofExecutionValue(tech.kzen.lib.common.exec.MapExecutionValue(mapOf(
                "kind" to tech.kzen.lib.common.exec.TextExecutionValue("range"))))
        }
        assertEquals(DataProblem.invalidTypeEncoding, unknownKind.problem.code)
    }


    @Test
    fun typeAlgebraIgnoresConstraints() {
        val unconstrained = DataContract(listing)
        assertEquals(TypeAcceptance.Accepted, DataTypeAlgebra.isAssignable(listingContract, unconstrained))
        assertEquals(TypeAcceptance.Accepted, DataTypeAlgebra.isAssignable(unconstrained, listingContract))
        assertEquals(listing, DataTypeAlgebra.join(listingContract.structural, unconstrained.structural))
    }


    @Test
    fun validateEnforcesTheDeclaredSymbols() {
        val conforming = LiteralDataValues.lift(recordOf("name" to "a", "kind" to "link"), DataContract(listing))
        assertEquals(emptyList(), DataValueAlgebra.validate(listingContract, conforming))

        val absentKind = LiteralDataValues.lift(recordOf("name" to "a", "kind" to null), DataContract(listing))
        assertEquals(emptyList(), DataValueAlgebra.validate(listingContract, absentKind), "null is nullability's call")

        val stray = LiteralDataValues.lift(recordOf("name" to "a", "kind" to "socket"), DataContract(listing))
        assertEquals(emptyList(), DataValueAlgebra.validate(DataContract(listing), stray))
        val problems = DataValueAlgebra.validate(listingContract, stray)
        assertEquals(listOf(DataProblem.constraintViolation), problems.map { it.code })
        assertEquals(kindPath.segments, problems.single().path)

        val carried = LiteralDataValues.lift(recordOf("name" to "a", "kind" to "socket"), listingContract)
        assertEquals(listingContract, carried.contract, "a literal adopts the expected constraints")
        assertEquals(
            listOf(DataProblem.constraintViolation),
            DataValueAlgebra.validate(DataContract(listing), carried).map { it.code },
            "the value's own constraints are enforced too")

        val byKind = DataContract(
            DataType.Mapping(DataType.Scalar(ScalarKind.Text), DataType.Scalar(ScalarKind.Integer(64))),
            constraintsByPath = mapOf(path(DataPathSegment.MappingKey) to listOf(kinds)))
        val counts = LiteralDataValues.lift(mapOf("file" to 3L, "fifo" to 1L), DataContract(byKind.structural))
        assertEquals(
            listOf(DataProblem.constraintViolation),
            DataValueAlgebra.validate(byKind, counts).map { it.code })
    }


    @Test
    fun withFieldsCarriesEveryFieldsMetadata() {
        val base = DataContract(DataType.Record(listOf(DataField(FieldId("path"), DataType.Scalar(ScalarKind.Text)))))
        val kind = listingContract.child(DataPathSegment.Field(FieldId("kind")))
        val node = DataContract(nodeDefinition, mapOf(DataTypePath.root to TypeMetadata.any),
            definitions = mapOf(nodeId to nodeDefinition))

        val composed = base.withFields(listOf(
            DataField(FieldId("kind"), kind.structural) to kind,
            DataField(FieldId("node"), node.structural) to node))

        assertEquals(listOf(kinds), composed.constraintsByPath[kindPath])
        assertEquals(TypeMetadata.any, composed.nativeByPath[path(DataPathSegment.Field(FieldId("node")))])
        assertEquals(mapOf(nodeId to nodeDefinition), composed.definitions)

        assertInvalid(DataProblem.invalidRecord) {
            composed.withFields(listOf(DataField(FieldId("kind"), kind.structural) to kind))
        }
        assertInvalid(DataProblem.invalidContract) {
            composed.withFields(listOf(DataField(FieldId("other"), DataType.Reference(nodeId)) to
                DataContract(DataType.Reference(nodeId), definitions = mapOf(nodeId to row))))
        }
    }


    private fun path(vararg segments: DataPathSegment): DataTypePath =
        DataTypePath(segments.toList())


    private fun assertInvalid(code: String, message: String? = null, block: () -> Unit) {
        val error = assertFailsWith<DataException>(message) { block() }
        assertEquals(code, error.problem.code, message)
    }
}
