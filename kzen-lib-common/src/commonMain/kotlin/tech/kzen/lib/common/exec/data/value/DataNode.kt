package tech.kzen.lib.common.exec.data.value

import kotlin.jvm.JvmInline


/** A backing-local position. A token is meaningful only to the [ValueAccess] that minted it. */
@JvmInline
value class DataNode(val token: Long)
