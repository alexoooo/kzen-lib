package tech.kzen.lib.common.exec.data.type

import kotlin.reflect.KClass
import kotlin.reflect.KType


interface NativeTypeDescriber {
    val nativeClass: KClass<*>

    fun describe(native: KType): DataContract?
}
