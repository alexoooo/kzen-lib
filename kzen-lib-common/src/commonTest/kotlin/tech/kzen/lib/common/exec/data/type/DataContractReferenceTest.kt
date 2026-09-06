package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue


/**
 * E7 item 5, the common half (JVM and JS): a recursive contract is finite — references are leaves expanded one
 * level at a time — with deterministic identity, a serialization round trip, nominal assignability and
 * bounded, named errors for malformed or unresolved references.
 */
class DataContractReferenceTest {
    private val nodeId = DefinitionId("fixture.Node")
    private val treeId = DefinitionId("fixture.Tree")

    // Node { label: Text, next: Node? }  — self recursion through a nullable field
    private val nodeDefinition = DataType.Record(listOf(
        DataField(FieldId("label"), DataType.Scalar(ScalarKind.Text)),
        DataField(FieldId("next"), DataType.Reference(nodeId, nullable = true))))

    // Tree { children: List<Tree>, value: Node }  — recursive collection plus mutual reference
    private val treeDefinition = DataType.Record(listOf(
        DataField(FieldId("children"), DataType.Listing(DataType.Reference(treeId))),
        DataField(FieldId("value"), DataType.Reference(nodeId))))


    @Test
    fun childExpandsOneLevelAndStaysFinite() {
        val contract = DataContract(nodeDefinition, definitions = mapOf(nodeId to nodeDefinition))
        assertTrue(contract.unresolvedReferences().isEmpty())

        val next = contract.child(DataPathSegment.Field(FieldId("next")))
        assertEquals(nodeDefinition.withNullability(true), next.structural, "the reference expands to its definition, nullable as referenced")
        assertEquals(contract.definitions, next.definitions, "definitions travel with every child")

        val deeper = next.child(DataPathSegment.Field(FieldId("next")))
        assertEquals(next.structural, deeper.structural)
        assertIs<DataType.Reference>((deeper.structural as DataType.Record).fields[1].type, "the next level is still a reference")
    }


    @Test
    fun rootReferenceAndMutualRecursionExpandOnDemand() {
        val definitions = mapOf(nodeId to nodeDefinition, treeId to treeDefinition)
        val root = DataContract(DataType.Reference(treeId), definitions = definitions)
        assertIs<DataType.Reference>(root.structural)
        assertEquals(treeDefinition, root.expanded().structural)

        val children = root.child(DataPathSegment.Field(FieldId("children")))
        assertEquals(DataType.Listing(DataType.Reference(treeId)), children.structural)
        val element = children.child(DataPathSegment.ListingElement)
        assertEquals(treeDefinition, element.structural)
        val value = element.child(DataPathSegment.Field(FieldId("value")))
        assertEquals(nodeDefinition, value.structural)
    }


    @Test
    fun serializationRoundTripAndDeterministicDigests() {
        val forward = DataContract(treeDefinition, definitions = mapOf(nodeId to nodeDefinition, treeId to treeDefinition))
        val reversed = DataContract(treeDefinition, definitions = mapOf(treeId to treeDefinition, nodeId to nodeDefinition))
        assertEquals(forward, reversed)
        assertEquals(forward.declarationDigest, reversed.declarationDigest, "definition insertion order does not change identity")

        val decoded = DataContract.ofExecutionValue(forward.asExecutionValue())
        assertEquals(forward, decoded)
        assertEquals(forward.declarationDigest, decoded.declarationDigest)
        assertEquals(DataType.Reference(nodeId, nullable = true),
            DataType.ofExecutionValue(DataType.Reference(nodeId, nullable = true).asExecutionValue()))

        val withoutDefinitions = DataContract(DataType.Scalar(ScalarKind.Text))
        assertTrue("definitions" !in withoutDefinitions.asExecutionValue().values.keys, "non-recursive contracts encode as before")
        assertNotEquals(forward, DataContract(treeDefinition, definitions = mapOf(nodeId to nodeDefinition, treeId to nodeDefinition)))
    }


    @Test
    fun unresolvedAndMalformedReferencesAreBoundedNamedFailures() {
        val dangling = DataContract(nodeDefinition)
        assertEquals(setOf(nodeId), dangling.unresolvedReferences())
        val error = assertFailsWith<DataException> { dangling.child(DataPathSegment.Field(FieldId("next"))) }
        assertEquals(DataProblem.unresolvedReference, error.problem.code)
        assertTrue(error.problem.message.contains("fixture.Node"), error.problem.message)

        val alias = assertFailsWith<DataException> {
            DataContract(nodeDefinition, definitions = mapOf(nodeId to DataType.Reference(treeId)))
        }
        assertEquals(DataProblem.invalidContract, alias.problem.code)
    }


    @Test
    fun referencesAreNominalInAssignabilityAndJoin() {
        val same = DataType.Reference(nodeId)
        assertEquals(TypeAcceptance.Accepted, DataTypeAlgebra.isAssignable(same, DataType.Reference(nodeId)))
        assertEquals(TypeAcceptance.Accepted, DataTypeAlgebra.isAssignable(DataType.Reference(nodeId, nullable = true), same))
        assertIs<TypeAcceptance.Rejected>(DataTypeAlgebra.isAssignable(same, DataType.Reference(treeId)))
        assertIs<TypeAcceptance.Rejected>(DataTypeAlgebra.isAssignable(same, nodeDefinition), "without definitions a reference is nominal")
        assertEquals(TypeAcceptance.Accepted, DataTypeAlgebra.isAssignable(DataType.Dynamic(), same))

        // With contracts, references expand on either side and a recursive pair is assumed compatible
        val definitions = mapOf(nodeId to nodeDefinition, treeId to treeDefinition)
        val designTime = DataContract(DataType.Listing(DataType.Reference(treeId)), definitions = definitions)
        val runTime = DataContract(DataType.Listing(treeDefinition), definitions = definitions)
        assertEquals(TypeAcceptance.Accepted, DataTypeAlgebra.isAssignable(designTime, runTime))
        assertEquals(TypeAcceptance.Accepted, DataTypeAlgebra.isAssignable(runTime, designTime))
        val otherNode = DataType.Record(listOf(DataField(FieldId("label"), DataType.Scalar(ScalarKind.Integer(32)))))
        val incompatible = DataContract(DataType.Listing(DataType.Reference(treeId)),
            definitions = mapOf(treeId to treeDefinition, nodeId to otherNode))
        assertIs<TypeAcceptance.Rejected>(DataTypeAlgebra.isAssignable(designTime, incompatible))

        assertEquals(same, DataTypeAlgebra.join(same, DataType.Reference(nodeId)))
        assertEquals(DataType.Dynamic(false), DataTypeAlgebra.join(same, DataType.Reference(treeId)))
    }
}
