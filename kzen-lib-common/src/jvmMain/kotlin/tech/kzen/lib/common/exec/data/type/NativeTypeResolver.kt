package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.data.value.DataValue
import kotlin.reflect.KType


interface NativeTypeResolver: AutoCloseable {
    fun describe(native: KType): ResolvedDataContract

    fun describe(value: DataValue): ResolvedDataContract

    fun resolve(contract: DataContract, owner: ClassLoader): ResolvedDataContract

    fun isAssignable(
        expected: ResolvedDataContract,
        actual: ResolvedDataContract
    ): TypeAcceptance

    fun isAssignable(expected: ResolvedDataContract, actual: DataValue): TypeAcceptance

    fun selectVariant(
        union: ResolvedDataContract,
        actual: ResolvedDataContract
    ): VariantSelection

    fun validateVariant(
        union: ResolvedDataContract,
        variant: VariantId,
        actual: ResolvedDataContract
    ): TypeAcceptance

    override fun close()
}
