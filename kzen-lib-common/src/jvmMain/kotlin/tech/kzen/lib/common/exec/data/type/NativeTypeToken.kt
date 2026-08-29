package tech.kzen.lib.common.exec.data.type

import kotlin.reflect.KClass
import kotlin.reflect.KType


class NativeTypeToken(
    val type: KType,
    val loader: ClassLoader? = (type.classifier as? KClass<*>)?.java?.classLoader
) {
    override fun equals(other: Any?): Boolean =
        this === other || other is NativeTypeToken && type == other.type && loader === other.loader

    override fun hashCode(): Int = 31 * type.hashCode() + System.identityHashCode(loader)

    override fun toString(): String = "NativeTypeToken(type=$type, loader=$loader)"
}
