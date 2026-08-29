package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs


class TypeMetadataDataContractTest {
    @Test
    fun mapsScalarAndCollectionMetadataStructurally() {
        assertEquals(
            DataType.Scalar(ScalarKind.Integer(32), nullable = true),
            TypeMetadata(ClassName("kotlin.Int"), emptyList(), true)
                .toDataContract().structural)

        val listMetadata = TypeMetadata(
            ClassName("kotlin.collections.List"),
            listOf(TypeMetadata.string),
            false)
        val listContract = listMetadata.toDataContract()
        assertEquals(DataType.Listing(DataType.Scalar(ScalarKind.Text)), listContract.structural)
        assertEquals(mapOf(DataTypePath.root to listMetadata), listContract.nativeByPath)

        val opaqueValue = TypeMetadata(ClassName("example.Value"), emptyList(), false)
        val mapMetadata = TypeMetadata(
            ClassName("kotlin.collections.Map"),
            listOf(TypeMetadata.string, opaqueValue),
            false)
        val mapContract = mapMetadata.toDataContract()
        assertEquals(
            DataType.Mapping(
                DataType.Scalar(ScalarKind.Text),
                DataType.Opaque()),
            mapContract.structural)
        assertEquals(
            setOf(
                DataTypePath.root,
                DataTypePath(listOf(DataPathSegment.MappingValue))),
            mapContract.nativeByPath.keys)
    }


    @Test
    fun mapsAnyToDynamicAtEveryDepth() {
        assertEquals(
            DataType.Dynamic(nullable = true),
            TypeMetadata.anyNullable.toDataContract().structural)

        val listOfAny = TypeMetadata(
            ClassName("kotlin.collections.List"),
            listOf(TypeMetadata.anyNullable),
            false)
        assertEquals(
            DataType.Listing(DataType.Dynamic(nullable = true)),
            listOfAny.toDataContract().structural)
    }


    @Test
    fun unknownAndInvalidCollectionDeclarationsStayOpaque() {
        val unknown = TypeMetadata(ClassName("example.Unknown"), emptyList(), false)
        val unknownContract = unknown.toDataContract()
        assertIs<DataType.Opaque>(unknownContract.structural)
        assertEquals(mapOf(DataTypePath.root to unknown), unknownContract.nativeByPath)

        val invalidMap = TypeMetadata(
            ClassName("kotlin.collections.Map"),
            listOf(unknown, TypeMetadata.string),
            false)
        assertIs<DataType.Opaque>(invalidMap.toDataContract().structural)
    }
}
