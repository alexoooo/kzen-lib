package tech.kzen.lib.common.exec.data.type

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem


class ResolvedDataContract(
    val contract: DataContract,
    tokenByPath: Map<DataTypePath, NativeTypeToken>
) {
    val tokenByPath: Map<DataTypePath, NativeTypeToken> = tokenByPath.toMap()

    init {
        if (this.tokenByPath.keys != contract.nativeByPath.keys) {
            throw DataException(DataProblem(
                DataProblem.invalidResolvedContract,
                "Native token paths must exactly match metadata paths: " +
                        "metadata=${contract.nativeByPath.keys}, tokens=${this.tokenByPath.keys}"))
        }
    }

    fun child(segment: DataPathSegment): ResolvedDataContract {
        val prefix = DataTypePath(listOf(segment))
        val rebasedTokens = tokenByPath.entries
            .filter { it.key.startsWith(prefix) }
            .associate { it.key.removePrefix(prefix) to it.value }
        return ResolvedDataContract(contract.child(segment), rebasedTokens)
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is ResolvedDataContract &&
                contract == other.contract && tokenByPath == other.tokenByPath

    override fun hashCode(): Int = 31 * contract.hashCode() + tokenByPath.hashCode()

    override fun toString(): String =
        "ResolvedDataContract(contract=$contract, tokenByPath=$tokenByPath)"
}
