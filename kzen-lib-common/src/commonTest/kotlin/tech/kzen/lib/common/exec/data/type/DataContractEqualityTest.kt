package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


/**
 * Contract equality is declaration identity: structure, natives, definitions, definition natives, constraints and
 * metadata, with every map compared as a map (insertion order never matters) and a path's constraints as a set.
 * Checked against that definition over contracts that differ in one part each, and against hash consistency.
 */
class DataContractEqualityTest {
    private val text = DataType.Scalar(ScalarKind.Text)
    private val nodeId = DefinitionId("fixture.Node")
    private val leafId = DefinitionId("fixture.Leaf")

    private val nodeDefinition = DataType.Record(listOf(
        DataField(FieldId("label"), text),
        DataField(FieldId("next"), DataType.Reference(nodeId, nullable = true))))
    private val leafDefinition = DataType.Record(listOf(DataField(FieldId("value"), text)))

    private val name = path(DataPathSegment.Field(FieldId("name")))
    private val kind = path(DataPathSegment.Field(FieldId("kind")))
    private val handle = path(DataPathSegment.Field(FieldId("handle")))
    private val label = path(DataPathSegment.Field(FieldId("label")))
    private val source = path(DataPathSegment.Field(FieldId("source")))

    private val row = DataType.Record(listOf(
        DataField(FieldId("name"), text),
        DataField(FieldId("kind"), DataType.Scalar(ScalarKind.Text, nullable = true)),
        DataField(FieldId("node"), DataType.Reference(nodeId, nullable = true)),
        DataField(FieldId("leaf"), DataType.Reference(leafId)),
        DataField(FieldId("handle"), DataType.Opaque())))

    private val kinds = DataConstraint.SymbolSet(listOf("file", "directory"))
    private val names = DataConstraint.SymbolSet(listOf("a", "b"))
    private val sources = DataConstraint.SymbolSet(listOf("disk", "network"))

    private val metadata = MetadataContract(
        DataType.Record(listOf(DataField(FieldId("source"), text))),
        constraintsByPath = mapOf(source to listOf(sources)))


    @Test
    fun equalPartsInAnyInsertionOrderAreEqualWithEqualHashes() {
        val base = contract()
        val reordered = contract(
            natives = linkedMapOf(name to TypeMetadata.string, handle to TypeMetadata.any),
            definitions = linkedMapOf(leafId to leafDefinition, nodeId to nodeDefinition),
            definitionNatives = linkedMapOf(
                leafId to mapOf(path(DataPathSegment.Field(FieldId("value"))) to TypeMetadata.string),
                nodeId to mapOf(label to TypeMetadata.string)),
            constraints = linkedMapOf(name to listOf(names), kind to listOf(kinds)))

        for (equal in listOf(contract(), reordered, DataContract.ofExecutionValue(base.asExecutionValue()))) {
            assertEquals(base, equal)
            assertEquals(equal, base)
            assertEquals(base.hashCode(), equal.hashCode())
        }
        assertEquals(base.hashCode(), base.hashCode(), "a hash is stable")
    }


    @Test
    fun eachPartDecidesEquality() {
        val base = contract()
        val differing = mapOf(
            "structure" to contract(structural = DataType.Record(row.fields.map {
                if (it.id.name == "kind") it.copy(type = text) else it
            })),
            "a native" to contract(natives = mapOf(handle to TypeMetadata.any, name to TypeMetadata.any)),
            "a missing native" to contract(natives = mapOf(handle to TypeMetadata.any)),
            "a definition" to contract(definitions = mapOf(nodeId to nodeDefinition, leafId to DataType.Record(
                listOf(DataField(FieldId("value"), DataType.Scalar(ScalarKind.Integer(64))))))),
            "a definition native" to contract(definitionNatives = mapOf(nodeId to mapOf(label to TypeMetadata.any))),
            "a missing definition native" to contract(definitionNatives = emptyMap()),
            "symbol order" to contract(
                constraints = mapOf(kind to listOf(DataConstraint.SymbolSet(listOf("directory", "file"))),
                    name to listOf(names))),
            "a missing constraint" to contract(constraints = mapOf(kind to listOf(kinds))),
            "no constraints" to contract(constraints = emptyMap()),
            "no metadata" to contract().payload(),
            "metadata constraints" to contract().withMetadata(MetadataContract(metadata.structural)))

        for ((part, other) in differing) {
            assertNotEquals(base, other, part)
            assertNotEquals(other, base, part)
        }
    }


