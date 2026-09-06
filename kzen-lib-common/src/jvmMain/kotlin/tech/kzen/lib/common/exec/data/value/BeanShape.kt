package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.data.problem.DataException
import tech.kzen.lib.common.exec.data.problem.DataProblem
import java.beans.Introspector
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.kotlinFunction
import kotlin.reflect.jvm.kotlinProperty


/**
 * The data shape of an ordinary class — one never written for kzen — by the fixed JavaBeans-style convention
 * (extensibility plan E7 item 1):
 *
 * - a property is a public, non-static, non-synthetic, non-bridge `getX()` / `isX()` (the latter only for a
 *   primitive `boolean`) or a public non-static field, declared by the class or a supertype of its own —
 *   members declared by the JDK or the Kotlin standard library (`java.*`, `javax.*`, `jdk.*`, `kotlin.*`:
 *   `getClass`, a collection's `isEmpty`, a `Throwable`'s `getMessage`) are not data and are excluded;
 * - names by JavaBeans decapitalization (`getURL()` → `URL`, `getId()` → `id`); **order is lexical by final
 *   name** (reflection guarantees no declaration order; records and data classes keep component order and do
 *   not come through here);
 * - precedence: a getter over a public field of the same name, `getX()` over `isX()`; the most-derived
 *   declaration wins across the hierarchy; a getter and a field of one name with different types is an error
 *   naming the class;
 * - nullability: primitives are non-null; a Kotlin-declared member keeps its declared nullability; a Java
 *   member reads a runtime-visible `@Nullable` / `@NonNull` (JSpecify, JSR-305) on the getter, then the field,
 *   then a JSpecify `@NullMarked` class or package, otherwise every reference-typed property is optional
 *   (JetBrains' `@Nullable` / `@NotNull` have class retention and cannot be seen by reflection);
 * - a getter that throws is a [DataAccessException] naming the property, not a run crash.
 *
 * Shapes are cached per `Class` identity (a [ClassValue]), so equal names from different loaders stay distinct.
 * A class with no property at all has no bean shape and stays opaque. These are data-shape rules, not a
 * security boundary.
 */
