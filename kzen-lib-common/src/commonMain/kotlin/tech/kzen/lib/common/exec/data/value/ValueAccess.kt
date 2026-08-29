package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.VariantId


/** Read-only structural access. Unsupported operations fail immediately with [DataAccessException]. */
interface ValueAccess {
    fun contract(node: DataNode): DataContract
    fun state(node: DataNode): DataState

    fun activeVariant(node: DataNode): VariantId
    fun selected(node: DataNode): DataNode

    fun field(node: DataNode, field: FieldId): DataNode
    fun entry(node: DataNode, key: ScalarExecutionValue): DataNode
    fun element(node: DataNode, index: Int): DataNode
    fun size(node: DataNode): Int
    fun keyAt(node: DataNode, index: Int): ScalarExecutionValue

    fun scalar(node: DataNode): ScalarExecutionValue

    fun readBoolean(node: DataNode): Boolean
    fun readLong(node: DataNode): Long
    fun readDouble(node: DataNode): Double
    fun readText(node: DataNode): String
    fun readBinary(node: DataNode): ByteArray

    fun native(node: DataNode): Any
}
