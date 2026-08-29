package tech.kzen.lib.common.exec.data.problem

import tech.kzen.lib.common.exec.data.type.DataPathSegment


class DataProblem(
    val code: String,
    val message: String,
    path: List<DataPathSegment> = emptyList()
) {
    val path: List<DataPathSegment> = path.toList()

    init {
        require(code.isNotBlank()) { "Data problem code must not be blank" }
        require(message.isNotBlank()) { "Data problem message must not be blank" }
    }

    companion object Code {
        const val invalidIdentifier = "data.invalid-identifier"
        const val invalidPath = "data.invalid-path"
        const val invalidRecord = "data.invalid-record"
        const val invalidMapping = "data.invalid-mapping"
        const val invalidUnion = "data.invalid-union"
        const val invalidScalar = "data.invalid-scalar"
        const val invalidContract = "data.invalid-contract"
        const val incompatibleType = "data.incompatible-type"
        const val unionVariantUnknown = "data.union-variant-unknown"
        const val unionVariantNoMatch = "data.union-variant-no-match"
        const val unionVariantAmbiguous = "data.union-variant-ambiguous"
        const val invalidTypeEncoding = "data.invalid-type-encoding"
        const val invalidResolvedContract = "data.invalid-resolved-contract"
        const val nativeTypeUnresolved = "data.native-type-unresolved"
        const val nativeTypeMissing = "data.native-type-missing"
        const val nativeTypeIncompatible = "data.native-type-incompatible"
        const val nativeResolverReleased = "data.native-resolver-released"
        const val invalidValue = "data.invalid-value"
        const val invalidOperation = "data.invalid-operation"
        const val invalidState = "data.invalid-state"
        const val missingValue = "data.missing-value"
        const val invalidMappingKey = "data.invalid-mapping-key"
        const val mappingKeyCollision = "data.mapping-key-collision"
        const val snapshotRejected = "data.snapshot-rejected"
        const val snapshotLimit = "data.snapshot-limit"
        const val snapshotCycle = "data.snapshot-cycle"
        const val snapshotOpaque = "data.snapshot-opaque"
        const val snapshotDuplicateField = "data.snapshot-duplicate-field"
        const val snapshotBinaryHandle = "data.snapshot-binary-handle"
        const val adapterConflict = "data.adapter-conflict"
        const val adapterRefused = "data.adapter-refused"
        const val adapterContractMismatch = "data.adapter-contract-mismatch"
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is DataProblem &&
                code == other.code && message == other.message && path == other.path

    override fun hashCode(): Int {
        var result = code.hashCode()
        result = 31 * result + message.hashCode()
        result = 31 * result + path.hashCode()
        return result
    }

    override fun toString(): String =
        "DataProblem(code='$code', message='$message', path=$path)"
}
