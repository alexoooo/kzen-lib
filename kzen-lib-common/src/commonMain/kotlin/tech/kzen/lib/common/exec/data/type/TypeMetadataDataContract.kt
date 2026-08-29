package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassNames


fun TypeMetadata.toDataContract(): DataContract {
    if (className == ClassNames.kotlinAny && generics.isEmpty()) {
        return DataContract(DataType.Dynamic(nullable))
    }

    val scalarKind = scalarKind(className.asString())
    if (scalarKind != null) {
        return DataContract(DataType.Scalar(scalarKind, nullable))
    }

    return when (className.asString()) {
        "kotlin.Array",
        "kotlin.collections.List",
        "kotlin.collections.MutableList" ->
            if (generics.size == 1) {
                collectionContract(generics.single())
            }
            else {
                opaqueContract()
            }

        "kotlin.collections.Map",
        "kotlin.collections.MutableMap" ->
            if (generics.size == 2) {
                mappingContract(generics[0], generics[1])
            }
            else {
                opaqueContract()
            }

        else -> opaqueContract()
    }
}


private fun TypeMetadata.collectionContract(element: TypeMetadata): DataContract {
    val elementContract = element.toDataContract()
    return DataContract(
        DataType.Listing(elementContract.structural, nullable),
        mapOf(DataTypePath.root to this) +
                elementContract.nativeByPath.prefixed(DataPathSegment.ListingElement))
}


private fun TypeMetadata.mappingContract(
    key: TypeMetadata,
    value: TypeMetadata
): DataContract {
    val keyContract = key.toDataContract()
    val valueContract = value.toDataContract()
    if (keyContract.structural !is DataType.Scalar || keyContract.structural.nullable) {
        return opaqueContract()
    }

    return DataContract(
        DataType.Mapping(keyContract.structural, valueContract.structural, nullable),
        mapOf(DataTypePath.root to this) +
                valueContract.nativeByPath.prefixed(DataPathSegment.MappingValue))
}


private fun TypeMetadata.opaqueContract(): DataContract =
    DataContract(
        DataType.Opaque(nullable),
        mapOf(DataTypePath.root to this))


private fun Map<DataTypePath, TypeMetadata>.prefixed(
    segment: DataPathSegment
): Map<DataTypePath, TypeMetadata> =
    mapKeys { (path, _) -> DataTypePath(listOf(segment) + path.segments) }


private fun scalarKind(className: String): ScalarKind? =
    when (className) {
        "kotlin.Boolean" -> ScalarKind.Boolean
        "kotlin.Byte" -> ScalarKind.Integer(8)
        "kotlin.Short" -> ScalarKind.Integer(16)
        "kotlin.Int" -> ScalarKind.Integer(32)
        "kotlin.Long" -> ScalarKind.Integer(64)
        "kotlin.UByte" -> ScalarKind.Integer(8, signed = false)
        "kotlin.UShort" -> ScalarKind.Integer(16, signed = false)
        "kotlin.UInt" -> ScalarKind.Integer(32, signed = false)
        "kotlin.ULong" -> ScalarKind.Integer(64, signed = false)
        "java.math.BigInteger" -> ScalarKind.Integer()
        "java.math.BigDecimal" -> ScalarKind.Decimal
        "kotlin.Float" -> ScalarKind.Floating(32)
        "kotlin.Double" -> ScalarKind.Floating(64)
        "kotlin.String" -> ScalarKind.Text
        "kotlin.ByteArray" -> ScalarKind.Binary
        "java.time.LocalDate" -> ScalarKind.Date
        "java.time.LocalTime" -> ScalarKind.Time
        "java.time.Instant" -> ScalarKind.Instant
        "java.time.Duration" -> ScalarKind.Duration
        "java.util.UUID" -> ScalarKind.Uuid
        else -> null
    }
