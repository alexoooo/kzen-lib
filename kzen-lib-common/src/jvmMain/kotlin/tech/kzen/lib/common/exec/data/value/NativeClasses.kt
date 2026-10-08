package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.data.type.NativeTypeToken
import tech.kzen.lib.common.exec.data.value.budget.ReadPreparation
import kotlin.reflect.KType
import kotlin.reflect.full.starProjectedType


/**
 * The kotlin-reflect facts the native value path asks of a value's runtime class, computed once per `Class`
 * identity (loader-local) as [ReadPreparation]: a class's first kotlin-reflect use loads its metadata.
 */
internal object NativeClasses {
    private class Facts(type: Class<*>) {
        val starProjectedType: KType = type.kotlin.starProjectedType
        val token = NativeTypeToken(starProjectedType)
        val isData = type.kotlin.isData
    }

    private val byClass = object: ClassValue<Facts>() {
        override fun computeValue(type: Class<*>): Facts = ReadPreparation.prepare { Facts(type) }
    }


    fun starProjectedType(value: Any): KType = byClass.get(value.javaClass).starProjectedType

    fun token(value: Any): NativeTypeToken = byClass.get(value.javaClass).token

    fun isData(value: Any): Boolean = byClass.get(value.javaClass).isData
}
