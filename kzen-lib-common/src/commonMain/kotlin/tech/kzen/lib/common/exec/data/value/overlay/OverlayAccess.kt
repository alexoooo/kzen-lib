package tech.kzen.lib.common.exec.data.value.overlay

import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.VariantId
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.DataNode
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.ValueAccess
import tech.kzen.lib.platform.platformSynchronized


/**
 * A record composed without copying ([tech.kzen.lib.common.exec.data.value.DataOverlay]): [base] with [values] set,
 * laid out by [layout]. The field at position `p` is node `p + 1`, read through to its set value or to the base's
 * field. A base that is itself a composition is read through to its parts, so compositions of compositions do not
 * nest.
 *
 * Tokens belong to this view. Nodes inside a field are interned on first navigation, so recursive data stays
 * navigable without copying; that state, and a plain base's field nodes, are made only when something reads them.
 */
internal class OverlayAccess(
    private val layout: OverlayLayout,
    private val base: DataValue?,
    private val values: List<DataValue>
): ValueAccess {
    companion object {
        val root = DataNode(0)
    }


    private class Bound(val access: ValueAccess, val node: DataNode)


    private val baseOverlay: OverlayAccess? =
        if (base != null && base.root == root) base.access as? OverlayAccess else null

    private var baseNodes: Array<DataNode?>? = null
    private var nested: MutableList<Bound>? = null
    private var tokens: MutableMap<ValueAccess, MutableMap<DataNode, DataNode>>? = null


    /** The value whose native stands for the composed record: the original record under any composition. */
    private fun nativeSource(): DataValue? =
        if (baseOverlay != null) baseOverlay.nativeSource() else base


    private fun fieldAccess(position: Int): ValueAccess {
        val setIndex = layout.setIndex(position)
        return when {
            setIndex >= 0 -> values[setIndex].access
            baseOverlay != null -> baseOverlay.fieldAccess(position)
            else -> checkNotNull(base).access
        }
    }


    private fun fieldNode(position: Int): DataNode {
        val setIndex = layout.setIndex(position)
        return when {
            setIndex >= 0 -> values[setIndex].root
            baseOverlay != null -> baseOverlay.fieldNode(position)
            else -> plainBaseFieldNode(position)
        }
    }


    // Asked once per field, so every read of it reaches the same node of the base
    private fun plainBaseFieldNode(position: Int): DataNode = platformSynchronized(this) {
        val nodes = baseNodes ?: arrayOfNulls<DataNode>(layout.size).also { baseNodes = it }
        nodes[position] ?: run {
            val plain = checkNotNull(base)
            plain.access.field(plain.root, layout.id(position)).also { nodes[position] = it }
        }
    }


    private fun intern(access: ValueAccess, node: DataNode): DataNode = platformSynchronized(this) {
        val bounds = nested ?: mutableListOf<Bound>().also { nested = it }
        val byAccess = tokens ?: HashMap<ValueAccess, MutableMap<DataNode, DataNode>>().also { tokens = it }
        byAccess.getOrPut(access) { mutableMapOf() }.getOrPut(node) {
            bounds += Bound(access, node)
            DataNode((layout.size + bounds.size).toLong())
        }
    }


    private fun nestedBound(token: Long): Bound = platformSynchronized(this) {
        val bounds = nested
        val index = token - layout.size - 1
        if (bounds == null || index < 0 || index >= bounds.size) {
            throw DataAccessException(DataProblem(
                DataProblem.invalidOperation, "Operation requires a composed field node"))
        }
        bounds[index.toInt()]
    }


    private inline fun <T> read(node: DataNode, action: (ValueAccess, DataNode) -> T): T {
        val token = node.token
        if (token >= 1 && token <= layout.size) {
            val position = (token - 1).toInt()
            return action(fieldAccess(position), fieldNode(position))
        }
        val bound = nestedBound(token)
        return action(bound.access, bound.node)
    }


    private inline fun navigate(node: DataNode, action: (ValueAccess, DataNode) -> DataNode): DataNode =
        read(node) { access, original -> intern(access, action(access, original)) }


    private fun rootOnly(operation: String): Nothing =
        throw DataAccessException(DataProblem(
            DataProblem.invalidOperation, "A composed record does not support $operation"))


    //-----------------------------------------------------------------------------------------------------------------
    override fun contract(node: DataNode): DataContract =
        if (node == root) layout.contract else read(node) { a, n -> a.contract(n) }

    override fun state(node: DataNode): DataState =
        if (node == root) DataState.Present else read(node) { a, n -> a.state(n) }

    override fun field(node: DataNode, field: FieldId): DataNode =
        if (node == root) {
            val position = layout.position(field)
            if (position < 0) {
                throw DataAccessException(DataProblem(DataProblem.invalidPath, "Unknown field '$field'"))
            }
            DataNode(position + 1L)
        }
        else {
            navigate(node) { a, n -> a.field(n, field) }
        }

    override fun native(node: DataNode): Any =
        if (node == root) {
            val source = nativeSource() ?: rootOnly("native access")
            source.access.native(source.root)
        }
        else {
            read(node) { a, n -> a.native(n) }
        }

    override fun size(node: DataNode): Int =
        if (node == root) layout.size else read(node) { a, n -> a.size(n) }

    override fun activeVariant(node: DataNode): VariantId =
        if (node == root) rootOnly("variants") else read(node) { a, n -> a.activeVariant(n) }
    override fun selected(node: DataNode): DataNode =
        if (node == root) rootOnly("variants") else navigate(node) { a, n -> a.selected(n) }
    override fun entry(node: DataNode, key: ScalarExecutionValue): DataNode =
        if (node == root) rootOnly("keyed entries") else navigate(node) { a, n -> a.entry(n, key) }
    override fun element(node: DataNode, index: Int): DataNode =
        if (node == root) rootOnly("elements") else navigate(node) { a, n -> a.element(n, index) }
    override fun keyAt(node: DataNode, index: Int): ScalarExecutionValue =
        if (node == root) rootOnly("keys") else read(node) { a, n -> a.keyAt(n, index) }
    override fun scalar(node: DataNode): ScalarExecutionValue =
        if (node == root) rootOnly("scalar reads") else read(node) { a, n -> a.scalar(n) }
    override fun readBoolean(node: DataNode): Boolean =
        if (node == root) rootOnly("scalar reads") else read(node) { a, n -> a.readBoolean(n) }
    override fun readLong(node: DataNode): Long =
        if (node == root) rootOnly("scalar reads") else read(node) { a, n -> a.readLong(n) }
    override fun readDouble(node: DataNode): Double =
        if (node == root) rootOnly("scalar reads") else read(node) { a, n -> a.readDouble(n) }
    override fun readText(node: DataNode): String =
        if (node == root) rootOnly("scalar reads") else read(node) { a, n -> a.readText(n) }
    override fun readBinary(node: DataNode): ByteArray =
        if (node == root) rootOnly("scalar reads") else read(node) { a, n -> a.readBinary(n) }
}
