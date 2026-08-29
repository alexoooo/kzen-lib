package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.data.type.NativeTypeToken


/** Optional JVM facet: supplies the exact loader-local native type at a value node. */
interface JvmNativeValueAccess: ValueAccess {
    fun nativeType(node: DataNode): NativeTypeToken?
}
