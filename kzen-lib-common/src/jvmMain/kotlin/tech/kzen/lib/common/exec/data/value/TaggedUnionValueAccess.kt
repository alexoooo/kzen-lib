package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.NativeTypeToken
import tech.kzen.lib.common.exec.data.type.VariantId


/** Adds an explicit expected-union tag without copying or changing the selected backing. */
internal class TaggedUnionValueAccess private constructor(
    private val unionContract: DataContract,
    private val variant: VariantId,
    private val selected: DataValue
): JvmNativeValueAccess {
    companion object {
        fun value(contract: DataContract, variant: VariantId, selected: DataValue): DataValue {
            val access = TaggedUnionValueAccess(contract, variant, selected)
            return DataValue(access, DataNode(0))
        }
    }


    private val delegateByToken = mutableListOf<DataNode?>(null, selected.root)
    private val tokenByDelegate = mutableMapOf(selected.root to DataNode(1))
    private val selectedContract = unionContract.child(DataPathSegment.Variant(variant))

    override fun contract(node: DataNode): DataContract =
        when (node.token) {
            0L -> unionContract
            1L -> selectedContract
            else -> selected.access.contract(delegate(node))
        }

    override fun state(node: DataNode): DataState =
        if (isRoot(node)) DataState.Present else selected.access.state(delegate(node))

    override fun activeVariant(node: DataNode): VariantId {
        requireRoot(node, "activeVariant")
        return variant
    }

    override fun selected(node: DataNode): DataNode {
        requireRoot(node, "selected")
        return DataNode(1)
    }

    override fun field(node: DataNode, field: FieldId): DataNode =
        wrap(selected.access.field(delegate(node), field))

    override fun entry(node: DataNode, key: ScalarExecutionValue): DataNode =
        wrap(selected.access.entry(delegate(node), key))

    override fun element(node: DataNode, index: Int): DataNode =
        wrap(selected.access.element(delegate(node), index))

    override fun size(node: DataNode): Int = selected.access.size(delegate(node))
    override fun keyAt(node: DataNode, index: Int): ScalarExecutionValue =
        selected.access.keyAt(delegate(node), index)
    override fun scalar(node: DataNode): ScalarExecutionValue = selected.access.scalar(delegate(node))
    override fun readBoolean(node: DataNode): Boolean = selected.access.readBoolean(delegate(node))
    override fun readLong(node: DataNode): Long = selected.access.readLong(delegate(node))
    override fun readDouble(node: DataNode): Double = selected.access.readDouble(delegate(node))
    override fun readText(node: DataNode): String = selected.access.readText(delegate(node))
    override fun readBinary(node: DataNode): ByteArray = selected.access.readBinary(delegate(node))
    override fun native(node: DataNode): Any = selected.access.native(delegate(node))

    override fun nativeType(node: DataNode): NativeTypeToken? =
        (selected.access as? JvmNativeValueAccess)?.nativeType(delegate(node))


    private fun wrap(delegate: DataNode): DataNode =
        tokenByDelegate.getOrPut(delegate) {
            delegateByToken += delegate
            DataNode((delegateByToken.size - 1).toLong())
        }

    private fun delegate(node: DataNode): DataNode {
        if (isRoot(node) || node.token < 0 || node.token > Int.MAX_VALUE ||
            node.token.toInt() !in delegateByToken.indices
        ) {
            invalid(node, "Operation requires the selected union node")
        }
        return delegateByToken[node.token.toInt()]
            ?: invalid(node, "Union node has no selected backing position")
    }

    private fun requireRoot(node: DataNode, operation: String) {
        if (!isRoot(node)) invalid(node, "$operation is valid only for the union root")
    }

    private fun isRoot(node: DataNode): Boolean = node.token == 0L

    private fun invalid(node: DataNode, message: String): Nothing =
        throw DataAccessException(DataProblem(
            DataProblem.invalidOperation,
            message,
            if (isRoot(node)) emptyList() else listOf(DataPathSegment.Variant(variant))))
}
