package tech.kzen.lib.common.reflect

import tech.kzen.lib.platform.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


/**
 * A fresh registry is used throughout (never [ReflectionRegistry.global]) so the process-global stays clean.
 */
class ReflectionRegistryServiceArgumentsTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val fooConsumer = "com.example.FooConsumer"
    private val barConsumer = "com.example.BarConsumer"
    private val plainObject = "com.example.PlainObject"

    private val alphaService = "com.example.AlphaService"
    private val betaService = "com.example.BetaService"


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun emptyRegistryHasNoDeclarations() {
        assertTrue(ReflectionRegistry().serviceArgumentDeclarations().isEmpty())
    }


    @Test
    fun registrationWithoutServiceArgumentsIsAbsent() {
        val registry = ReflectionRegistry()
        registry.put(plainObject, listOf("name")) { "instance" }

        assertTrue(registry.serviceArgumentDeclarations().isEmpty())
    }


    @Test
    fun declaredServiceTypesMapToTheirDeclaringClasses() {
        val registry = ReflectionRegistry()

        // Two service arguments on one class, one of them shared with a second class
        registry.put(
            fooConsumer,
            listOf("alpha", "beta"),
            mapOf("alpha" to alphaService, "beta" to betaService)
        ) { "foo" }

        registry.put(
            barConsumer,
            listOf("alpha"),
            mapOf("alpha" to alphaService)
        ) { "bar" }

        registry.put(plainObject, listOf()) { "plain" }

        val declarations = registry.serviceArgumentDeclarations()

        assertEquals(
            setOf(ClassName(alphaService), ClassName(betaService)),
            declarations.keys)

        assertEquals(
            setOf(ClassName(fooConsumer), ClassName(barConsumer)),
            declarations[ClassName(alphaService)])

        assertEquals(
            setOf(ClassName(fooConsumer)),
            declarations[ClassName(betaService)])
    }
}
