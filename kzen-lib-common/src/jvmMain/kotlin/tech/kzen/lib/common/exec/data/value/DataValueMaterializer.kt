package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.BooleanExecutionValue
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.NumberExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.ScalarKind


/** Projects a JVM-backed value to its native or canonical structural representation. */
fun DataValue.materializeJvm(): Any? = materializeNode(root)


private fun DataValue.materializeNode(node: DataNode): Any? {
    return when (access.state(node)) {
        DataState.Absent -> error("Cannot materialize an absent data node")
        DataState.Null -> null
        DataState.Present -> {
            val contract = access.contract(node)
            if (contract.nativeByPath[DataTypePath.root] != null || contract.structural is DataType.Opaque) {
                return access.native(node)
            }
            when (val type = contract.structural) {
                is DataType.Dynamic -> materializeDynamic(node)
                is DataType.Listing -> List(access.size(node)) { index ->
                    materializeNode(access.element(node, index))
                }
                is DataType.Mapping -> buildMap<Any, Any?> {
                    repeat(access.size(node)) { index ->
                        val key = access.keyAt(node, index).asJvmScalar()
                        put(key, materializeNode(access.entry(node, access.keyAt(node, index))))
                    }
                }
                is DataType.Record -> buildMap<String, Any?> {
                    for (field in type.fields) {
                        val name = if (field.id.occurrence == 0) {
                            field.id.name
                        }
                        else {
                            "${field.id.name}#${field.id.occurrence}"
                        }
                        put(name, materializeNode(access.field(node, field.id)))
                    }
                }
                is DataType.Scalar -> materializeScalar(node, type.kind)
                is DataType.Union -> materializeNode(access.selected(node))
                is DataType.Opaque -> error("Opaque values must expose a native root")
            }
        }
    }
}


private fun DataValue.materializeDynamic(node: DataNode): Any? {
    val actual = access.contract(node).structural
    require(actual !is DataType.Dynamic) { "Present Dynamic value has no materializable runtime shape" }
    return materializeNode(node)
}


private fun DataValue.materializeScalar(node: DataNode, kind: ScalarKind): Any =
    when (kind) {
        ScalarKind.Boolean -> access.readBoolean(node)
        is ScalarKind.Integer -> access.readLong(node)
        ScalarKind.Decimal -> access.scalar(node).asJvmScalar()
        is ScalarKind.Floating -> access.readDouble(node)
        ScalarKind.Text,
        ScalarKind.Date,
        ScalarKind.Time,
        ScalarKind.Instant,
        ScalarKind.Duration,
        ScalarKind.Uuid -> access.readText(node)
        ScalarKind.Binary -> access.readBinary(node)
    }


private fun tech.kzen.lib.common.exec.ScalarExecutionValue.asJvmScalar(): Any =
    when (this) {
        is BooleanExecutionValue -> value
        is LongExecutionValue -> value
        is NumberExecutionValue -> value
        is TextExecutionValue -> value
        else -> toString()
    }
