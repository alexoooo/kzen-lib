package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.data.type.NativeTypeToken
import tech.kzen.lib.common.exec.data.value.budget.ReadPreparation
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.full.withNullability


/**
 * The kotlin-reflect facts the native value path asks of a value's runtime class, computed once per `Class`
 * identity (loader-local) as [ReadPreparation]: a class's first kotlin-reflect use loads its metadata.
 */
internal object NativeClasses {
    private class Facts(type: Class<*>) {
        val starProjectedType: KType = type.kotlin.starProjectedType
        val token = NativeTypeToken(starProjectedType)
        val isData = type.kotlin.isData

        // An array of non-arrays has two held types (see heldType): with non-null elements, then with nullable ones
        val flatArrayTypes: Array<KType>? =
            if (isObjectArray(type) && !isObjectArray(type.componentType)) {
                arrayOf(arrayType(type, booleanArrayOf(false), 0), arrayType(type, booleanArrayOf(true), 0))
            }
            else {
                null
            }
    }

    private val byClass = object: ClassValue<Facts>() {
        override fun computeValue(type: Class<*>): Facts = ReadPreparation.prepare { Facts(type) }
    }


    fun starProjectedType(value: Any): KType = byClass.get(value.javaClass).starProjectedType

    fun token(value: Any): NativeTypeToken = byClass.get(value.javaClass).token

    fun isData(value: Any): Boolean = byClass.get(value.javaClass).isData


    /**
     * The type [value] shows at run time: its class's star projection, except for an object array, whose component
     * class is reified (`String[]` is `Array<String>`, `String[][]` is `Array<Array<String>>`) as the describer
     * reads a declared `Array<T>`. The JVM lets any element be null, so the elements of each nesting level are
     * nullable where an array of that level holds a null, as a collection's elements are typed by what it holds.
     */
    fun heldType(value: Any): KType {
        val facts = byClass.get(value.javaClass)
        facts.flatArrayTypes?.let { types ->
            return types[if (null in value as Array<*>) 1 else 0]
        }
        if (!isObjectArray(value.javaClass)) {
            return facts.starProjectedType
        }
        val nulls = BooleanArray(nestingLevels(value.javaClass))
        heldNulls(value as Array<*>, 0, nulls)
        return arrayType(value.javaClass, nulls, 0)
    }


    private fun isObjectArray(type: Class<*>): Boolean =
        type.isArray && !type.componentType.isPrimitive


    private fun nestingLevels(type: Class<*>): Int {
        var levels = 0
        var current = type
        while (isObjectArray(current)) {
            levels++
            current = current.componentType
        }
        return levels
    }


    private fun heldNulls(array: Array<*>, level: Int, nulls: BooleanArray) {
        for (element in array) {
            if (element == null) {
                nulls[level] = true
            }
            else if (level + 1 < nulls.size) {
                heldNulls(element as Array<*>, level + 1, nulls)
            }
        }
    }


    /** [type], an object array, with the element nullability of each of its nesting levels from [level] on. */
    private fun arrayType(type: Class<*>, nulls: BooleanArray, level: Int): KType {
        val component = type.componentType
        val element =
            if (isObjectArray(component)) arrayType(component, nulls, level + 1)
            else component.kotlin.starProjectedType
        return type.kotlin.createType(listOf(KTypeProjection.invariant(element.withNullability(nulls[level]))))
    }
}
