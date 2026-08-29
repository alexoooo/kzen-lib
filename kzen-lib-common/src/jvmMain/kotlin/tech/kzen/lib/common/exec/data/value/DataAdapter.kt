package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.data.type.DataContract
import kotlin.jvm.JvmInline
import kotlin.reflect.KType


@JvmInline
value class DataAdapterId(val value: String) {
    init {
        require(value.isNotBlank()) { "Data adapter identifier must not be blank" }
    }

    override fun toString(): String = value
}


/** A JVM native type's static description and runtime view must be supplied by the same adapter. */
interface DataAdapter {
    val id: DataAdapterId

    fun describe(native: KType): DataContract?

    fun lift(value: Any, expected: DataContract? = null): DataValue
}
