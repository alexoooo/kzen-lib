@file:Suppress("ConstPropertyName")

package tech.kzen.lib.common.model.structure.metadata

import tech.kzen.lib.common.exec.*
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames
import tech.kzen.lib.platform.ClassNames.simple


data class TypeMetadata(
    val className: ClassName,
    val generics: List<TypeMetadata>,
    val nullable: Boolean
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val classNameKey = "class"
        private const val genericsKey = "generics"
        private const val nullableKey = "nullable"

        val any = of(ClassNames.kotlinAny)
        val string = of(ClassNames.kotlinString)
        val boolean = of(ClassNames.kotlinBoolean)
        val int = of(ClassNames.kotlinInt)
        val long = of(ClassNames.kotlinLong)
        val double = of(ClassNames.kotlinDouble)

        @Suppress("unused")
        val anyNullable = TypeMetadata(ClassNames.kotlinAny, listOf(), true)


        fun of(className: ClassName): TypeMetadata {
            return TypeMetadata(
                className,
                listOf(),
                false)
        }


        @Suppress("MemberVisibilityCanBePrivate")
        fun ofExecutionValue(executionValue: MapExecutionValue): TypeMetadata {
            val className = (executionValue[classNameKey] as? TextExecutionValue)?.value
                ?: throw IllegalArgumentException("'$classNameKey' not found: $executionValue")

            val generics =
                (executionValue[genericsKey] as? ListExecutionValue)
                ?.values
                ?.map {
                    (it as? MapExecutionValue)
                    ?: throw IllegalArgumentException("Generic not map not found: $it")
                }
                ?: throw IllegalArgumentException("'$classNameKey' not found: $executionValue")

            val nullable = (executionValue[nullableKey] as? BooleanExecutionValue)?.value ?: false

            return TypeMetadata(
                ClassName(className),
                generics.map { ofExecutionValue(it) },
                nullable
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addDigestible(className)
        sink.addDigestibleList(generics)
        sink.addBoolean(nullable)
    }


    @Suppress("MemberVisibilityCanBePrivate", "unused")
    fun asExecutionValue(): ExecutionValue {
        return MapExecutionValue(mapOf(
            classNameKey to TextExecutionValue(className.asString()),
            genericsKey to ListExecutionValue(
                generics.map { it.asExecutionValue() }
            ),
            nullableKey to BooleanExecutionValue.of(nullable)
        ))
    }


    @Suppress("MemberVisibilityCanBePrivate", "unused")
    fun toSimple(): String {
        val prefix = className.simple()

        val parameters =
            if (generics.isEmpty()) {
                ""
            }
            else {
                "<${generics.joinToString { it.toSimple() }}>"
            }

        val suffix = if (nullable) { "?" } else { "" }

        return prefix + parameters + suffix
    }


    @Suppress("MemberVisibilityCanBePrivate", "unused")
    fun classNames(): Set<ClassName> {
        return setOf(className) + generics.flatMap { it.classNames() }
    }
}