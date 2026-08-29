package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.data.type.DataContract
import kotlin.reflect.KClass
import kotlin.reflect.KType


data class ExactDataAdapter(
    val nativeClass: KClass<*>,
    val adapter: DataAdapter
)


data class CapabilityDataAdapter(
    val name: String,
    val accepts: (KClass<*>) -> Boolean,
    val adapter: DataAdapter
) {
    init {
        require(name.isNotBlank()) { "Capability adapter name must not be blank" }
    }
}


interface DataAdapterRegistry {
    fun describe(native: KType): DataContract

    fun lift(value: Any?, expected: DataContract? = null): DataValue
}