    @Test
    fun equalityAgreesWithItsDefinitionAndHashes() {
        val nodeChild = contract().child(DataPathSegment.Field(FieldId("node")))
        val nodeDirect = DataContract(
            nodeDefinition.withNullability(true),
            mapOf(label to TypeMetadata.string),
            mapOf(nodeId to nodeDefinition, leafId to leafDefinition),
            definitionNatives())
        val plain = DataContract(text)
        val emptyConstraintList = DataContract(text, constraintsByPath = mapOf(DataTypePath.root to emptyList()))
        val contracts = listOf(
            contract(),
            contract(constraints = linkedMapOf(name to listOf(names), kind to listOf(kinds))),
            contract(constraints = emptyMap()),
            contract(constraints = mapOf(kind to listOf(kinds))),
            contract(definitionNatives = emptyMap()),
            contract().payload(),
            contract().withMetadata(MetadataContract(metadata.structural)),
            contract().child(DataPathSegment.Field(FieldId("kind"))),
            DataContract(DataType.Scalar(ScalarKind.Text, nullable = true),
                constraintsByPath = mapOf(DataTypePath.root to listOf(kinds))),
            nodeChild,
            nodeDirect,
            plain,
            emptyConstraintList,
            metadata.contract,
            MetadataContract(metadata.structural).contract)

        for (left in contracts) {
            for (right in contracts) {
                val equal = left == right
                assertEquals(definitionallyEqual(left, right), equal, "$left vs $right")
                if (equal) {
                    assertEquals(left.hashCode(), right.hashCode(), "$left vs $right")
                }
            }
        }
        assertEquals(nodeDirect, nodeChild, "a child equals the same contract built directly")
        assertEquals(plain, emptyConstraintList, "an empty constraint list is no constraint")
    }


    @Test
    fun metadataContractsCompareByTheirContract() {
        val equal = MetadataContract(
            DataType.Record(listOf(DataField(FieldId("source"), text))),
            constraintsByPath = mapOf(source to listOf(DataConstraint.SymbolSet(listOf("disk", "network")))))
        assertEquals(metadata, equal)
        assertEquals(metadata.hashCode(), equal.hashCode())
        assertNotEquals(metadata, MetadataContract(metadata.structural))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun contract(
        structural: DataType = row,
        natives: Map<DataTypePath, TypeMetadata> = linkedMapOf(handle to TypeMetadata.any, name to TypeMetadata.string),
        definitions: Map<DefinitionId, DataType> = linkedMapOf(nodeId to nodeDefinition, leafId to leafDefinition),
        definitionNatives: Map<DefinitionId, Map<DataTypePath, TypeMetadata>> = definitionNatives(),
        constraints: Map<DataTypePath, List<DataConstraint>> = linkedMapOf(kind to listOf(kinds), name to listOf(names))
    ): DataContract =
        DataContract(structural, natives, definitions, definitionNatives, constraints, metadata)


    private fun definitionNatives(): Map<DefinitionId, Map<DataTypePath, TypeMetadata>> =
        linkedMapOf(
            nodeId to mapOf(label to TypeMetadata.string),
            leafId to mapOf(path(DataPathSegment.Field(FieldId("value"))) to TypeMetadata.string))


    // Equality as declared: every part equal, a path's constraints compared as a set
    private fun definitionallyEqual(left: DataContract, right: DataContract): Boolean =
        left.structural == right.structural &&
                left.nativeByPath == right.nativeByPath &&
                left.definitions == right.definitions &&
                left.definitionNatives == right.definitionNatives &&
                left.constraintsByPath.mapValues { it.value.toSet() } ==
                        right.constraintsByPath.mapValues { it.value.toSet() } &&
                left.metadata == right.metadata


    private fun path(vararg segments: DataPathSegment): DataTypePath =
        DataTypePath(segments.toList())
}