internal class BeanShape private constructor(
    val properties: List<Property>
) {
    class Property(
        val name: String,
        val type: KType,
        private val reader: (Any) -> Any?
    ) {
        fun read(instance: Any): Any? {
            return try {
                reader(instance)
            }
            catch (e: InvocationTargetException) {
                val cause = e.targetException ?: e
                throw DataAccessException(DataProblem(
                    DataProblem.invalidValue,
                    "Native property '$name' of ${instance.javaClass.name} threw ${cause.javaClass.name}: ${cause.message}"))
            }
            catch (e: ReflectiveOperationException) {
                throw DataAccessException(DataProblem(
                    DataProblem.invalidValue,
                    "Unable to read native property '$name' of ${instance.javaClass.name}: ${e.message}"))
            }
        }
    }


    companion object {
        private val shapes = object: ClassValue<BeanShape>() {
            override fun computeValue(type: Class<*>): BeanShape = analyze(type)
        }

        private val empty = BeanShape(emptyList())

        private val nullableAnnotations = setOf(
            "org.jspecify.annotations.Nullable",
            "javax.annotation.Nullable")
        private val nonNullAnnotations = setOf(
            "org.jspecify.annotations.NonNull",
            "javax.annotation.Nonnull")
        private val platformPackagePrefixes = listOf("java.", "javax.", "jdk.", "kotlin.")
        private const val nullMarkedAnnotation = "org.jspecify.annotations.NullMarked"


        /** The bean shape of [type], or null when the convention finds no property (the class stays opaque). */
        fun of(type: Class<*>): BeanShape? {
            return shapes.get(type).takeIf { it.properties.isNotEmpty() }
        }


        /** Whether [type] is a candidate at all: not a primitive, array, enum, annotation or `Object` itself. */
        fun isCandidate(type: Class<*>): Boolean {
            return !type.isPrimitive && !type.isArray && !type.isEnum && !type.isAnnotation &&
                    type != Any::class.java
        }


        private fun analyze(type: Class<*>): BeanShape {
            if (!isCandidate(type) || isPlatform(type)) {
                return empty
            }
            val kotlinDeclared = type.isAnnotationPresent(Metadata::class.java)
            val nullMarked = isNullMarked(type)
            val kotlinGetters = if (kotlinDeclared) kotlinPropertyGetters(type) else emptyMap()

            val getters = mutableMapOf<String, Method>()
            for (method in type.methods) {
                if (!isPropertyGetter(method)) {
                    continue
                }
                val name = propertyName(method) ?: continue
                val existing = getters[name]
                if (existing == null || preferred(method, existing)) {
                    getters[name] = method
                }
            }

            val fields = mutableMapOf<String, Field>()
            for (field in type.fields) {
                if (Modifier.isStatic(field.modifiers) || field.isSynthetic || isPlatform(field.declaringClass)) {
                    continue
                }
                val existing = fields[field.name]
                if (existing == null || existing.declaringClass.isAssignableFrom(field.declaringClass)) {
                    fields[field.name] = field
                }
            }

            val properties = mutableListOf<Property>()
            for (name in (getters.keys + fields.keys).sorted()) {
                val getter = getters[name]
                val field = fields[name]
                if (getter != null && field != null && getter.returnType != field.type) {
                    throw DataException(DataProblem(
                        DataProblem.nativeShapeConflict,
                        "Class ${type.name} declares property '$name' as both ${getter.name}(): " +
                                "${getter.returnType.name} and field ${field.type.name}"))
                }
                properties += if (getter != null) {
                    getterProperty(name, getter, field, kotlinGetters[getter], nullMarked)
                }
                else {
                    fieldProperty(name, field!!, kotlinDeclared, nullMarked)
                }
            }
            return BeanShape(properties)
        }


        private fun isPropertyGetter(method: Method): Boolean {
            return !Modifier.isStatic(method.modifiers) &&
                    !method.isSynthetic &&
                    !method.isBridge &&
                    method.parameterCount == 0 &&
                    method.returnType != Void.TYPE &&
                    !isPlatform(method.declaringClass)
        }


        private fun isPlatform(type: Class<*>): Boolean {
            val name = type.name
            return platformPackagePrefixes.any { name.startsWith(it) }
        }


        /** A Kotlin property's getter is not a `KFunction`; map each getter method to its property's type. */
        private fun kotlinPropertyGetters(type: Class<*>): Map<Method, KType> {
            return try {
                type.kotlin.memberProperties.mapNotNull { property ->
                    property.javaGetter?.let { it to property.returnType }
                }.toMap()
            }
            catch (_: Throwable) {
                // kotlin-reflect cannot model every class (e.g. synthetic or file-facade shapes); fall back to Java
                emptyMap()
            }
        }


        private fun propertyName(method: Method): String? {
            val name = method.name
            return when {
                name.length > 3 && name.startsWith("get") && name[3].isUpperCase() ->
                    Introspector.decapitalize(name.substring(3))
                name.length > 2 && name.startsWith("is") && name[2].isUpperCase() &&
                        method.returnType == java.lang.Boolean.TYPE ->
                    Introspector.decapitalize(name.substring(2))
                else -> null
            }
        }


        /** `getX` over `isX`; otherwise the most-derived declaration. */
        private fun preferred(candidate: Method, existing: Method): Boolean {
            val candidateIs = candidate.name.startsWith("is")
            val existingIs = existing.name.startsWith("is")
            if (candidateIs != existingIs) {
                return existingIs
            }
            return existing.declaringClass.isAssignableFrom(candidate.declaringClass) &&
                    existing.declaringClass != candidate.declaringClass
        }


        private fun getterProperty(
            name: String,
            getter: Method,
            field: Field?,
            kotlinProperty: KType?,
            nullMarked: Boolean
        ): Property {
            val kotlinDeclared = kotlinProperty ?: getter.kotlinFunction?.returnType
                ?.takeIf { getter.declaringClass.isAnnotationPresent(Metadata::class.java) }
            val declared = kotlinDeclared
                ?: getter.kotlinFunction?.returnType
                ?: getter.returnType.kotlin.starProjectedType
            val nullable = when {
                getter.returnType.isPrimitive -> false
                kotlinDeclared != null -> kotlinDeclared.isMarkedNullable
                else -> javaNullability(getter.annotations.map { it.annotationClass.java.name } +
                        getter.annotatedReturnType.annotations.map { it.annotationClass.java.name })
                    ?: field?.let { javaNullability(it.annotations.map { a -> a.annotationClass.java.name }) }
                    ?: !nullMarked
            }
            return Property(name, declared.withNullability(nullable)) { instance -> getter.invoke(instance) }
        }


        private fun fieldProperty(
            name: String,
            field: Field,
            kotlinDeclared: Boolean,
            nullMarked: Boolean
        ): Property {
            val declared = field.kotlinProperty?.returnType
                ?: field.type.kotlin.starProjectedType
            val nullable = when {
                field.type.isPrimitive -> false
                kotlinDeclared && field.declaringClass.isAnnotationPresent(Metadata::class.java) -> declared.isMarkedNullable
                else -> javaNullability(field.annotations.map { it.annotationClass.java.name } +
                        field.annotatedType.annotations.map { it.annotationClass.java.name })
                    ?: !nullMarked
            }
            return Property(name, declared.withNullability(nullable)) { instance -> field.get(instance) }
        }


        /** true = nullable, false = non-null, null = unannotated. */
        private fun javaNullability(annotationNames: List<String>): Boolean? {
            return when {
                annotationNames.any { it in nullableAnnotations } -> true
                annotationNames.any { it in nonNullAnnotations } -> false
                else -> null
            }
        }


        private fun isNullMarked(type: Class<*>): Boolean {
            var current: Class<*>? = type
            while (current != null) {
                if (current.annotations.any { it.annotationClass.java.name == nullMarkedAnnotation }) {
                    return true
                }
                current = current.enclosingClass
            }
            return type.`package`?.annotations?.any { it.annotationClass.java.name == nullMarkedAnnotation } ?: false
        }
    }


    fun property(name: String): Property? {
        return properties.firstOrNull { it.name == name }
    }
}
